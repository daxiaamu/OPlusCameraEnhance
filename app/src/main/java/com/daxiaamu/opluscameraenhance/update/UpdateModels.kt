package com.daxiaamu.opluscameraenhance.update

data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val publishedAt: String?,
    val changelog: String,
    val maxForcedVersionCode: Long,
    val policyRevision: Long,
    val urls: List<String>,
    val sha256: String,
    val size: Long?,
    val digest: String
) {
    fun isRequired(currentVersionCode: Long) =
        currentVersionCode <= maxForcedVersionCode && versionCode > currentVersionCode
}

sealed interface CheckState {
    data object Idle : CheckState
    data object Checking : CheckState
    data object UpToDate : CheckState
    data class Available(val update: UpdateManifest, val required: Boolean) : CheckState
    data class Failed(val message: String) : CheckState
}

sealed interface DownloadState {
    data object NotStarted : DownloadState
    data class Downloading(val percent: Int?) : DownloadState
    data object Verifying : DownloadState
    data class ReadyToInstall(val versionCode: Long) : DownloadState
    data class NeedsAuthorization(val versionCode: Long) : DownloadState
    data class LaunchingInstaller(val versionCode: Long) : DownloadState
    data class Failed(val message: String) : DownloadState
}
