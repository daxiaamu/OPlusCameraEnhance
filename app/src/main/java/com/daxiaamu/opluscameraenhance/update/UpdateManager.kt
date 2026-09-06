package com.daxiaamu.opluscameraenhance.update

import android.app.Activity
import com.daxiaamu.opluscameraenhance.R
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import com.daxiaamu.opluscameraenhance.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.Executors

object UpdateManager {
    private const val CHANNEL = "stable"
    private const val MAX_RESPONSE_BYTES = 1024 * 1024
    private const val MIN_SOURCE_COUNT = 5
    private const val PREFS = "app_update"
    private val USER_AGENT = "OPlusCameraEnhance/${BuildConfig.VERSION_NAME} Android"

    private val metadataUrls = listOf(
        "https://api.github.com/repos/daxiaamu/OPlusCameraEnhance/contents/update/update.json?ref=main",
        "https://raw.githubusercontent.com/daxiaamu/OPlusCameraEnhance/main/update/update.json",
        "https://cdn.jsdelivr.net/gh/daxiaamu/OPlusCameraEnhance@main/update/update.json",
        "https://fastly.jsdelivr.net/gh/daxiaamu/OPlusCameraEnhance@main/update/update.json",
        "https://gcore.jsdelivr.net/gh/daxiaamu/OPlusCameraEnhance@main/update/update.json",
        "https://testingcf.jsdelivr.net/gh/daxiaamu/OPlusCameraEnhance@main/update/update.json",
        "https://cdn.statically.io/gh/daxiaamu/OPlusCameraEnhance/main/update/update.json"
    )
    private val executor = Executors.newSingleThreadExecutor()
    private val metadataExecutor = Executors.newFixedThreadPool(metadataUrls.size)
    private val lock = Any()
    private var checking = false
    private var manualResultRequested = false
    private var automaticCheckStarted = false
    private var shownVersionCode: Long? = null
    private var appContext: Context? = null
    private var verifiedApk: File? = null

    var checkState by mutableStateOf<CheckState>(CheckState.Idle)
        private set
    var downloadState by mutableStateOf<DownloadState>(DownloadState.NotStarted)
        private set
    var dialogUpdate by mutableStateOf<UpdateManifest?>(null)
        private set
    var manualMessage by mutableStateOf<String?>(null)
        private set
    var hasUpdateBadge by mutableStateOf(false)
        private set

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun checkAutomatically() {
        synchronized(lock) {
            if (automaticCheckStarted) return
            automaticCheckStarted = true
        }
        startCheck(manual = false)
    }

    fun checkManually() = startCheck(manual = true)

    private fun startCheck(manual: Boolean) {
        val context = checkNotNull(appContext)
        synchronized(lock) {
            if (checking) {
                if (manual) manualResultRequested = true
                return
            }
            checking = true
            manualResultRequested = manual
        }
        manualMessage = null
        checkState = CheckState.Checking
        executor.execute {
            val result = runCatching { fetchTrustedManifest(context) }
            synchronized(lock) {
                val requestedManually = manualResultRequested
                try {
                    result.onSuccess { manifest -> applyCheckResult(context, manifest, requestedManually) }
                        .onFailure {
                            checkState = CheckState.Failed(it.message ?: "检查更新失败")
                            if (requestedManually) manualMessage = context.getString(R.string.update_failed)
                        }
                } finally {
                    checking = false
                    manualResultRequested = false
                }
            }
        }
    }
    private fun applyCheckResult(context: Context, manifest: UpdateManifest, manual: Boolean) {
        val current = BuildConfig.VERSION_CODE.toLong()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit { putLong("highest_policy_revision", manifest.policyRevision); putString("highest_digest", manifest.digest) }
        val stateVersion = when (val state = downloadState) {
            is DownloadState.ReadyToInstall -> state.versionCode
            is DownloadState.NeedsAuthorization -> state.versionCode
            is DownloadState.LaunchingInstaller -> state.versionCode
            else -> null
        }
        if (stateVersion != null && stateVersion != manifest.versionCode) {
            verifiedApk = null
            downloadState = DownloadState.NotStarted
        }
        val cachedApk = File(context.cacheDir, "updates/update-${manifest.versionCode}.apk")
        cachedApk.parentFile?.listFiles()?.filter {
            it != cachedApk && (it.name.endsWith(".apk") || it.name.endsWith(".download"))
        }?.forEach(File::delete)
        if (cachedApk.isFile && verifyDownloadedApk(context, cachedApk, manifest)) {
            verifiedApk = cachedApk
            downloadState = DownloadState.ReadyToInstall(manifest.versionCode)
        } else if (cachedApk.exists()) {
            cachedApk.delete()
        }
        if (manifest.versionCode <= current) {
            checkState = CheckState.UpToDate
            hasUpdateBadge = false
            if (manual) manualMessage = context.getString(R.string.latest)
            return
        }
        val required = manifest.isRequired(current)
        checkState = CheckState.Available(manifest, required)
        hasUpdateBadge = true
        val skipped = prefs.getLong("skipped_version", -1) == manifest.versionCode
        if (manual || required || (!skipped && shownVersionCode != manifest.versionCode)) {
            shownVersionCode = manifest.versionCode
            dialogUpdate = manifest
        }
    }

    private fun fetchTrustedManifest(context: Context): UpdateManifest {
        check(metadataUrls.size >= MIN_SOURCE_COUNT)
        val highestAccepted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("highest_policy_revision", 0)
        val responses = metadataExecutor.invokeAll(metadataUrls.mapIndexed { index, source ->
            java.util.concurrent.Callable {
                runCatching { Triple(index, URI(source).host.lowercase(), fetchManifest(source)) }.getOrNull()
            }
        }).mapNotNull { it.get() }.filter { it.third.policyRevision >= highestAccepted }
        val persistedDigest = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("highest_digest", null)
        check(responses.none { it.third.policyRevision == highestAccepted && persistedDigest != null && it.third.digest != persistedDigest }) { "更新清单与已接受版本冲突" }
        val conflicts = responses.groupBy { it.third.policyRevision }.filterValues { group -> group.map { it.third.digest }.distinct().size > 1 }.keys
        check(conflicts.isEmpty()) { "更新源内容冲突，请稍后重试" }
        val authority = responses.firstOrNull { it.first == 0 }?.third
        authority?.let { trusted ->
            val conflict = responses.any { it.third.policyRevision == trusted.policyRevision && it.third.digest != trusted.digest }
            check(!conflict) { "更新源存在同 revision 内容冲突" }
            return trusted
        }
        return responses.groupBy { it.third.policyRevision to it.third.digest }
            .filterValues { group -> group.map { if (it.second.endsWith("jsdelivr.net")) "jsdelivr.net" else if (it.second.endsWith("githubusercontent.com")) "github.com" else it.second }.distinct().size >= 2 }
            .maxByOrNull { it.key.first }?.value?.first()?.third
            ?: error("无法从更新源获得可信结果")
    }

    private fun fetchManifest(source: String): UpdateManifest {
        val separator = if ('?' in source) '&' else '?'
        val connection = URL("$source${separator}t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.instanceFollowRedirects = true
        try {
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            check(connection.url.protocol == "https") { "更新源发生非 HTTPS 重定向" }
            val bytes = connection.inputStream.use { input ->
                val data = input.readNBytes(MAX_RESPONSE_BYTES + 1)
                check(data.size <= MAX_RESPONSE_BYTES) { "更新元数据过大" }
                data
            }
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            val json = if (source.contains("api.github.com")) {
                val encoded = root.getString("content").replace("\n", "")
                JSONObject(String(Base64.getDecoder().decode(encoded), Charsets.UTF_8))
            } else root
            // A mutable pointer binds an immutable manifest to its digest and expiry.
            val manifestUrl = json.getString("manifestUrl")
            check(URI(manifestUrl).scheme == "https" && URI(manifestUrl).host != null)
            val expected = json.getString("manifestSha256")
            check(expected.matches(Regex("[0-9a-f]{64}")))
            check(java.time.Instant.parse(json.getString("expiresAt")).isAfter(java.time.Instant.now())) { "更新清单已过期" }
            val manifestConnection = URL(manifestUrl).openConnection() as HttpURLConnection
            manifestConnection.connectTimeout = 6000
            manifestConnection.readTimeout = 8000
            manifestConnection.setRequestProperty("User-Agent", USER_AGENT)
            try {
                check(manifestConnection.responseCode in 200..299 && manifestConnection.url.protocol == "https")
                val content = manifestConnection.inputStream.use { it.readNBytes(MAX_RESPONSE_BYTES + 1) }
                check(content.size <= MAX_RESPONSE_BYTES && sha256(content) == expected)
                val manifest = parseManifest(JSONObject(content.toString(Charsets.UTF_8)))
                check(manifest.policyRevision == json.getLong("policyRevision"))
                return manifest
            } finally { manifestConnection.disconnect() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseManifest(json: JSONObject): UpdateManifest {
        check(listOf("schemaVersion","versionCode","maxForcedVersionCode","policyRevision").all {
            val value=json.get(it); value is Int || value is Long
        }) { "Invalid numeric fields" }
        check(json.getInt("schemaVersion") == 1)
        check(json.getString("channel") == CHANNEL) { "更新渠道不匹配" }
        val versionCode = json.getLong("versionCode")
        val forced = json.getLong("maxForcedVersionCode")
        val revision = json.getLong("policyRevision")
        check(versionCode > 0 && forced in 0 until versionCode && revision > 0)
        val sha = json.optString("sha256", json.optString("apkSha256"))
        check(sha.matches(Regex("[0-9a-f]{64}"))) { "SHA-256 非法" }
        val urls = buildList {
            json.optJSONArray("urls")?.let { array ->
                repeat(array.length()) { add(array.getString(it)) }
            }
            json.optString("url", json.optString("apkUrl")).takeIf(String::isNotBlank)?.let(::add)
        }.distinct()
        check(urls.size in MIN_SOURCE_COUNT..20 && urls.all { URI(it).scheme == "https" && URI(it).host != null && URI(it).userInfo == null })
        check(urls.map { URI(it).host.lowercase() }.distinct().size >= MIN_SOURCE_COUNT)
        val canonical = listOf(versionCode, json.getString("versionName"), forced, revision, sha, json.optString("publishedAt"), json.optString("changelog", json.optString("notes")), json.optLong("size", -1), urls.joinToString("\n")).joinToString("|")
        return UpdateManifest(
            versionCode = versionCode,
            versionName = json.getString("versionName"),
            publishedAt = json.optString("publishedAt").takeIf(String::isNotBlank),
            changelog = json.optString("changelog", json.optString("notes", "暂无更新说明")),
            maxForcedVersionCode = forced,
            policyRevision = revision,
            urls = urls,
            sha256 = sha,
            size = json.optLong("size", -1).takeIf { it >= 0 },
            digest = sha256(canonical.toByteArray())
        )
    }

    fun skip(update: UpdateManifest) {
        if (update.isRequired(BuildConfig.VERSION_CODE.toLong())) return
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putLong("skipped_version", update.versionCode)?.apply()
        dismiss()
    }

    fun ignore(update: UpdateManifest) {
        if (update.isRequired(BuildConfig.VERSION_CODE.toLong())) return
        hasUpdateBadge = true
        dismiss()
    }

    fun dismiss() {
        dialogUpdate = null
        if (downloadState !is DownloadState.Downloading && downloadState !is DownloadState.Verifying) {
            downloadState = DownloadState.NotStarted
        }
    }

    fun download(update: UpdateManifest) {
        val context = appContext ?: return
        if (downloadState is DownloadState.Downloading || downloadState is DownloadState.Verifying) return
        downloadState = DownloadState.Downloading(null)
        executor.execute {
            val destination = File(context.cacheDir, "updates/update-${update.versionCode}.apk")
            runCatching {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                    putLong("active_version", update.versionCode)
                    putString("active_sha256", update.sha256)
                    putString("active_urls", update.urls.joinToString("\n"))
                }
                destination.parentFile?.mkdirs()
                if (destination.isFile && verifyDownloadedApk(context, destination, update)) {
                    verifiedApk = destination
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                        putLong("verified_version", update.versionCode)
                        remove("active_url_index")
                    }
                    downloadState = DownloadState.ReadyToInstall(update.versionCode)
                    return@runCatching
                }
                destination.delete()
                var failure: Throwable? = null
                for ((urlIndex, url) in update.urls.withIndex()) {
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putInt("active_url_index", urlIndex) }
                    val temporary = File(destination.parentFile, destination.name + ".download")
                    temporary.delete()
                    val attempt = runCatching {
                        downloadOne(url, temporary, update)
                        check(temporary.renameTo(destination)) { "无法保存已验证安装包" }
                        check(verifyDownloadedApk(context, destination, update)) { "安装包身份校验失败" }
                    }
                    if (attempt.isSuccess) {
                        verifiedApk = destination
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                            putLong("verified_version", update.versionCode)
                            remove("active_url_index")
                        }
                        downloadState = DownloadState.ReadyToInstall(update.versionCode)
                        return@runCatching
                    }
                    failure = attempt.exceptionOrNull()
                    temporary.delete()
                    destination.delete()
                }
                throw failure ?: error("所有下载源均不可用")
            }.onFailure {
                destination.delete()
                downloadState = DownloadState.Failed(it.message ?: "下载失败")
            }
        }
    }

    private fun downloadOne(source: String, file: File, update: UpdateManifest) {
        val connection = URL(source).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", USER_AGENT)
        try {
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            check(connection.url.protocol == "https") { "下载发生非 HTTPS 重定向" }
            val total = connection.contentLengthLong.takeIf { it > 0 }
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    var lastUpdate = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        copied += count
                        check(copied <= (update.size ?: 268435456L)) { "安装包超出预期大小" }
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 300) {
                            val percent = total?.let { ((copied * 100 / it).coerceAtMost(99)).toInt() }
                            downloadState = DownloadState.Downloading(percent)
                            lastUpdate = now
                        }
                    }
                    output.fd.sync()
                }
            }
            downloadState = DownloadState.Verifying
            check(MessageDigest.isEqual(digest.digest(), update.sha256.hexToBytes())) { "SHA-256 不匹配" }
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyDownloadedApk(context: Context, file: File, update: UpdateManifest): Boolean = runCatching {
        check(sha256(file) == update.sha256)
        check(update.size == null || file.length() == update.size)
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: error("无法读取 APK")
        check(archive.packageName == context.packageName)
        check(archive.longVersionCode == update.versionCode)
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        fun signers(info: android.content.pm.PackageInfo): Set<String> {
            val signing = info.signingInfo ?: return emptySet()
            val signatures = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
            return signatures.map { sha256(it.toByteArray()) }.toSet()
        }
                val oldSigners = installed.signingInfo!!.apkContentsSigners.map { sha256(it.toByteArray()) }.toSet()
        val newSigners = archive.signingInfo!!.apkContentsSigners.map { sha256(it.toByteArray()) }.toSet()
        check(oldSigners.isNotEmpty() && if (oldSigners.size > 1 || newSigners.size > 1) oldSigners == newSigners
            else signers(archive).containsAll(oldSigners))
        true
    }.getOrDefault(false)

    fun install(activity: Activity, update: UpdateManifest) {
        val file = verifiedApk
        val stateVersion = when (val state = downloadState) {
            is DownloadState.ReadyToInstall -> state.versionCode
            is DownloadState.NeedsAuthorization -> state.versionCode
            else -> null
        }
        if (file == null || stateVersion != update.versionCode || !verifyDownloadedApk(activity, file, update)) {
            file?.delete()
            verifiedApk = null
            downloadState = DownloadState.Failed("已缓存安装包与当前更新版本不匹配，请重新下载")
            return
        }
        if (!activity.packageManager.canRequestPackageInstalls()) {
            downloadState = DownloadState.NeedsAuthorization(update.versionCode)
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${activity.packageName}".toUri()))
            return
        }
        downloadState = DownloadState.ReadyToInstall(update.versionCode)
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun resumeInstallation(activity: Activity) {
        val state = downloadState as? DownloadState.NeedsAuthorization ?: return
        val update = dialogUpdate?.takeIf { it.versionCode == state.versionCode } ?: return
        if (activity.packageManager.canRequestPackageInstalls()) install(activity, update)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
