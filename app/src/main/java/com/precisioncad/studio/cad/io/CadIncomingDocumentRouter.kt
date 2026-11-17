package com.precisioncad.studio.cad.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.coroutineContext

enum class CadExternalFormat {
    DXF,
    PCAD
}

enum class CadImportErrorKind {
    INVALID_INTENT,
    PERMISSION_DENIED,
    FILE_NOT_FOUND,
    EMPTY_FILE,
    SIZE_LIMIT_EXCEEDED,
    INSUFFICIENT_STORAGE,
    UNSUPPORTED_FORMAT,
    MALFORMED_CONTENT,
    CANCELLED,
    IO_ERROR
}

data class IncomingDocumentRequest(
    val requestId: String,
    val uri: Uri,
    val declaredMimeType: String?,
    val clipMimeTypes: List<String>,
    val displayNameHint: String?,
    val hasReadGrantFlag: Boolean,
    val intentFingerprint: String,
    val receivedAtEpochMs: Long = System.currentTimeMillis()
)

data class StagedExternalDocument(
    val requestId: String,
    val sourceUriString: String,
    val resolvedDisplayName: String,
    val projectBaseName: String,
    val format: CadExternalFormat,
    val declaredMimeType: String?,
    val byteCount: Long,
    val sha256Hex: String,
    val stagedFilePath: String,
    val parsedDxf: DxfDocumentPayload? = null,
    val parsedPcad: PcadProjectDocument? = null
) {
    val entityCount: Int
        get() = parsedPcad?.entities?.size ?: parsedDxf?.entities?.size ?: 0
}

sealed interface CadIncomingImportState {
    data object Idle : CadIncomingImportState

    data class Reading(
        val requestId: String,
        val displayName: String,
        val bytesRead: Long,
        val totalBytes: Long?
    ) : CadIncomingImportState

    data class Importing(
        val requestId: String,
        val displayName: String,
        val format: CadExternalFormat
    ) : CadIncomingImportState

    data class AwaitingUnsavedDecision(
        val stagedDocument: StagedExternalDocument
    ) : CadIncomingImportState

    data class Ready(
        val requestId: String,
        val displayName: String,
        val format: CadExternalFormat,
        val entityCount: Int,
        val sha256Hex: String,
        val byteCount: Long
    ) : CadIncomingImportState

    data class Cancelled(
        val requestId: String,
        val displayName: String
    ) : CadIncomingImportState

    data class Failed(
        val requestId: String,
        val displayName: String,
        val errorKind: CadImportErrorKind,
        val title: String,
        val message: String,
        val retryable: Boolean,
        val lastRequest: IncomingDocumentRequest? = null
    ) : CadIncomingImportState
}

sealed interface CadImportStageOutcome {
    data class Success(val document: StagedExternalDocument) : CadImportStageOutcome
    data class Failure(
        val errorKind: CadImportErrorKind,
        val title: String,
        val message: String,
        val retryable: Boolean,
        val displayName: String
    ) : CadImportStageOutcome
}

/**
 * Validates, streams, sanitizes, and parses external `ACTION_VIEW` CAD document intents
 * (`content://` / `file://`) into durable app-private staging storage without relying on
 * persistent caller FileProvider grants or broad storage permissions.
 */
object CadIncomingDocumentRouter {

    const val EXTRA_IMPORT_HANDLED = "com.precisioncad.studio.extra.EXTERNAL_IMPORT_HANDLED"
    const val STAGING_DIR_NAME = "incoming_imports"
    const val MAX_IMPORT_BYTES: Long = 25L * 1024L * 1024L
    const val MIN_FREE_STORAGE_MARGIN_BYTES: Long = 4L * 1024L * 1024L
    private const val STREAM_BUFFER_SIZE = 16 * 1024

    val SUPPORTED_DXF_MIMES: Set<String> = setOf(
        "image/vnd.dxf",
        "application/dxf",
        "application/x-dxf",
        "drawing/x-dxf",
        "image/x-dxf",
        "application/x-autocad"
    )

    val SUPPORTED_PCAD_MIMES: Set<String> = setOf(
        "application/vnd.precisioncad.pcad",
        "application/x-pcad"
    )

    val FALLBACK_OCTET_STREAM_MIME = "application/octet-stream"

    private val UNSUPPORTED_KNOWN_MIMES: Map<String, String> = mapOf(
        "application/step" to "STEP 3D CAD (.step/.stp)",
        "model/step" to "STEP 3D CAD (.step/.stp)",
        "model/iges" to "IGES CAD (.iges/.igs)",
        "image/vnd.dwg" to "AutoCAD binary DWG (.dwg)",
        "application/acad" to "AutoCAD binary DWG (.dwg)",
        "model/stl" to "STL 3D mesh (.stl)",
        "model/obj" to "Wavefront OBJ 3D mesh (.obj)",
        "model/gltf-binary" to "glTF binary 3D model (.glb)",
        "model/gltf+json" to "glTF 3D model (.gltf)"
    )

    private val UNSUPPORTED_KNOWN_EXTENSIONS: Map<String, String> = mapOf(
        "step" to "STEP 3D CAD (.step/.stp)",
        "stp" to "STEP 3D CAD (.step/.stp)",
        "iges" to "IGES CAD (.iges/.igs)",
        "igs" to "IGES CAD (.iges/.igs)",
        "dwg" to "AutoCAD binary DWG (.dwg)",
        "dwf" to "Autodesk DWF (.dwf)",
        "stl" to "STL 3D mesh (.stl)",
        "obj" to "Wavefront OBJ 3D mesh (.obj)",
        "glb" to "glTF binary 3D model (.glb)",
        "gltf" to "glTF 3D model (.gltf)"
    )

    fun isIntentAlreadyHandled(intent: Intent?): Boolean {
        if (intent == null) return true
        return intent.getBooleanExtra(EXTRA_IMPORT_HANDLED, false)
    }

    fun markIntentHandled(intent: Intent?) {
        intent?.putExtra(EXTRA_IMPORT_HANDLED, true)
    }

    fun extractRequest(intent: Intent?): IncomingDocumentRequest? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_VIEW) return null

        val primaryUri = intent.data ?: run {
            val clip = intent.clipData
            if (clip != null && clip.itemCount > 0) clip.getItemAt(0)?.uri else null
        } ?: return null

        val scheme = primaryUri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "content" && scheme != "file") return null

        val declaredMime = intent.type?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        val clipMimeTypes = buildList {
            val desc = intent.clipData?.description
            if (desc != null) {
                for (i in 0 until desc.mimeTypeCount) {
                    desc.getMimeType(i)?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }?.let { add(it) }
                }
            }
        }
        val rawSegment = primaryUri.lastPathSegment?.takeIf { it.isNotBlank() }
        val hint = rawSegment?.let { sanitizeDisplayName(it, allowEmptyExtension = true) }
        val hasReadGrant = (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
        val fingerprint = "${intent.action}|$primaryUri|${declaredMime.orEmpty()}|${clipMimeTypes.joinToString(",")}"

        return IncomingDocumentRequest(
            requestId = UUID.randomUUID().toString(),
            uri = primaryUri,
            declaredMimeType = declaredMime,
            clipMimeTypes = clipMimeTypes,
            displayNameHint = hint,
            hasReadGrantFlag = hasReadGrant,
            intentFingerprint = fingerprint
        )
    }

    /**
     * Sanitizes an untrusted display name or URI path segment to eliminate path traversal (`..`, `/`, `\`),
     * NUL/control characters, and oversized names.
     */
    fun sanitizeDisplayName(rawName: String?, allowEmptyExtension: Boolean = false): String {
        val stripped = (rawName ?: "")
            .replace('\u0000', '_')
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace("..", "_")
            .replace(Regex("[\\p{Cntrl}:*?\"<>|]"), "_")
            .trim()
            .trim('.')
        val clamped = if (stripped.length > 96) stripped.takeLast(96) else stripped
        if (clamped.isBlank()) {
            return if (allowEmptyExtension) "Imported_Drawing" else "Imported_Drawing.dxf"
        }
        return clamped
    }

    internal fun extractExtensionLower(fileName: String): String {
        val clean = sanitizeDisplayName(fileName, allowEmptyExtension = true)
        val dotIdx = clean.lastIndexOf('.')
        if (dotIdx <= 0 || dotIdx >= clean.length - 1) return ""
        return clean.substring(dotIdx + 1).lowercase(Locale.ROOT)
    }

    fun isDeclaredMimeSupportedOrFallback(mime: String?): Boolean {
        if (mime.isNullOrBlank()) return true
        val norm = mime.trim().lowercase(Locale.ROOT)
        return norm in SUPPORTED_DXF_MIMES ||
            norm in SUPPORTED_PCAD_MIMES ||
            norm == FALLBACK_OCTET_STREAM_MIME
    }

    internal fun detectUnsupportedBinaryOrSignature(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val headLen = minOf(bytes.size, 512)
        val headAscii = String(bytes, 0, headLen, Charsets.US_ASCII)
        val headTrimmed = headAscii.trimStart()

        if (headAscii.startsWith("AutoCAD Binary DXF")) {
            return "Binary DXF files are not supported. Export the drawing as ASCII DXF (AC1015)."
        }
        if (Regex("^AC10\\d\\d").containsMatchIn(headTrimmed)) {
            return "AutoCAD binary DWG files are not supported. Export the drawing as ASCII DXF (AC1015)."
        }
        if (headTrimmed.startsWith("ISO-10303-21;")) {
            return "STEP 3D CAD files (.step/.stp) are not supported. PrecisionCAD Studio supports 2D ASCII DXF and .pcad files."
        }
        if (bytes.size >= 4 &&
            bytes[0] == 'g'.code.toByte() &&
            bytes[1] == 'l'.code.toByte() &&
            bytes[2] == 'T'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()
        ) {
            return "glTF binary 3D models (.glb) are not supported. PrecisionCAD Studio supports 2D ASCII DXF and .pcad files."
        }
        if (headTrimmed.startsWith("solid ") && headAscii.contains("facet normal", ignoreCase = true)) {
            return "STL 3D mesh files (.stl) are not supported. PrecisionCAD Studio supports 2D ASCII DXF and .pcad files."
        }
        if (headAscii.contains("S      1") && headAscii.contains("G      1") && headAscii.lines().any { it.length >= 73 && (it[72] == 'S' || it[72] == 'G') }) {
            return "IGES CAD files (.iges/.igs) are not supported. PrecisionCAD Studio supports 2D ASCII DXF and .pcad files."
        }
        val sampleLimit = minOf(bytes.size, 4096)
        for (i in 0 until sampleLimit) {
            if (bytes[i] == 0.toByte()) {
                return "Binary CAD or unknown binary data cannot be imported. PrecisionCAD Studio supports 2D ASCII DXF and .pcad files."
            }
        }
        return null
    }

    internal fun looksLikePcadJson(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith("{") &&
            (trimmed.contains("\"PrecisionCAD_Native_PCAD\"") || trimmed.contains("\"schemaVersion\""))
    }

    internal fun looksLikeAsciiDxf(text: String): Boolean {
        val upper = text.uppercase(Locale.ROOT)
        return upper.contains("SECTION") && upper.contains("ENTITIES")
    }

    suspend fun stageAndValidateRequest(
        context: Context,
        request: IncomingDocumentRequest,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit = { _, _ -> },
        availableBytesProvider: (File) -> Long = { dir ->
            runCatching {
                val stat = StatFs(dir.absolutePath)
                if (stat.totalBytes > 0L) stat.availableBytes else Long.MAX_VALUE
            }.getOrDefault(Long.MAX_VALUE)
        }
    ): CadImportStageOutcome = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var queriedName: String? = null
        var queriedSize: Long? = null

        val metaResult = runCatching {
            resolver.query(
                request.uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        queriedName = cursor.getString(nameIdx)
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        val sz = cursor.getLong(sizeIdx)
                        if (sz >= 0L) queriedSize = sz
                    }
                }
            }
        }
        val metaThrowable = metaResult.exceptionOrNull()
        if (metaThrowable is SecurityException) {
            return@withContext CadImportStageOutcome.Failure(
                errorKind = CadImportErrorKind.PERMISSION_DENIED,
                title = "FILE ACCESS DENIED",
                message = "Read permission for this shared URI is missing or has expired. Re-open the file from the source app.",
                retryable = false,
                displayName = sanitizeDisplayName(request.displayNameHint, allowEmptyExtension = true)
            )
        }

        val resolvedDisplayName = sanitizeDisplayName(
            queriedName ?: request.displayNameHint,
            allowEmptyExtension = true
        )

        if (queriedSize != null && queriedSize!! > MAX_IMPORT_BYTES) {
            return@withContext CadImportStageOutcome.Failure(
                errorKind = CadImportErrorKind.SIZE_LIMIT_EXCEEDED,
                title = "FILE TOO LARGE",
                message = "'$resolvedDisplayName' exceeds the ${MAX_IMPORT_BYTES / (1024 * 1024)} MiB import limit.",
                retryable = false,
                displayName = resolvedDisplayName
            )
        }

        val stagingDir = File(context.filesDir, STAGING_DIR_NAME).apply { mkdirs() }
        val availableBytes = availableBytesProvider(stagingDir)
        val requiredHeadroom = (queriedSize ?: 0L) + MIN_FREE_STORAGE_MARGIN_BYTES
        if (availableBytes < requiredHeadroom) {
            return@withContext CadImportStageOutcome.Failure(
                errorKind = CadImportErrorKind.INSUFFICIENT_STORAGE,
                title = "INSUFFICIENT STORAGE",
                message = "Not enough private storage space is available to stage '$resolvedDisplayName'. Free device storage and retry.",
                retryable = true,
                displayName = resolvedDisplayName
            )
        }

        val declaredExt = extractExtensionLower(resolvedDisplayName)
        val declaredMime = request.declaredMimeType?.lowercase(Locale.ROOT)
        if (declaredMime != null && declaredMime in UNSUPPORTED_KNOWN_MIMES) {
            val label = UNSUPPORTED_KNOWN_MIMES[declaredMime] ?: declaredMime
            return@withContext CadImportStageOutcome.Failure(
                errorKind = CadImportErrorKind.UNSUPPORTED_FORMAT,
                title = "UNSUPPORTED CAD FORMAT",
                message = "'$resolvedDisplayName' ($label) is not supported. PrecisionCAD Studio currently imports 2D ASCII DXF (.dxf) and native .pcad files.",
                retryable = false,
                displayName = resolvedDisplayName
            )
        }
        if (declaredExt.isNotEmpty() && declaredExt in UNSUPPORTED_KNOWN_EXTENSIONS) {
            val label = UNSUPPORTED_KNOWN_EXTENSIONS[declaredExt] ?: ".$declaredExt"
            return@withContext CadImportStageOutcome.Failure(
                errorKind = CadImportErrorKind.UNSUPPORTED_FORMAT,
                title = "UNSUPPORTED CAD FORMAT",
                message = "'$resolvedDisplayName' ($label) is not supported. PrecisionCAD Studio currently imports 2D ASCII DXF (.dxf) and native .pcad files.",
                retryable = false,
                displayName = resolvedDisplayName
            )
        }

        val stagedFile = File(stagingDir, "${request.requestId}_$resolvedDisplayName")
        val digest = MessageDigest.getInstance("SHA-256")
        var totalRead = 0L

        try {
            val inputStream = resolver.openInputStream(request.uri)
                ?: return@withContext CadImportStageOutcome.Failure(
                    errorKind = CadImportErrorKind.FILE_NOT_FOUND,
                    title = "UNABLE TO OPEN FILE",
                    message = "Could not open an input stream for '$resolvedDisplayName'. The shared file may have been removed.",
                    retryable = false,
                    displayName = resolvedDisplayName
                )

            inputStream.use { stream ->
                FileOutputStream(stagedFile).use { fos ->
                    val buffer = ByteArray(STREAM_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = stream.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        totalRead += read
                        if (totalRead > MAX_IMPORT_BYTES) {
                            throw SizeLimitExceededException(
                                "'$resolvedDisplayName' exceeds the ${MAX_IMPORT_BYTES / (1024 * 1024)} MiB import limit."
                            )
                        }
                        if (totalRead > availableBytes) {
                            throw InsufficientStorageException(
                                "Available storage exhausted while copying '$resolvedDisplayName'."
                            )
                        }
                        fos.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        onProgress(totalRead, queriedSize)
                    }
                    fos.fd.sync()
                }
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            stagedFile.delete()
            throw ce
        } catch (se: SecurityException) {
            stagedFile.delete()
            return@withContext CadImportStageOutcome.Failure(
                errorKind = CadImportErrorKind.PERMISSION_DENIED,
                title = "FILE ACCESS DENIED",
                message = "Read permission for '$resolvedDisplayName' is missing or expired (${se.message ?: "SecurityException"}). Re-open the file from the source app.",
                retryable = false,
                displayName = resolvedDisplayName
            )
        } catch (fnf: FileNotFoundException) {
            stagedFile.delete()
            return@withContext CadImportStageOutcome.Failure(
                errorKind = CadImportErrorKind.FILE_NOT_FOUND,
                title = "FILE NOT FOUND",
                message = "'$resolvedDisplayName' could not be found or its temporary share expired (${fnf.message ?: "missing file"}).",
                retryable = false,
                displayName = resolvedDisplayName
            )
        } catch (sle: SizeLimitExceededException) {
            stagedFile.delete()
            return@withContext CadImportStageOutcome.Failure(
                errorKind = CadImportErrorKind.SIZE_LIMIT_EXCEEDED,
                title = "FILE TOO LARGE",
                message = sle.message ?: "External CAD file exceeded the maximum allowed size.",
                retryable = false,
                displayName = resolvedDisplayName
            )
        } catch (ise: InsufficientStorageException) {
            stagedFile.delete()
            return@withContext CadImportStageOutcome.Failure(
                errorKind = CadImportErrorKind.INSUFFICIENT_STORAGE,
                title = "INSUFFICIENT STORAGE",
                message = ise.message ?: "Not enough free space to stage external CAD file.",
                retryable = true,
                displayName = resolvedDisplayName
            )
        } catch (io: IOException) {
            stagedFile.delete()
            return@withContext CadImportStageOutcome.Failure(
                errorKind = CadImportErrorKind.IO_ERROR,
                title = "FILE READ ERROR",
                message = "Failed to read '$resolvedDisplayName': ${io.message ?: "I/O error"}.",
                retryable = true,
                displayName = resolvedDisplayName
            )
        }

        val sha256Hex = digest.digest().joinToString("") { b -> "%02x".format(Locale.ROOT, b) }
        val outcome = parseValidatedStagedFile(
            stagedFile = stagedFile,
            requestId = request.requestId,
            sourceUriString = request.uri.toString(),
            resolvedDisplayName = resolvedDisplayName,
            declaredMimeType = request.declaredMimeType,
            byteCount = totalRead,
            sha256Hex = sha256Hex
        )
        if (outcome is CadImportStageOutcome.Failure) {
            stagedFile.delete()
        }
        return@withContext outcome
    }

    suspend fun parseValidatedStagedFile(
        stagedFile: File,
        requestId: String,
        sourceUriString: String,
        resolvedDisplayName: String,
        declaredMimeType: String?,
        byteCount: Long,
        sha256Hex: String
    ): CadImportStageOutcome = withContext(Dispatchers.IO) {
        if (!stagedFile.exists() || byteCount <= 0L || stagedFile.length() == 0L) {
            return@withContext CadImportStageOutcome.Failure(
                errorKind = CadImportErrorKind.EMPTY_FILE,
                title = "EMPTY CAD FILE",
                message = "'$resolvedDisplayName' is empty and cannot be imported.",
                retryable = false,
                displayName = resolvedDisplayName
            )
        }

        val rawBytes = try {
            stagedFile.readBytes()
        } catch (io: IOException) {
            return@withContext CadImportStageOutcome.Failure(
                errorKind = CadImportErrorKind.IO_ERROR,
                title = "STAGED READ ERROR",
                message = "Could not read staged copy of '$resolvedDisplayName': ${io.message ?: "I/O error"}.",
                retryable = true,
                displayName = resolvedDisplayName
            )
        }

        if (rawBytes.isEmpty()) {
            return@withContext CadImportStageOutcome.Failure(
                errorKind = CadImportErrorKind.EMPTY_FILE,
                title = "EMPTY CAD FILE",
                message = "'$resolvedDisplayName' contains 0 bytes.",
                retryable = false,
                displayName = resolvedDisplayName
            )
        }

        detectUnsupportedBinaryOrSignature(rawBytes)?.let { unsupportedReason ->
            return@withContext CadImportStageOutcome.Failure(
                errorKind = CadImportErrorKind.UNSUPPORTED_FORMAT,
                title = "UNSUPPORTED CAD FORMAT",
                message = "'$resolvedDisplayName': $unsupportedReason",
                retryable = false,
                displayName = resolvedDisplayName
            )
        }

        val text = rawBytes.toString(Charsets.UTF_8)
        if (text.isBlank()) {
            return@withContext CadImportStageOutcome.Failure(
                errorKind = CadImportErrorKind.EMPTY_FILE,
                title = "EMPTY CAD FILE",
                message = "'$resolvedDisplayName' contains no readable text or CAD entities.",
                retryable = false,
                displayName = resolvedDisplayName
            )
        }

        val ext = extractExtensionLower(resolvedDisplayName)
        val mime = declaredMimeType?.trim()?.lowercase(Locale.ROOT)
        val projectBase = CadPcadPersistence.canonicalBasename(resolvedDisplayName)

        // 1. Check Native .pcad JSON format
        if (ext == "pcad" || mime in SUPPORTED_PCAD_MIMES || looksLikePcadJson(text)) {
            val doc = CadPcadPersistence.deserializeFromPcadJson(text)
            if (doc != null) {
                val docWithTitle = if (doc.projectName == "Untitled_Drawing" && projectBase != "Untitled_Drawing") {
                    doc.withSynchronizedProjectName(projectBase)
                } else {
                    doc
                }
                return@withContext CadImportStageOutcome.Success(
                    StagedExternalDocument(
                        requestId = requestId,
                        sourceUriString = sourceUriString,
                        resolvedDisplayName = resolvedDisplayName,
                        projectBaseName = docWithTitle.canonicalBasename,
                        format = CadExternalFormat.PCAD,
                        declaredMimeType = declaredMimeType,
                        byteCount = rawBytes.size.toLong(),
                        sha256Hex = sha256Hex,
                        stagedFilePath = stagedFile.absolutePath,
                        parsedPcad = docWithTitle
                    )
                )
            }
            if (ext == "pcad" || mime in SUPPORTED_PCAD_MIMES) {
                return@withContext CadImportStageOutcome.Failure(
                    errorKind = CadImportErrorKind.MALFORMED_CONTENT,
                    title = "CORRUPT .PCAD PROJECT",
                    message = "'$resolvedDisplayName' is not a valid PrecisionCAD .pcad project file.",
                    retryable = false,
                    displayName = resolvedDisplayName
                )
            }
        }

        // 2. Check glTF JSON or OBJ text signatures passed via octet-stream / unknown extension
        val trimmed = text.trimStart()
        if (trimmed.startsWith("{") && trimmed.contains("\"asset\"") &&
            (trimmed.contains("\"scenes\"") || trimmed.contains("\"meshes\"") || trimmed.contains("\"nodes\""))
        ) {
            return@withContext CadImportStageOutcome.Failure(
                errorKind = CadImportErrorKind.UNSUPPORTED_FORMAT,
                title = "UNSUPPORTED CAD FORMAT",
                message = "'$resolvedDisplayName' appears to be a glTF 3D model, which is not supported. PrecisionCAD Studio imports 2D ASCII DXF and .pcad files.",
                retryable = false,
                displayName = resolvedDisplayName
            )
        }

        // 3. Validate and parse ASCII DXF
        if (!looksLikeAsciiDxf(text)) {
            val isExplicitDxf = ext == "dxf" || mime in SUPPORTED_DXF_MIMES
            return@withContext CadImportStageOutcome.Failure(
                errorKind = if (isExplicitDxf) CadImportErrorKind.MALFORMED_CONTENT else CadImportErrorKind.UNSUPPORTED_FORMAT,
                title = if (isExplicitDxf) "CORRUPT DXF FILE" else "UNSUPPORTED CAD FORMAT",
                message = if (isExplicitDxf) {
                    "'$resolvedDisplayName' is malformed and does not contain valid ASCII DXF SECTION/ENTITIES blocks."
                } else {
                    "'$resolvedDisplayName' is not a recognized ASCII DXF or .pcad CAD file."
                },
                retryable = false,
                displayName = resolvedDisplayName
            )
        }

        val payload = runCatching { CadDxfInterchange.importFromDxf(text) }.getOrNull()
        if (payload == null || payload.entities.isEmpty()) {
            return@withContext CadImportStageOutcome.Failure(
                errorKind = CadImportErrorKind.MALFORMED_CONTENT,
                title = "NO IMPORTABLE GEOMETRY",
                message = "'$resolvedDisplayName' did not yield any supported 2D CAD entities.",
                retryable = false,
                displayName = resolvedDisplayName
            )
        }

        return@withContext CadImportStageOutcome.Success(
            StagedExternalDocument(
                requestId = requestId,
                sourceUriString = sourceUriString,
                resolvedDisplayName = resolvedDisplayName,
                projectBaseName = projectBase,
                format = CadExternalFormat.DXF,
                declaredMimeType = declaredMimeType,
                byteCount = rawBytes.size.toLong(),
                sha256Hex = sha256Hex,
                stagedFilePath = stagedFile.absolutePath,
                parsedDxf = payload
            )
        )
    }

    fun cleanupStagedFile(stagedFilePath: String?) {
        if (stagedFilePath.isNullOrBlank()) return
        runCatching {
            val file = File(stagedFilePath)
            if (file.exists() && file.parentFile?.name == STAGING_DIR_NAME) {
                file.delete()
            }
        }
    }

    private class SizeLimitExceededException(message: String) : IOException(message)
    private class InsufficientStorageException(message: String) : IOException(message)
}
