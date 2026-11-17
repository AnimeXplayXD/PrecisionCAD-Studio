package com.precisioncad.studio

import android.content.ClipData
import android.content.ClipDescription
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.precisioncad.studio.cad.io.CadDxfInterchange
import com.precisioncad.studio.cad.io.CadExternalFormat
import com.precisioncad.studio.cad.io.CadImportErrorKind
import com.precisioncad.studio.cad.io.CadImportStageOutcome
import com.precisioncad.studio.cad.io.CadIncomingDocumentRouter
import com.precisioncad.studio.cad.io.CadPcadPersistence
import com.precisioncad.studio.cad.io.DxfDocumentPayload
import com.precisioncad.studio.cad.io.IncomingDocumentRequest
import com.precisioncad.studio.cad.io.PcadProjectDocument
import com.precisioncad.studio.cad.model.CadEntity
import com.precisioncad.studio.cad.model.CadLayer
import com.precisioncad.studio.cad.model.CadUnit
import com.precisioncad.studio.cad.model.Point2D
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CadIncomingDocumentRouterTest {

    private lateinit var context: Context
    private lateinit var exportsDir: File

    @Before
    fun setup() {
        resetFileProviderCache()
        context = ApplicationProvider.getApplicationContext()
        exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        exportsDir.listFiles()?.forEach { it.delete() }
        File(context.filesDir, CadIncomingDocumentRouter.STAGING_DIR_NAME).listFiles()?.forEach { it.delete() }
        resetFileProviderCache()
    }

    private fun resetFileProviderCache() {
        runCatching {
            val cacheField = FileProvider::class.java.getDeclaredField("sCache").apply {
                isAccessible = true
            }
            (cacheField.get(null) as? MutableMap<*, *>)?.clear()
        }
    }

    private fun sampleDxfString(lineEndX: Double = 120.0): String {
        val payload = DxfDocumentPayload(
            storageUnit = CadUnit.MM,
            displayUnit = CadUnit.MM,
            layers = CadLayer.defaultLayers(),
            entities = listOf(
                CadEntity.LineEntity(
                    id = "line_ext_1",
                    start = Point2D(0.0, 0.0),
                    end = Point2D(lineEndX, 45.0)
                ),
                CadEntity.CircleEntity(
                    id = "circle_ext_1",
                    center = Point2D(60.0, 22.5),
                    radius = 12.0
                )
            )
        )
        return CadDxfInterchange.exportToDxf(payload)
    }

    private fun samplePcadString(projectName: String = "Valve_Housing"): String {
        val doc = PcadProjectDocument(
            schemaVersion = CadPcadPersistence.CURRENT_SCHEMA_VERSION,
            projectId = "pcad_ext_1",
            projectName = projectName,
            savedAtEpochMs = 1_700_000_000_000L,
            storageUnit = CadUnit.MM,
            displayUnit = CadUnit.MM,
            layers = CadLayer.defaultLayers(),
            entities = listOf(
                CadEntity.LineEntity(
                    id = "pcad_line_1",
                    start = Point2D(10.0, 10.0),
                    end = Point2D(90.0, 10.0)
                )
            )
        )
        return CadPcadPersistence.serializeToPcadJson(doc)
    }

    private fun writeExportFileAndGetUri(name: String, bytes: ByteArray): Pair<File, Uri> {
        val file = File(exportsDir, name)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { b -> "%02x".format(Locale.ROOT, b) }

    @Test
    fun `extractRequest supports ACTION_VIEW content URI, ClipData fallback, rejects http scheme, and tracks handled flag`() {
        val contentUri = Uri.parse("content://com.lumina.gallery.fileprovider/shared/gear_plate.dxf")
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "image/vnd.dxf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val req = CadIncomingDocumentRouter.extractRequest(viewIntent)
        assertNotNull(req)
        assertEquals(contentUri, req!!.uri)
        assertEquals("image/vnd.dxf", req.declaredMimeType)
        assertTrue(req.hasReadGrantFlag)
        assertFalse(CadIncomingDocumentRouter.isIntentAlreadyHandled(viewIntent))
        CadIncomingDocumentRouter.markIntentHandled(viewIntent)
        assertTrue(CadIncomingDocumentRouter.isIntentAlreadyHandled(viewIntent))

        // ClipData fallback when intent.data is null
        val clipIntent = Intent(Intent.ACTION_VIEW).apply {
            clipData = ClipData(
                ClipDescription("CAD", arrayOf("application/dxf")),
                ClipData.Item(contentUri)
            )
            type = "application/dxf"
        }
        val clipReq = CadIncomingDocumentRouter.extractRequest(clipIntent)
        assertNotNull(clipReq)
        assertEquals(contentUri, clipReq!!.uri)

        // Unsupported action or http scheme rejected
        val mainIntent = Intent(Intent.ACTION_MAIN).apply { data = contentUri }
        assertNull(CadIncomingDocumentRouter.extractRequest(mainIntent))

        val httpIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/model.dxf"))
        assertNull(CadIncomingDocumentRouter.extractRequest(httpIntent))
    }

    @Test
    fun `sanitizeDisplayName neutralizes path traversal, null bytes, and control characters`() {
        assertEquals(
            "secret_plan.dxf",
            CadIncomingDocumentRouter.sanitizeDisplayName("../../../sdcard/secret_plan.dxf")
        )
        assertEquals(
            "flange_v2.pcad",
            CadIncomingDocumentRouter.sanitizeDisplayName("C:\\Users\\admin\\..\\flange_v2.pcad")
        )
        assertEquals(
            "part_1.dxf",
            CadIncomingDocumentRouter.sanitizeDisplayName("part\u00001.dxf")
        )
        assertEquals(
            "Imported_Drawing.dxf",
            CadIncomingDocumentRouter.sanitizeDisplayName("   ")
        )
    }

    @Test
    fun `detectUnsupportedBinaryOrSignature rejects binary DXF, DWG, STEP, STL, GLB, IGES, and raw binary`() {
        assertNotNull(
            CadIncomingDocumentRouter.detectUnsupportedBinaryOrSignature(
                "AutoCAD Binary DXF\r\n\u001a\u0000".toByteArray(Charsets.US_ASCII)
            )
        )
        assertNotNull(
            CadIncomingDocumentRouter.detectUnsupportedBinaryOrSignature(
                "AC1027\u0000\u0001\u0002".toByteArray(Charsets.US_ASCII)
            )
        )
        assertNotNull(
            CadIncomingDocumentRouter.detectUnsupportedBinaryOrSignature(
                "ISO-10303-21;\nHEADER;\nFILE_DESCRIPTION(('STEP AP214'));".toByteArray(Charsets.UTF_8)
            )
        )
        assertNotNull(
            CadIncomingDocumentRouter.detectUnsupportedBinaryOrSignature(
                "glTF\u0002\u0000\u0000\u0000".toByteArray(Charsets.US_ASCII)
            )
        )
        assertNotNull(
            CadIncomingDocumentRouter.detectUnsupportedBinaryOrSignature(
                "solid assembly\n  facet normal 0 0 1\n".toByteArray(Charsets.UTF_8)
            )
        )
        assertNull(
            CadIncomingDocumentRouter.detectUnsupportedBinaryOrSignature(
                sampleDxfString().toByteArray(Charsets.UTF_8)
            )
        )
    }

    @Test
    fun `stageAndValidateRequest stages ASCII DXF and PCAD via FileProvider with exact SHA-256 and parses geometry`() = runTest {
        val dxfBytes = sampleDxfString(lineEndX = 145.0).toByteArray(Charsets.UTF_8)
        val (_, dxfUri) = writeExportFileAndGetUri("bracket_rev3.dxf", dxfBytes)
        val dxfReq = IncomingDocumentRequest(
            requestId = "req-dxf-1",
            uri = dxfUri,
            declaredMimeType = "image/vnd.dxf",
            clipMimeTypes = emptyList(),
            displayNameHint = "bracket_rev3.dxf",
            hasReadGrantFlag = true,
            intentFingerprint = "fp-dxf-1"
        )

        val dxfOutcome = CadIncomingDocumentRouter.stageAndValidateRequest(context, dxfReq)
        assertTrue(dxfOutcome is CadImportStageOutcome.Success)
        val stagedDxf = (dxfOutcome as CadImportStageOutcome.Success).document
        assertEquals(CadExternalFormat.DXF, stagedDxf.format)
        assertEquals("bracket_rev3.dxf", stagedDxf.resolvedDisplayName)
        assertEquals(sha256Hex(dxfBytes), stagedDxf.sha256Hex)
        assertEquals(2, stagedDxf.entityCount)
        assertTrue(File(stagedDxf.stagedFilePath).exists())

        // PCAD received with generic application/octet-stream fallback
        val pcadBytes = samplePcadString("Turbine_Mount").toByteArray(Charsets.UTF_8)
        val (_, pcadUri) = writeExportFileAndGetUri("Turbine_Mount.pcad", pcadBytes)
        val pcadReq = IncomingDocumentRequest(
            requestId = "req-pcad-1",
            uri = pcadUri,
            declaredMimeType = "application/octet-stream",
            clipMimeTypes = emptyList(),
            displayNameHint = "Turbine_Mount.pcad",
            hasReadGrantFlag = true,
            intentFingerprint = "fp-pcad-1"
        )
        val pcadOutcome = CadIncomingDocumentRouter.stageAndValidateRequest(context, pcadReq)
        assertTrue(pcadOutcome is CadImportStageOutcome.Success)
        val stagedPcad = (pcadOutcome as CadImportStageOutcome.Success).document
        assertEquals(CadExternalFormat.PCAD, stagedPcad.format)
        assertEquals(sha256Hex(pcadBytes), stagedPcad.sha256Hex)
        assertEquals(1, stagedPcad.entityCount)
    }

    @Test
    fun `stageAndValidateRequest rejects empty, corrupt, STEP octet-stream, oversized, low-storage, missing, and SecurityException URIs`() = runTest {
        // 1. Empty 0-byte file
        val (_, emptyUri) = writeExportFileAndGetUri("empty.dxf", ByteArray(0))
        val emptyOutcome = CadIncomingDocumentRouter.stageAndValidateRequest(
            context,
            IncomingDocumentRequest("r1", emptyUri, "application/dxf", emptyList(), "empty.dxf", true, "fp1")
        )
        assertEquals(CadImportErrorKind.EMPTY_FILE, (emptyOutcome as CadImportStageOutcome.Failure).errorKind)

        // 2. Corrupt DXF content
        val (_, corruptUri) = writeExportFileAndGetUri("corrupt.dxf", "not a dxf file at all".toByteArray())
        val corruptOutcome = CadIncomingDocumentRouter.stageAndValidateRequest(
            context,
            IncomingDocumentRequest("r2", corruptUri, "image/vnd.dxf", emptyList(), "corrupt.dxf", true, "fp2")
        )
        assertEquals(CadImportErrorKind.MALFORMED_CONTENT, (corruptOutcome as CadImportStageOutcome.Failure).errorKind)

        // 3. STEP CAD sent as application/octet-stream
        val (_, stepUri) = writeExportFileAndGetUri(
            "cad_part.bin",
            "ISO-10303-21;\nHEADER;\nDATA;\nEND-ISO-10303-21;".toByteArray()
        )
        val stepOutcome = CadIncomingDocumentRouter.stageAndValidateRequest(
            context,
            IncomingDocumentRequest("r3", stepUri, "application/octet-stream", emptyList(), "cad_part.bin", true, "fp3")
        )
        assertEquals(CadImportErrorKind.UNSUPPORTED_FORMAT, (stepOutcome as CadImportStageOutcome.Failure).errorKind)

        // 4. Missing file on disk
        val (deletedFile, missingUri) = writeExportFileAndGetUri("gone.dxf", sampleDxfString().toByteArray())
        assertTrue(deletedFile.delete())
        val missingOutcome = CadIncomingDocumentRouter.stageAndValidateRequest(
            context,
            IncomingDocumentRequest("r4", missingUri, "image/vnd.dxf", emptyList(), "gone.dxf", true, "fp4")
        )
        assertEquals(CadImportErrorKind.FILE_NOT_FOUND, (missingOutcome as CadImportStageOutcome.Failure).errorKind)

        // 5. Insufficient private storage
        val (_, validUri) = writeExportFileAndGetUri("valid.dxf", sampleDxfString().toByteArray())
        val storageOutcome = CadIncomingDocumentRouter.stageAndValidateRequest(
            context = context,
            request = IncomingDocumentRequest("r5", validUri, "image/vnd.dxf", emptyList(), "valid.dxf", true, "fp5"),
            availableBytesProvider = { 512L }
        )
        assertEquals(CadImportErrorKind.INSUFFICIENT_STORAGE, (storageOutcome as CadImportStageOutcome.Failure).errorKind)
        assertTrue(storageOutcome.retryable)

        // 6. Permission denied (SecurityException from caller ContentProvider)
        Robolectric.buildContentProvider(DenyingTestProvider::class.java)
            .create("com.test.denied.provider")
        val deniedUri = Uri.parse("content://com.test.denied.provider/locked.dxf")
        val deniedOutcome = CadIncomingDocumentRouter.stageAndValidateRequest(
            context,
            IncomingDocumentRequest("r6", deniedUri, "image/vnd.dxf", emptyList(), "locked.dxf", false, "fp6")
        )
        assertEquals(CadImportErrorKind.PERMISSION_DENIED, (deniedOutcome as CadImportStageOutcome.Failure).errorKind)
    }

    class DenyingTestProvider : ContentProvider() {
        override fun onCreate(): Boolean = true
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = throw SecurityException("Revoked read grant for $uri")
        override fun getType(uri: Uri): String? = "image/vnd.dxf"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
