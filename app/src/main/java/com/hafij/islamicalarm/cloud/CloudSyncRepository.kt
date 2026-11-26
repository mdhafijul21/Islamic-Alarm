package com.hafij.islamicalarm.cloud

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.report.ReportStore
import com.hafij.islamicalarm.settings.AppShareConfigStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    /**
     * Registers or updates `/users/{uid}` so the Admin app can count total users
     * and view active prayer district / device stats without exposing user PII to peers.
     */
    suspend fun upsertUserProfile(
        districtNameBn: String,
        deviceModel: String,
        appVersion: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val user = auth.currentUser
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path

            val safeName = (user?.displayName?.trim()?.takeIf { it.isNotBlank() }
                ?: user?.email?.substringBefore('@')?.trim()?.takeIf { it.isNotBlank() }
                ?: "ব্যবহারকারী")
                .take(100)
            val safeEmail = user?.email?.trim()?.takeIf { it.isNotBlank() }?.take(160)
            val safeDistrict = districtNameBn.trim().takeIf { it.isNotBlank() }?.take(120)
            val safeDevice = deviceModel.trim().takeIf { it.isNotBlank() }?.take(120)
            val safeVersion = appVersion.trim().takeIf { it.isNotBlank() }?.take(40)

            val existing = docRef.get().await()
            if (!existing.exists()) {
                val createPayload = mutableMapOf<String, Any?>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "districtNameBn" to safeDistrict,
                    "deviceModel" to safeDevice,
                    "appVersion" to safeVersion,
                    "platform" to "android",
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any?>(
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "districtNameBn" to safeDistrict,
                    "deviceModel" to safeDevice,
                    "appVersion" to safeVersion,
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitReport(
        reportId: String,
        type: String,
        message: String,
        device: String,
        appVersion: String
    ): Result<Unit> {
        var docPath = "reports/$reportId"
        return try {
            val uid = requireUserId()
            require(reportId.matches(VALID_ID_REGEX)) { "Invalid report ID format" }
            val safeType = if (type == ReportStore.TYPE_SUGGESTION) {
                ReportStore.TYPE_SUGGESTION
            } else {
                ReportStore.TYPE_PROBLEM
            }
            val safeMsg = message.trim()
            require(safeMsg.length in 5..2000) { "Report message must be 5..2000 characters" }
            val safeDevice = device.trim().ifBlank { "Android" }.take(140)
            val safeVersion = appVersion.trim().ifBlank { "1.0" }.take(40)
            val safeEmail = auth.currentUser?.email?.trim()?.takeIf { it.isNotBlank() }?.take(160)

            val docRef = db.collection("reports").document(reportId)
            docPath = docRef.path
            val payload = mutableMapOf<String, Any?>(
                "userId" to uid,
                "userEmail" to safeEmail,
                "type" to safeType,
                "message" to safeMsg,
                "device" to safeDevice,
                "appVersion" to safeVersion,
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncPendingReports(
        reportStore: ReportStore,
        device: String,
        appVersion: String
    ): Result<Int> {
        return try {
            requireUserId()
            var count = 0
            for (item in reportStore.pending()) {
                val res = submitReport(
                    reportId = item.id,
                    type = item.type,
                    message = item.message,
                    device = item.device.ifBlank { device },
                    appVersion = item.appVersion.ifBlank { appVersion }
                )
                if (res.isSuccess) {
                    reportStore.markSynced(item.id)
                    count++
                }
            }
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeActiveAnnouncements(): Flow<List<CloudAnnouncement>> = flow {
        val uid = requireUserId()
        val path = "announcements"
        emitAll(
            db.collection(path)
                .whereEqualTo("active", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudAnnouncement::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun fetchGlobalConfig(context: Context): Result<CloudAppConfig?> {
        val docPath = "app_config/global"
        return try {
            requireUserId()
            val snapshot = db.collection("app_config").document("global").get().await()
            if (!snapshot.exists()) return Result.success(null)
            val cfg = snapshot.toObject(
                CloudAppConfig::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            if (cfg != null && cfg.shareUrl.startsWith("http")) {
                AppShareConfigStore.saveCustomShareLink(
                    context = context,
                    shareUrl = cfg.shareUrl,
                    shareTitle = cfg.shareTitle.takeIf { it.isNotBlank() },
                    shareMessage = cfg.shareMessage.takeIf { it.isNotBlank() }
                )
            }
            Result.success(cfg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    suspend fun fetchAdminDailyHadith(dateKey: String): Result<CloudDailyHadith?> {
        val docPath = "daily_hadiths/$dateKey"
        return try {
            requireUserId()
            require(dateKey.matches(VALID_ID_REGEX)) { "Invalid dateKey format" }
            val snapshot = db.collection("daily_hadiths").document(dateKey).get().await()
            if (!snapshot.exists()) return Result.success(null)
            val hadith = snapshot.toObject(
                CloudDailyHadith::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(hadith)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    companion object {
        private val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")
    }
}
