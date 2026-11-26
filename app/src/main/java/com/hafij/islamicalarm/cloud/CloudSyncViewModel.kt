package com.hafij.islamicalarm.cloud

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.report.ReportStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CloudSyncViewModel(
    private val repository: CloudSyncRepository,
    val currentUserId: String
) : ViewModel() {

    val announcementsState: StateFlow<CloudUiState<List<CloudAnnouncement>>> =
        repository.observeActiveAnnouncements()
            .map<List<CloudAnnouncement>, CloudUiState<List<CloudAnnouncement>>> {
                CloudUiState.Success(it)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing announcements", rootError)
                emit(CloudUiState.Error(rootError.message ?: "ঘোষণা লোড করা যায়নি"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CloudUiState.Loading
            )

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _globalConfig = MutableStateFlow<CloudAppConfig?>(null)
    val globalConfig: StateFlow<CloudAppConfig?> = _globalConfig.asStateFlow()

    fun syncUserSession(
        context: Context,
        districtNameBn: String,
        appVersion: String
    ) {
        val appCtx = context.applicationContext
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val deviceInfo = "$deviceModel, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        viewModelScope.launch {
            val profileResult = repository.upsertUserProfile(
                districtNameBn = districtNameBn,
                deviceModel = deviceModel,
                appVersion = appVersion
            )
            val reportStore = ReportStore(appCtx)
            val reportsResult = repository.syncPendingReports(
                reportStore = reportStore,
                device = deviceInfo,
                appVersion = appVersion
            )
            val cfgResult = repository.fetchGlobalConfig(appCtx)
            cfgResult.getOrNull()?.let { _globalConfig.value = it }

            if (profileResult.isSuccess) {
                val syncedReports = reportsResult.getOrDefault(0)
                _syncMessage.value = if (syncedReports > 0) {
                    "ক্লাউড সিঙ্ক সম্পন্ন • $syncedReports টি রিপোর্ট পাঠানো হয়েছে"
                } else {
                    "ক্লাউড ডাটাবেস সংযুক্ত ✅"
                }
            } else {
                _syncMessage.value = "সিঙ্ক সাময়িকভাবে ব্যর্থ হয়েছে"
            }
        }
    }

    companion object {
        private const val TAG = "CloudSyncVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                CloudSyncViewModel(CloudSyncRepository(db), currentUserId)
            }
        }
    }
}
