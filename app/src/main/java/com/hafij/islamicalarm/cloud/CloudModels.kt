package com.hafij.islamicalarm.cloud

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CloudUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val districtNameBn: String = "",
    val deviceModel: String = "",
    val appVersion: String = "",
    val platform: String = "android",
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudUserReport(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val type: String = "problem",
    val message: String = "",
    val device: String = "",
    val appVersion: String = "",
    val status: String = "pending",
    val adminReply: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudAnnouncement(
    @DocumentId val id: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = "notice",
    val active: Boolean = true,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudAppConfig(
    @DocumentId val id: String = "",
    val shareTitle: String = "",
    val shareMessage: String = "",
    val shareUrl: String = "",
    val noticeBanner: String = "",
    val minVersionCode: Long = 1L,
    val maintenanceMode: Boolean = false,
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudDailyHadith(
    @DocumentId val id: String = "",
    val topicBn: String = "",
    val arabicText: String = "",
    val bengaliText: String = "",
    val referenceBn: String = "",
    val gradeBn: String = "",
    val updatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
