package com.example.cad.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.net.toUri
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.squareup.moshi.Moshi
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Phase 12.1 — Configured GitHub Release Repository Coordinates.
 */
data class UpdateRepositoryConfig(
    val owner: String = BuildConfig.GITHUB_RELEASE_OWNER,
    val repo: String = BuildConfig.GITHUB_RELEASE_REPO,
    val apiBaseUrl: String = "https://api.github.com",
    val expectedPackageId: String = EXPECTED_PACKAGE_ID
) {
    val releasesEndpointUrl: String
        get() = "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo/releases?per_page=100"

    companion object {
        const val EXPECTED_PACKAGE_ID = "com.precisioncad.studio"
        const val AUTO_CHECK_THROTTLE_MS = 6L * 60L * 60L * 1000L // 6 hours
    }
}

/**
 * Phase 12.2 — Home-Screen Top-Right Update Icon State.
 * - `0` updates available -> White icon, no number, no dot.
 * - `1` update available -> Gold/yellow icon, NO number and NO dot.
 * - `> 1` updates available -> Gold/yellow icon with integer count beside the icon.
 */
enum class HomeUpdateIconColor {
    WHITE,
    GOLD
}

data class HomeUpdateIconVisualState(
    val updatesBehindCount: Int,
    val iconColor: HomeUpdateIconColor,
    val badgeCountText: String?
) {
    val showBadgeCount: Boolean
        get() = badgeCountText != null

    companion object {
        fun fromUpdatesBehindCount(count: Int): HomeUpdateIconVisualState {
            val safeCount = count.coerceAtLeast(0)
            return when {
                safeCount == 0 -> HomeUpdateIconVisualState(
                    updatesBehindCount = 0,
                    iconColor = HomeUpdateIconColor.WHITE,
                    badgeCountText = null
                )
                safeCount == 1 -> HomeUpdateIconVisualState(
                    updatesBehindCount = 1,
                    iconColor = HomeUpdateIconColor.GOLD,
                    badgeCountText = null
                )
                else -> HomeUpdateIconVisualState(
                    updatesBehindCount = safeCount,
                    iconColor = HomeUpdateIconColor.GOLD,
                    badgeCountText = safeCount.toString()
                )
            }
        }
    }
}

/**
 * Phase 12.3 — Structured Machine-Readable Release Metadata embedded in GitHub Release Notes.
 */
data class CadReleaseMetadata(
    val schemaVersion: Int = 1,
    val versionName: String,
    val versionCode: Int,
    val packageId: String = UpdateRepositoryConfig.EXPECTED_PACKAGE_ID,
    val minAndroidSdk: Int = 24,
    val targetAndroidSdk: Int = 37,
    val compileAndroidSdk: String = "37.2",
    val apkFileName: String,
    val apkSha256: String = "",
    val apkSizeBytes: Long = 0L,
    val signingCertSha256: String = "",
    val publishedAtUtc: String = ""
)

enum class ChangelogCategory(val displayTitle: String) {
    NEW("New"),
    FIXES("Fixes"),
    OPTIMISATIONS("Optimisations"),
    SECURITY("Security"),
    OTHER("Highlights")
}

data class ChangelogSection(
    val category: ChangelogCategory,
    val items: List<String>
)

data class GitHubReleaseAsset(
    val name: String,
    val browserDownloadUrl: String,
    val sizeBytes: Long,
    val contentType: String
)

data class GitHubReleaseRecord(
    val id: Long,
    val tagName: String,
    val releaseTitle: String,
    val versionName: String,
    val versionCode: Int,
    val publishedAtIso: String,
    val publishedDateFormatted: String,
    val isDraft: Boolean,
    val isPrerelease: Boolean,
    val metadata: CadReleaseMetadata,
    val apkAsset: GitHubReleaseAsset?,
    val sections: List<ChangelogSection>,
    val cleanMarkdownNotes: String,
    val isInstalledVersion: Boolean = false,
    val isLatestStable: Boolean = false
)

/**
 * Phase 12.8 — Complete Update & Installation State Machine.
 */
sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState
    data class Checking(val isManual: Boolean) : AppUpdateUiState
    data class UpToDate(
        val installedVersionName: String,
        val installedVersionCode: Int,
        val lastCheckedEpochMs: Long
    ) : AppUpdateUiState

    data class UpdateAvailable(
        val latestRelease: GitHubReleaseRecord,
        val updatesBehindCount: Int
    ) : AppUpdateUiState

    data class Downloading(
        val targetRelease: GitHubReleaseRecord,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : AppUpdateUiState {
        val progressFraction: Float
            get() = if (totalBytes > 0L) {
                (bytesDownloaded.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
            } else 0f
    }

    data class DownloadFailed(
        val targetRelease: GitHubReleaseRecord?,
        val reason: String
    ) : AppUpdateUiState

    data class DownloadedVerified(
        val targetRelease: GitHubReleaseRecord,
        val verifiedApkFile: File,
        val computedSha256: String
    ) : AppUpdateUiState

    data class InstallSourcePermissionRequired(
        val targetRelease: GitHubReleaseRecord,
        val verifiedApkFile: File
    ) : AppUpdateUiState

    data class InstallerLaunching(
        val targetRelease: GitHubReleaseRecord,
        val sessionId: Int
    ) : AppUpdateUiState

    data class AwaitingSystemConfirmation(
        val targetRelease: GitHubReleaseRecord,
        val sessionId: Int,
        val confirmationIntent: Intent? = null
    ) : AppUpdateUiState

    data class InstallSucceeded(
        val targetRelease: GitHubReleaseRecord
    ) : AppUpdateUiState

    data class InstallCancelled(
        val targetRelease: GitHubReleaseRecord,
        val message: String
    ) : AppUpdateUiState

    data class InstallFailed(
        val targetRelease: GitHubReleaseRecord?,
        val reason: String
    ) : AppUpdateUiState
}

typealias CadReleaseEntry = GitHubReleaseRecord
typealias CadUpdateUiState = AppUpdateUiState

/**
 * Result of the 10-point APK verification check (Section 12.7).
 */
data class ApkVerificationResult(
    val isValid: Boolean,
    val failureReason: String? = null,
    val computedSha256: String = "",
    val archivePackageName: String? = null,
    val archiveVersionCode: Int? = null,
    val archiveVersionName: String? = null,
    val archiveSigningCertSha256: String? = null
)

/**
 * Phase 12.3 & 12.4 — Parser for GitHub Release JSON payloads, embedded `PRECISIONCAD-RELEASE-METADATA`,
 * and categorized Markdown release notes (`New`, `Fixes`, `Optimisations`, `Security`, `Other`).
 */
object CadReleaseParser {
    private val moshiAnyAdapter = Moshi.Builder().build().adapter(Any::class.java)

    private fun Map<*, *>.optStr(key: String, default: String = ""): String =
        (this[key] as? String) ?: default

    private fun Map<*, *>.optBool(key: String, default: Boolean = false): Boolean =
        (this[key] as? Boolean) ?: default

    private fun Map<*, *>.optIntVal(key: String, default: Int = 0): Int =
        (this[key] as? Number)?.toInt() ?: (this[key] as? String)?.toIntOrNull() ?: default

    private fun Map<*, *>.optLongVal(key: String, default: Long = 0L): Long =
        (this[key] as? Number)?.toLong() ?: (this[key] as? String)?.toLongOrNull() ?: default

    private val METADATA_BLOCK_REGEX = Regex(
        pattern = """<!--\s*PRECISIONCAD-RELEASE-METADATA\s*(.*?)\s*-->""",
        options = setOf(RegexOption.DOT_MATCHES_ALL)
    )
    private val VERSION_TAG_REGEX = Regex("""v?(\d+)\.(\d+)\.(\d+)""")

    fun parseEmbeddedMetadata(rawBody: String): CadReleaseMetadata? {
        val match = METADATA_BLOCK_REGEX.find(rawBody) ?: return null
        val content = match.groupValues[1].trim()
        if (content.isEmpty()) return null

        if (content.startsWith("{")) {
            return runCatching {
                val obj = moshiAnyAdapter.fromJson(content) as? Map<*, *> ?: return null
                val vName = obj.optStr("versionName", "").trim()
                val vCode = obj.optIntVal("versionCode", -1)
                if (vName.isEmpty() || vCode <= 0) return null
                val sha = obj.optStr("apkSha256", obj.optStr("sha256", ""))
                    .removePrefix("sha256:")
                    .trim()
                    .lowercase(Locale.ROOT)
                val assetName = obj.optStr(
                    "apkFileName",
                    obj.optStr("assetName", "PrecisionCAD-Studio-v$vName-b$vCode-release.apk")
                ).trim()
                CadReleaseMetadata(
                    schemaVersion = obj.optIntVal("schemaVersion", 1),
                    versionName = vName,
                    versionCode = vCode,
                    packageId = obj.optStr("packageId", UpdateRepositoryConfig.EXPECTED_PACKAGE_ID).trim(),
                    minAndroidSdk = obj.optIntVal("minAndroidSdk", 24),
                    targetAndroidSdk = obj.optIntVal("targetAndroidSdk", 37),
                    compileAndroidSdk = obj.optStr("compileAndroidSdk", "37.2"),
                    apkFileName = assetName,
                    apkSha256 = sha,
                    apkSizeBytes = obj.optLongVal("apkSizeBytes", 0L),
                    signingCertSha256 = obj.optStr("signingCertSha256", "").lowercase(Locale.ROOT),
                    publishedAtUtc = obj.optStr("publishedAtUtc", "")
                )
            }.getOrNull()
        }

        val kv = mutableMapOf<String, String>()
        content.lines().forEach { line ->
            val trimmed = line.trim()
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx > 0) {
                val key = trimmed.substring(0, colonIdx).trim()
                val value = trimmed.substring(colonIdx + 1).trim()
                kv[key] = value
            }
        }
        val vName = kv["versionName"].orEmpty()
        val vCode = kv["versionCode"]?.toIntOrNull() ?: -1
        if (vName.isEmpty() || vCode <= 0) return null
        val pkgId = kv["packageId"].orEmpty().ifBlank { UpdateRepositoryConfig.EXPECTED_PACKAGE_ID }
        val assetName = (kv["assetName"] ?: kv["apkFileName"]).orEmpty()
            .ifBlank { "PrecisionCAD-Studio-v$vName-b$vCode-release.apk" }
        val sha = (kv["sha256"] ?: kv["apkSha256"]).orEmpty()
            .removePrefix("sha256:")
            .trim()
            .lowercase(Locale.ROOT)

        return CadReleaseMetadata(
            schemaVersion = kv["schemaVersion"]?.toIntOrNull() ?: 1,
            versionName = vName,
            versionCode = vCode,
            packageId = pkgId,
            minAndroidSdk = kv["minAndroidSdk"]?.toIntOrNull() ?: 24,
            targetAndroidSdk = kv["targetAndroidSdk"]?.toIntOrNull() ?: 37,
            compileAndroidSdk = kv["compileAndroidSdk"] ?: "37.2",
            apkFileName = assetName,
            apkSha256 = sha,
            apkSizeBytes = kv["apkSizeBytes"]?.toLongOrNull() ?: 0L,
            signingCertSha256 = kv["signingCertSha256"].orEmpty().lowercase(Locale.ROOT),
            publishedAtUtc = kv["publishedAtUtc"].orEmpty()
        )
    }

    fun stripMetadataComment(rawBody: String): String {
        return rawBody.replace(METADATA_BLOCK_REGEX, "")
            .replace(Regex("""\n---\s*$"""), "")
            .trim()
    }

    fun inferVersionFromTag(tagName: String, fallbackId: Long = 1L): Pair<String, Int> {
        val cleanTag = tagName.trim().removePrefix("v").removePrefix("V")
        val explicitBuildCode = Regex("""-b(\d+)""").find(cleanTag)?.groupValues?.get(1)?.toIntOrNull()
        val match = VERSION_TAG_REGEX.find(tagName)
        if (match != null) {
            val major = match.groupValues[1].toIntOrNull() ?: 1
            val minor = match.groupValues[2].toIntOrNull() ?: 0
            val patch = match.groupValues[3].toIntOrNull() ?: 1
            val versionName = "$major.$minor.$patch"
            val versionCode = explicitBuildCode ?: if (major == 1 && minor == 0) {
                patch
            } else {
                patch.coerceAtLeast(1)
            }
            return versionName to versionCode
        }
        val trailingInt = explicitBuildCode ?: Regex("""(\d+)$""").find(cleanTag)?.groupValues?.get(1)?.toIntOrNull()
        val code = trailingInt ?: fallbackId.toInt().coerceAtLeast(1)
        return cleanTag.ifBlank { "1.0.$code" } to code
    }

    fun parseChangelogSections(cleanMarkdown: String): List<ChangelogSection> {
        if (cleanMarkdown.isBlank()) return emptyList()

        val bucketMap = linkedMapOf<ChangelogCategory, MutableList<String>>()
        var currentCategory = ChangelogCategory.OTHER

        cleanMarkdown.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line == "---") return@forEach

            if (line.startsWith("#")) {
                val heading = line.trimStart('#').trim().lowercase(Locale.ROOT)
                currentCategory = when {
                    heading.contains("new") || heading.contains("add") || heading.contains("feature") ->
                        ChangelogCategory.NEW
                    heading.contains("fix") || heading.contains("bug") || heading.contains("remediat") || heading.contains("resolv") ->
                        ChangelogCategory.FIXES
                    heading.contains("optim") || heading.contains("perf") || heading.contains("improv") || heading.contains("polish") ->
                        ChangelogCategory.OPTIMISATIONS
                    heading.contains("secur") || heading.contains("verif") || heading.contains("sign") ->
                        ChangelogCategory.SECURITY
                    else -> ChangelogCategory.OTHER
                }
            } else if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ")) {
                val itemText = line.drop(2).trim()
                if (itemText.isNotEmpty()) {
                    bucketMap.getOrPut(currentCategory) { mutableListOf() }.add(itemText)
                }
            } else if (!line.startsWith("<!--") && !line.startsWith("```")) {
                bucketMap.getOrPut(currentCategory) { mutableListOf() }.add(line)
            }
        }

        return bucketMap.entries
            .filter { it.value.isNotEmpty() }
            .map { ChangelogSection(category = it.key, items = it.value.toList()) }
    }

    fun parseReleasesJson(
        jsonArrayString: String,
        installedVersionCode: Int,
        includePrereleases: Boolean = false,
        expectedPackageId: String = UpdateRepositoryConfig.EXPECTED_PACKAGE_ID
    ): List<GitHubReleaseRecord> {
        val rawList = runCatching { moshiAnyAdapter.fromJson(jsonArrayString) as? List<*> }.getOrNull()
            ?: return emptyList()
        val parsed = mutableListOf<GitHubReleaseRecord>()

        for (i in rawList.indices) {
            val obj = rawList[i] as? Map<*, *> ?: continue
            val isDraft = obj.optBool("draft", false)
            if (isDraft) continue

            val isPrerelease = obj.optBool("prerelease", false)
            if (isPrerelease && !includePrereleases) continue

            val id = obj.optLongVal("id", (i + 1).toLong())
            val tagName = obj.optStr("tag_name", "")
            val releaseName = obj.optStr("name", "").ifBlank { tagName }
            val publishedAt = obj.optStr("published_at", "")
            val formattedDate = publishedAt.take(10).ifBlank { "Release" }
            val rawBody = obj.optStr("body", "")

            val assetsRaw = obj["assets"] as? List<*> ?: emptyList<Any?>()
            val assets = mutableListOf<GitHubReleaseAsset>()
            val assetDigests = mutableMapOf<String, String>()
            for (assetItem in assetsRaw) {
                val assetObj = assetItem as? Map<*, *> ?: continue
                val assetName = assetObj.optStr("name", "")
                val downloadUrl = assetObj.optStr("browser_download_url", "")
                val sizeBytes = assetObj.optLongVal("size", 0L)
                val contentType = assetObj.optStr("content_type", "")
                val digestRaw = assetObj.optStr("digest", "")
                if (assetName.isNotBlank() && downloadUrl.isNotBlank()) {
                    assets.add(
                        GitHubReleaseAsset(
                            name = assetName,
                            browserDownloadUrl = downloadUrl,
                            sizeBytes = sizeBytes,
                            contentType = contentType
                        )
                    )
                    if (digestRaw.startsWith("sha256:", ignoreCase = true)) {
                        assetDigests[assetName] = digestRaw.substringAfter(":").trim().lowercase(Locale.ROOT)
                    }
                }
            }

            val embeddedMeta = parseEmbeddedMetadata(rawBody)
            val (inferredVersionName, inferredVersionCode) = inferVersionFromTag(tagName, id)
            val apkAssets = assets.filter {
                it.name.endsWith(".apk", ignoreCase = true) &&
                    !it.name.contains("debug", ignoreCase = true)
            }
            val apkAsset = if (embeddedMeta != null) {
                apkAssets.firstOrNull { it.name == embeddedMeta.apkFileName }
                    ?: if (apkAssets.size == 1) apkAssets.first() else null
            } else {
                if (apkAssets.size == 1) apkAssets.first() else null
            }

            val resolvedDigest = embeddedMeta?.apkSha256?.ifBlank {
                apkAsset?.let { assetDigests[it.name] }.orEmpty()
            } ?: apkAsset?.let { assetDigests[it.name] }.orEmpty()

            val metadata = (embeddedMeta ?: CadReleaseMetadata(
                versionName = inferredVersionName,
                versionCode = inferredVersionCode,
                packageId = expectedPackageId,
                apkFileName = apkAsset?.name ?: "PrecisionCAD-Studio-v$inferredVersionName-b$inferredVersionCode-release.apk",
                apkSizeBytes = apkAsset?.sizeBytes ?: 0L,
                publishedAtUtc = publishedAt
            )).copy(
                apkSha256 = resolvedDigest,
                apkSizeBytes = if ((embeddedMeta?.apkSizeBytes ?: 0L) > 0L) embeddedMeta!!.apkSizeBytes else (apkAsset?.sizeBytes ?: 0L)
            )

            if (metadata.packageId != expectedPackageId) continue

            val cleanNotes = stripMetadataComment(rawBody)
            val sections = parseChangelogSections(cleanNotes)

            parsed.add(
                GitHubReleaseRecord(
                    id = id,
                    tagName = tagName.ifBlank { "v${metadata.versionName}" },
                    releaseTitle = releaseName.ifBlank { "v${metadata.versionName}" },
                    versionName = metadata.versionName,
                    versionCode = metadata.versionCode,
                    publishedAtIso = publishedAt,
                    publishedDateFormatted = formattedDate,
                    isDraft = isDraft,
                    isPrerelease = isPrerelease,
                    metadata = metadata,
                    apkAsset = apkAsset,
                    sections = sections,
                    cleanMarkdownNotes = cleanNotes
                )
            )
        }

        val sorted = parsed.sortedWith(
            compareByDescending<GitHubReleaseRecord> { it.versionCode }
                .thenByDescending { it.publishedAtIso }
        )
        val latestStableCode = sorted.firstOrNull { !it.isPrerelease && it.apkAsset != null }?.versionCode

        return sorted.map { record ->
            record.copy(
                isInstalledVersion = record.versionCode == installedVersionCode,
                isLatestStable = latestStableCode != null && record.versionCode == latestStableCode && !record.isPrerelease
            )
        }
    }

    /**
     * Phase 12.2 — Computes the number of distinct eligible releases newer than `installedVersionCode`.
     */
    fun computeUpdatesBehindCount(
        releases: List<GitHubReleaseRecord>,
        installedVersionCode: Int,
        includePrereleases: Boolean = false
    ): Int {
        return releases
            .asSequence()
            .filter { !it.isDraft }
            .filter { includePrereleases || !it.isPrerelease }
            .filter { it.apkAsset != null }
            .filter { it.metadata.packageId == UpdateRepositoryConfig.EXPECTED_PACKAGE_ID }
            .filter { it.versionCode > installedVersionCode }
            .map { it.versionCode }
            .distinct()
            .count()
    }
}

/**
 * Phase 12.7 — 10-Point Downloaded APK Verification Engine.
 */
object CadApkVerifier {

    fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { b ->
            String.format(Locale.ROOT, "%02x", b.toInt() and 0xFF)
        }
    }

    fun computeBytesSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { b ->
            String.format(Locale.ROOT, "%02x", b.toInt() and 0xFF)
        }
    }

    fun getInstalledSigningCertSha256(context: Context): String? {
        return runCatching {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            extractPrimarySignerSha256(pkgInfo)
        }.getOrNull()
    }

    fun extractPrimarySignerSha256(pkgInfo: PackageInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo
            if (signingInfo != null) {
                val certs = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                val first = certs?.firstOrNull()?.toByteArray()
                if (first != null && first.isNotEmpty()) {
                    return computeBytesSha256(first)
                }
            }
        }
        @Suppress("DEPRECATION")
        val legacySig = pkgInfo.signatures?.firstOrNull()?.toByteArray()
        if (legacySig != null && legacySig.isNotEmpty()) {
            return computeBytesSha256(legacySig)
        }
        return null
    }

    /**
     * Validates an APK archive against all 10 security & integrity rules before installation.
     */
    fun verifyApkFile(
        file: File,
        expectedMetadata: CadReleaseMetadata,
        installedVersionCode: Int,
        installedIsDebuggable: Boolean,
        installedSigningCertSha256: String?,
        archiveInfoProvider: (File) -> PackageInfo?,
        allowSameVersionReinstall: Boolean = false
    ): ApkVerificationResult {
        // 1. File existence & non-empty check
        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            return ApkVerificationResult(
                isValid = false,
                failureReason = "Downloaded APK file does not exist or is empty."
            )
        }

        // 2. Byte-length check when metadata specifies apkSizeBytes > 0
        if (expectedMetadata.apkSizeBytes > 0L && file.length() != expectedMetadata.apkSizeBytes) {
            return ApkVerificationResult(
                isValid = false,
                failureReason = "APK size mismatch: expected ${expectedMetadata.apkSizeBytes} bytes, got ${file.length()} bytes."
            )
        }

        // 3. SHA-256 digest check
        val computedSha256 = runCatching { computeFileSha256(file) }.getOrElse { err ->
            return ApkVerificationResult(
                isValid = false,
                failureReason = "Failed to compute SHA-256 digest: ${err.message}"
            )
        }
        val expectedSha256 = expectedMetadata.apkSha256.trim().lowercase(Locale.ROOT)
        if (expectedSha256.isNotEmpty() && !computedSha256.equals(expectedSha256, ignoreCase = true)) {
            return ApkVerificationResult(
                isValid = false,
                failureReason = "APK SHA-256 digest mismatch.",
                computedSha256 = computedSha256
            )
        }

        // 4. Archive readability check
        val archiveInfo = archiveInfoProvider(file)
            ?: return ApkVerificationResult(
                isValid = false,
                failureReason = "File is not a valid Android APK archive.",
                computedSha256 = computedSha256
            )

        // 5. Package name identity check
        val archivePkgName = archiveInfo.packageName.orEmpty()
        if (archivePkgName != UpdateRepositoryConfig.EXPECTED_PACKAGE_ID) {
            return ApkVerificationResult(
                isValid = false,
                failureReason = "APK package name mismatch: expected ${UpdateRepositoryConfig.EXPECTED_PACKAGE_ID}, got $archivePkgName.",
                computedSha256 = computedSha256,
                archivePackageName = archivePkgName
            )
        }

        // 6. Version code match & monotonic upgrade check
        val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.versionCode
        }
        if (expectedMetadata.versionCode > 0 && archiveVersionCode != expectedMetadata.versionCode) {
            return ApkVerificationResult(
                isValid = false,
                failureReason = "APK versionCode ($archiveVersionCode) does not match release metadata (${expectedMetadata.versionCode}).",
                computedSha256 = computedSha256,
                archivePackageName = archivePkgName,
                archiveVersionCode = archiveVersionCode
            )
        }
        if (!allowSameVersionReinstall && archiveVersionCode <= installedVersionCode) {
            return ApkVerificationResult(
                isValid = false,
                failureReason = "APK versionCode ($archiveVersionCode) is not newer than installed version ($installedVersionCode).",
                computedSha256 = computedSha256,
                archivePackageName = archivePkgName,
                archiveVersionCode = archiveVersionCode
            )
        }

        // 7. Version name match
        val archiveVersionName = archiveInfo.versionName ?: ""
        if (expectedMetadata.versionName.isNotBlank() &&
            archiveVersionName.isNotBlank() &&
            archiveVersionName != expectedMetadata.versionName
        ) {
            return ApkVerificationResult(
                isValid = false,
                failureReason = "APK versionName ($archiveVersionName) does not match release metadata (${expectedMetadata.versionName}).",
                computedSha256 = computedSha256,
                archivePackageName = archivePkgName,
                archiveVersionCode = archiveVersionCode,
                archiveVersionName = archiveVersionName
            )
        }

        // 8. Debuggable build rejection when installed app is non-debuggable
        val appFlags = archiveInfo.applicationInfo?.flags ?: 0
        val archiveIsDebuggable = (appFlags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!installedIsDebuggable && archiveIsDebuggable) {
            return ApkVerificationResult(
                isValid = false,
                failureReason = "Rejected debuggable APK archive on a production release installation.",
                computedSha256 = computedSha256,
                archivePackageName = archivePkgName,
                archiveVersionCode = archiveVersionCode,
                archiveVersionName = archiveVersionName
            )
        }

        // 9. Signing certificate continuity verification
        val archiveSignerSha256 = extractPrimarySignerSha256(archiveInfo)?.lowercase(Locale.ROOT)
        val expectedSignerSha256 = expectedMetadata.signingCertSha256.trim().lowercase(Locale.ROOT)
        if (expectedSignerSha256.isNotEmpty() && archiveSignerSha256 != null && archiveSignerSha256 != expectedSignerSha256) {
            return ApkVerificationResult(
                isValid = false,
                failureReason = "APK signing certificate does not match release metadata.",
                computedSha256 = computedSha256,
                archivePackageName = archivePkgName,
                archiveVersionCode = archiveVersionCode,
                archiveVersionName = archiveVersionName,
                archiveSigningCertSha256 = archiveSignerSha256
            )
        }
        if (!installedSigningCertSha256.isNullOrBlank() &&
            archiveSignerSha256 != null &&
            !archiveSignerSha256.equals(installedSigningCertSha256, ignoreCase = true)
        ) {
            return ApkVerificationResult(
                isValid = false,
                failureReason = "APK signing certificate does not match the currently installed app.",
                computedSha256 = computedSha256,
                archivePackageName = archivePkgName,
                archiveVersionCode = archiveVersionCode,
                archiveVersionName = archiveVersionName,
                archiveSigningCertSha256 = archiveSignerSha256
            )
        }

        return ApkVerificationResult(
            isValid = true,
            computedSha256 = computedSha256,
            archivePackageName = archivePkgName,
            archiveVersionCode = archiveVersionCode,
            archiveVersionName = archiveVersionName,
            archiveSigningCertSha256 = archiveSignerSha256
        )
    }
}

/**
 * Phase 12 — GitHub Release Catalogue & In-App Updater Repository.
 */
class CadGitHubUpdateRepository(
    context: Context,
    private val scope: CoroutineScope,
    val repoConfig: UpdateRepositoryConfig = UpdateRepositoryConfig()
) : CadInstallSessionCallbacks.Listener {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val updatesDir = File(appContext.filesDir, "updates").apply { mkdirs() }

    val installedVersionName: String = BuildConfig.VERSION_NAME
    val installedVersionCode: Int = BuildConfig.VERSION_CODE

    private val _releases = MutableStateFlow<List<GitHubReleaseRecord>>(emptyList())
    val releases: StateFlow<List<GitHubReleaseRecord>> = _releases.asStateFlow()

    private val _updatesBehindCount = MutableStateFlow(0)
    val updatesBehindCount: StateFlow<Int> = _updatesBehindCount.asStateFlow()

    private val _updateUiState = MutableStateFlow<AppUpdateUiState>(
        AppUpdateUiState.UpToDate(
            installedVersionName = installedVersionName,
            installedVersionCode = installedVersionCode,
            lastCheckedEpochMs = prefs.getLong(KEY_LAST_CHECK_EPOCH_MS, 0L)
        )
    )
    val updateUiState: StateFlow<AppUpdateUiState> = _updateUiState.asStateFlow()
    val uiState: StateFlow<AppUpdateUiState> get() = updateUiState

    private var activeDownloadJob: Job? = null
    private var activeTargetRelease: GitHubReleaseRecord? = null

    init {
        CadInstallSessionCallbacks.listener = this
        loadCachedCatalogueIfPresent()
    }

    private fun loadCachedCatalogueIfPresent() {
        val cachedJson = prefs.getString(KEY_CACHED_RELEASES_JSON, null)
        if (!cachedJson.isNullOrBlank()) {
            applyReleasesJson(cachedJson, updateTimestamp = false)
        } else {
            val synthesizedCurrent = synthesizeInstalledReleaseRecord()
            _releases.value = listOf(synthesizedCurrent)
            _updatesBehindCount.value = 0
        }
    }

    fun synthesizeInstalledReleaseRecord(): GitHubReleaseRecord {
        val meta = CadReleaseMetadata(
            versionName = installedVersionName,
            versionCode = installedVersionCode,
            packageId = repoConfig.expectedPackageId,
            apkFileName = "PrecisionCAD-Studio-v$installedVersionName-b$installedVersionCode-release-signed.apk"
        )
        return GitHubReleaseRecord(
            id = installedVersionCode.toLong(),
            tagName = "v$installedVersionName",
            releaseTitle = "PrecisionCAD Studio v$installedVersionName",
            versionName = installedVersionName,
            versionCode = installedVersionCode,
            publishedAtIso = "",
            publishedDateFormatted = "Installed Build",
            isDraft = false,
            isPrerelease = false,
            metadata = meta,
            apkAsset = null,
            sections = listOf(
                ChangelogSection(
                    category = ChangelogCategory.NEW,
                    items = listOf(
                        "Zoom-adaptive Grid Snap with 1–2–2.5–5 × 10^n quantization and hysteresis",
                        "Strict separation between Visual Polar Tracking (0°–359° + fractional guides) and PolarSnap",
                        "Paper Space opaque title block with zoom-independent screen-space typography",
                        "Horizontal swipeable QDIM option card decks and non-layout-shifting help callouts",
                        "GitHub Release Catalogue with 10-point APK verification and PackageInstaller updater"
                    )
                ),
                ChangelogSection(
                    category = ChangelogCategory.FIXES,
                    items = listOf(
                        "Eliminated keyboard double-inset jump and kept lower controller dock stationary",
                        "Removed outer border box from floating command and options dock",
                        "Concealed rear cards at rest with 2.4s post-exchange reveal and 75s isolated idle peek",
                        "Transient marquee selection notification in Selective Dimension Removal"
                    )
                ),
                ChangelogSection(
                    category = ChangelogCategory.OPTIMISATIONS,
                    items = listOf(
                        "Migrated compileSdk to API 37.2, targetSdk to 37, and remediated all 73 release lint warnings",
                        "Enforced Locale.ROOT formatting across all DXF, SVG, PDF, and dimension serializers"
                    )
                )
            ),
            cleanMarkdownNotes = "Currently installed build of PrecisionCAD Studio.",
            isInstalledVersion = true,
            isLatestStable = true
        )
    }

    /**
     * Applies raw GitHub Releases JSON (used both by live HTTP checks and deterministic unit/Robolectric tests).
     */
    fun applyReleasesJson(jsonArrayString: String, updateTimestamp: Boolean = true) {
        val parsed = CadReleaseParser.parseReleasesJson(
            jsonArrayString = jsonArrayString,
            installedVersionCode = installedVersionCode,
            includePrereleases = false,
            expectedPackageId = repoConfig.expectedPackageId
        )
        val effectiveList = if (parsed.isEmpty()) {
            listOf(synthesizeInstalledReleaseRecord())
        } else if (parsed.none { it.versionCode == installedVersionCode }) {
            val installedRecord = synthesizeInstalledReleaseRecord().copy(
                isLatestStable = parsed.none { !it.isPrerelease && it.apkAsset != null && it.versionCode > installedVersionCode }
            )
            (parsed + installedRecord).sortedByDescending { it.versionCode }
        } else {
            parsed
        }

        val behindCount = CadReleaseParser.computeUpdatesBehindCount(
            releases = effectiveList,
            installedVersionCode = installedVersionCode,
            includePrereleases = false
        )
        _releases.value = effectiveList
        _updatesBehindCount.value = behindCount

        val nowMs = System.currentTimeMillis()
        if (updateTimestamp) {
            prefs.edit {
                putString(KEY_CACHED_RELEASES_JSON, jsonArrayString)
                putLong(KEY_LAST_CHECK_EPOCH_MS, nowMs)
            }
        }

        val latestNewer = effectiveList.firstOrNull {
            !it.isDraft && !it.isPrerelease && it.apkAsset != null && it.versionCode > installedVersionCode
        }
        val currentState = _updateUiState.value
        val isBusy = currentState is AppUpdateUiState.Downloading ||
            currentState is AppUpdateUiState.DownloadedVerified ||
            currentState is AppUpdateUiState.InstallerLaunching ||
            currentState is AppUpdateUiState.AwaitingSystemConfirmation

        if (!isBusy) {
            if (latestNewer != null && behindCount > 0) {
                activeTargetRelease = latestNewer
                _updateUiState.value = AppUpdateUiState.UpdateAvailable(
                    latestRelease = latestNewer,
                    updatesBehindCount = behindCount
                )
            } else {
                _updateUiState.value = AppUpdateUiState.UpToDate(
                    installedVersionName = installedVersionName,
                    installedVersionCode = installedVersionCode,
                    lastCheckedEpochMs = if (updateTimestamp) nowMs else prefs.getLong(KEY_LAST_CHECK_EPOCH_MS, nowMs)
                )
            }
        }
    }

    /**
     * Checks GitHub Releases API for updates.
     * Automatic startup checks are throttled to once every 6 hours; manual checks run immediately.
     */
    fun checkForUpdates(manual: Boolean) {
        val lastCheckMs = prefs.getLong(KEY_LAST_CHECK_EPOCH_MS, 0L)
        val nowMs = System.currentTimeMillis()
        if (!manual && lastCheckMs > 0L && (nowMs - lastCheckMs) < UpdateRepositoryConfig.AUTO_CHECK_THROTTLE_MS) {
            return
        }

        scope.launch {
            _updateUiState.value = AppUpdateUiState.Checking(isManual = manual)
            val result = withContext(Dispatchers.IO) {
                fetchReleasesFromGitHub()
            }
            result.fold(
                onSuccess = { bodyOrNotModified ->
                    if (bodyOrNotModified != null) {
                        applyReleasesJson(bodyOrNotModified, updateTimestamp = true)
                    } else {
                        prefs.edit { putLong(KEY_LAST_CHECK_EPOCH_MS, System.currentTimeMillis()) }
                        loadCachedCatalogueIfPresent()
                    }
                },
                onFailure = {
                    // Gracefully retain cached catalogue so offline drafting is never interrupted
                    loadCachedCatalogueIfPresent()
                }
            )
        }
    }

    private fun fetchReleasesFromGitHub(): Result<String?> = runCatching {
        val url = URL(repoConfig.releasesEndpointUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "PrecisionCAD-Studio-Android/$installedVersionName")
            val cachedEtag = prefs.getString(KEY_CACHED_ETAG, null)
            if (!cachedEtag.isNullOrBlank()) {
                setRequestProperty("If-None-Match", cachedEtag)
            }
        }
        try {
            val code = conn.responseCode
            when (code) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> null
                HttpURLConnection.HTTP_OK -> {
                    val etag = conn.getHeaderField("ETag")
                    if (!etag.isNullOrBlank()) {
                        prefs.edit { putString(KEY_CACHED_ETAG, etag) }
                    }
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
                else -> throw IllegalStateException("GitHub API returned HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Downloads and verifies the selected (or latest available) release APK over HTTPS.
     */
    fun startDownload(targetRelease: GitHubReleaseRecord? = null) {
        val release = targetRelease
            ?: activeTargetRelease
            ?: _releases.value.firstOrNull { !it.isPrerelease && it.apkAsset != null && it.versionCode > installedVersionCode }
            ?: _releases.value.firstOrNull { it.apkAsset != null }

        if (release == null || release.apkAsset == null) {
            _updateUiState.value = AppUpdateUiState.DownloadFailed(
                targetRelease = release,
                reason = "No downloadable APK asset is attached to this release."
            )
            return
        }

        val downloadUrl = release.apkAsset.browserDownloadUrl
        if (!downloadUrl.startsWith("https://github.com/") &&
            !downloadUrl.startsWith("https://objects.githubusercontent.com/") &&
            !downloadUrl.startsWith("https://api.github.com/")
        ) {
            _updateUiState.value = AppUpdateUiState.DownloadFailed(
                targetRelease = release,
                reason = "Blocked untrusted download host: only official HTTPS GitHub release URLs are permitted."
            )
            return
        }

        activeDownloadJob?.cancel()
        activeTargetRelease = release
        activeDownloadJob = scope.launch {
            try {
                _updateUiState.value = AppUpdateUiState.Downloading(
                    targetRelease = release,
                    bytesDownloaded = 0L,
                    totalBytes = release.apkAsset.sizeBytes.coerceAtLeast(release.metadata.apkSizeBytes)
                )

                val verifiedFile = withContext(Dispatchers.IO) {
                    downloadAndVerifyReleaseApk(release)
                }

                val sha256 = withContext(Dispatchers.IO) {
                    CadApkVerifier.computeFileSha256(verifiedFile)
                }
                _updateUiState.value = AppUpdateUiState.DownloadedVerified(
                    targetRelease = release,
                    verifiedApkFile = verifiedFile,
                    computedSha256 = sha256
                )
            } catch (ce: CancellationException) {
                recalculateIdleState()
                throw ce
            } catch (t: Throwable) {
                _updateUiState.value = AppUpdateUiState.DownloadFailed(
                    targetRelease = release,
                    reason = t.message ?: "APK download or verification failed."
                )
            }
        }
    }

    fun cancelActiveDownloadOrReset() {
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        // Clean up any partial `.part` files
        updatesDir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
        recalculateIdleState()
    }

    private fun recalculateIdleState() {
        val behind = _updatesBehindCount.value
        val latest = _releases.value.firstOrNull {
            !it.isDraft && !it.isPrerelease && it.apkAsset != null && it.versionCode > installedVersionCode
        }
        if (latest != null && behind > 0) {
            _updateUiState.value = AppUpdateUiState.UpdateAvailable(
                latestRelease = latest,
                updatesBehindCount = behind
            )
        } else {
            _updateUiState.value = AppUpdateUiState.UpToDate(
                installedVersionName = installedVersionName,
                installedVersionCode = installedVersionCode,
                lastCheckedEpochMs = prefs.getLong(KEY_LAST_CHECK_EPOCH_MS, System.currentTimeMillis())
            )
        }
    }

    private suspend fun downloadAndVerifyReleaseApk(release: GitHubReleaseRecord): File {
        val asset = release.apkAsset ?: error("Missing APK release asset.")
        val safeFileName = release.metadata.apkFileName.replace(Regex("""[^a-zA-Z0-9._-]"""), "_")
        val partFile = File(updatesDir, "$safeFileName.part")
        val finalFile = File(updatesDir, safeFileName)
        if (partFile.exists()) partFile.delete()

        val conn = (URL(asset.browserDownloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "PrecisionCAD-Studio-Android/$installedVersionName")
        }

        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                error("Download failed with HTTP $code")
            }
            val totalLength = conn.contentLengthLong.takeIf { it > 0L }
                ?: asset.sizeBytes.takeIf { it > 0L }
                ?: release.metadata.apkSizeBytes

            conn.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var downloaded = 0L
                    while ( kotlinx.coroutines.currentCoroutineContext().isActive ) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        _updateUiState.value = AppUpdateUiState.Downloading(
                            targetRelease = release,
                            bytesDownloaded = downloaded,
                            totalBytes = totalLength
                        )
                    }
                    output.fd.sync()
                }
            }
        } finally {
            conn.disconnect()
        }

        val installedIsDebuggable = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val installedSigner = CadApkVerifier.getInstalledSigningCertSha256(appContext)
        val verification = CadApkVerifier.verifyApkFile(
            file = partFile,
            expectedMetadata = release.metadata,
            installedVersionCode = installedVersionCode,
            installedIsDebuggable = installedIsDebuggable,
            installedSigningCertSha256 = installedSigner,
            archiveInfoProvider = { file -> getArchivePackageInfo(file) },
            allowSameVersionReinstall = false
        )

        if (!verification.isValid) {
            partFile.delete()
            error(verification.failureReason ?: "APK verification failed.")
        }

        if (finalFile.exists()) finalFile.delete()
        if (!partFile.renameTo(finalFile)) {
            partFile.copyTo(finalFile, overwrite = true)
            partFile.delete()
        }
        // Remove older verified APKs so storage stays tidy
        updatesDir.listFiles()
            ?.filter { it.isFile && it.name != finalFile.name }
            ?.forEach { it.delete() }

        return finalFile
    }

    private fun getArchivePackageInfo(apkFile: File): PackageInfo? {
        val pm = appContext.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val flags = PackageManager.PackageInfoFlags.of(
                PackageManager.GET_SIGNING_CERTIFICATES.toLong()
            )
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun createManageUnknownAppSourcesIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${appContext.packageName}".toUri()
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Phase 12.8 — Launches the Android `PackageInstaller.Session` for a verified APK file.
     * Always requires explicit user confirmation via the system installer sheet.
     */
    fun requestInstallVerifiedApk() {
        val currentState = _updateUiState.value
        val (release, apkFile) = when (currentState) {
            is AppUpdateUiState.DownloadedVerified -> currentState.targetRelease to currentState.verifiedApkFile
            is AppUpdateUiState.InstallSourcePermissionRequired -> currentState.targetRelease to currentState.verifiedApkFile
            else -> return
        }

        if (!apkFile.exists()) {
            _updateUiState.value = AppUpdateUiState.InstallFailed(
                targetRelease = release,
                reason = "Verified APK file is no longer present. Please download again."
            )
            return
        }

        if (!canRequestPackageInstalls()) {
            _updateUiState.value = AppUpdateUiState.InstallSourcePermissionRequired(
                targetRelease = release,
                verifiedApkFile = apkFile
            )
            runCatching { appContext.startActivity(createManageUnknownAppSourcesIntent()) }
            return
        }

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val packageInstaller = appContext.packageManager.packageInstaller
                    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                        setAppPackageName(repoConfig.expectedPackageId)
                        if (apkFile.length() > 0L) {
                            setSize(apkFile.length())
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                        }
                    }

                    val sessionId = packageInstaller.createSession(params)
                    activeTargetRelease = release
                    _updateUiState.value = AppUpdateUiState.InstallerLaunching(
                        targetRelease = release,
                        sessionId = sessionId
                    )

                    packageInstaller.openSession(sessionId).use { session ->
                        FileInputStream(apkFile).use { input ->
                            session.openWrite("base.apk", 0L, apkFile.length()).use { output ->
                                input.copyTo(output, bufferSize = 16 * 1024)
                                session.fsync(output)
                            }
                        }

                        val callbackIntent = Intent(appContext, CadPackageInstallerReceiver::class.java).apply {
                            action = CadPackageInstallerReceiver.ACTION_INSTALL_COMMIT
                            setPackage(appContext.packageName)
                        }
                        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        } else {
                            PendingIntent.FLAG_UPDATE_CURRENT
                        }
                        val pendingIntent = PendingIntent.getBroadcast(
                            appContext,
                            sessionId,
                            callbackIntent,
                            pendingFlags
                        )
                        session.commit(pendingIntent.intentSender)
                    }
                }
            }.onFailure { err ->
                _updateUiState.value = AppUpdateUiState.InstallFailed(
                    targetRelease = release,
                    reason = "Failed to initiate PackageInstaller session: ${err.message}"
                )
            }
        }
    }

    /**
     * Re-evaluates unknown-sources install permission when returning from system Settings.
     */
    fun onHostActivityResumed() {
        val current = _updateUiState.value
        if (current is AppUpdateUiState.InstallSourcePermissionRequired && canRequestPackageInstalls()) {
            _updateUiState.value = AppUpdateUiState.DownloadedVerified(
                targetRelease = current.targetRelease,
                verifiedApkFile = current.verifiedApkFile,
                computedSha256 = current.targetRelease.metadata.apkSha256
            )
        }
    }

    fun onHostResumed(context: Context? = null) = onHostActivityResumed()
    fun downloadAndVerifyRelease(targetRelease: GitHubReleaseRecord? = null) = startDownload(targetRelease)
    fun requestInstallOrExecuteSession(context: Context? = null) = requestInstallVerifiedApk()
    fun cancelOrReset() = cancelActiveDownloadOrReset()

    override fun onUserConfirmationRequired(sessionId: Int, confirmationIntent: Intent) {
        val release = activeTargetRelease ?: _releases.value.firstOrNull() ?: synthesizeInstalledReleaseRecord()
        _updateUiState.value = AppUpdateUiState.AwaitingSystemConfirmation(
            targetRelease = release,
            sessionId = sessionId,
            confirmationIntent = confirmationIntent
        )
    }

    override fun onInstallResult(sessionId: Int, succeeded: Boolean, cancelled: Boolean, message: String) {
        val release = activeTargetRelease ?: _releases.value.firstOrNull() ?: synthesizeInstalledReleaseRecord()
        _updateUiState.value = when {
            succeeded -> AppUpdateUiState.InstallSucceeded(targetRelease = release)
            cancelled -> AppUpdateUiState.InstallCancelled(targetRelease = release, message = message)
            else -> AppUpdateUiState.InstallFailed(targetRelease = release, reason = message)
        }
    }

    companion object {
        private const val PREFS_NAME = "precisioncad_github_updater_prefs"
        private const val KEY_LAST_CHECK_EPOCH_MS = "last_check_epoch_ms"
        private const val KEY_CACHED_ETAG = "cached_releases_etag"
        private const val KEY_CACHED_RELEASES_JSON = "cached_releases_json"
    }
}
