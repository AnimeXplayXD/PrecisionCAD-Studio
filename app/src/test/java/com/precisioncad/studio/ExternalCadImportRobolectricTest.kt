package com.precisioncad.studio

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.precisioncad.studio.cad.io.CadDxfInterchange
import com.precisioncad.studio.cad.io.CadImportErrorKind
import com.precisioncad.studio.cad.io.CadIncomingDocumentRouter
import com.precisioncad.studio.cad.io.CadIncomingImportState
import com.precisioncad.studio.cad.io.CadPcadPersistence
import com.precisioncad.studio.cad.io.DxfDocumentPayload
import com.precisioncad.studio.cad.io.PcadProjectDocument
import com.precisioncad.studio.cad.model.CadCommandId
import com.precisioncad.studio.cad.model.CadEntity
import com.precisioncad.studio.cad.model.CadLayer
import com.precisioncad.studio.cad.model.CadUnit
import com.precisioncad.studio.cad.model.Point2D
import com.precisioncad.studio.ui.CadWorkspaceViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExternalCadImportRobolectricTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

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

    private fun buildDxfBytes(label: String, endX: Double, radius: Double): ByteArray {
        val payload = DxfDocumentPayload(
            storageUnit = CadUnit.MM,
            displayUnit = CadUnit.MM,
            layers = CadLayer.defaultLayers(),
            entities = listOf(
                CadEntity.LineEntity(
                    id = "ext_line_$label",
                    start = Point2D(0.0, 0.0),
                    end = Point2D(endX, 50.0)
                ),
                CadEntity.CircleEntity(
                    id = "ext_circle_$label",
                    center = Point2D(endX / 2.0, 25.0),
                    radius = radius
                )
            )
        )
        return CadDxfInterchange.exportToDxf(payload).toByteArray(Charsets.UTF_8)
    }

    private fun buildPcadBytes(projectName: String, endX: Double): ByteArray {
        val doc = PcadProjectDocument(
            schemaVersion = CadPcadPersistence.CURRENT_SCHEMA_VERSION,
            projectId = "ext_pcad_$projectName",
            projectName = projectName,
            savedAtEpochMs = 1_700_000_000_123L,
            storageUnit = CadUnit.MM,
            displayUnit = CadUnit.MM,
            layers = CadLayer.defaultLayers(),
            entities = listOf(
                CadEntity.LineEntity(
                    id = "ext_pcad_line",
                    start = Point2D(5.0, 5.0),
                    end = Point2D(endX, 5.0)
                )
            )
        )
        return CadPcadPersistence.serializeToPcadJson(doc).toByteArray(Charsets.UTF_8)
    }

    private fun createSharedFileIntent(
        fileName: String,
        bytes: ByteArray,
        mimeType: String?
    ): Triple<File, Uri, Intent> {
        val file = File(exportsDir, fileName)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setClass(context, MainActivity::class.java)
            if (mimeType != null) {
                setDataAndType(uri, mimeType)
            } else {
                data = uri
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Triple(file, uri, intent)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { b -> "%02x".format(Locale.ROOT, b) }

    private fun drainMainAndIdle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
        Thread.sleep(150)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
    }

    @Test
    fun `manifest resolves supported CAD intents including Lumina image vnd dxf and octet-stream, and rejects unsupported 3D MIMEs`() {
        val pm = context.packageManager

        fun matchesSelf(actionIntent: Intent): Boolean {
            val matches = pm.queryIntentActivities(actionIntent, PackageManager.MATCH_DEFAULT_ONLY)
            return matches.any { it.activityInfo.packageName == context.packageName }
        }

        // Lumina primary DXF MIME + FileProvider URI
        assertTrue(
            matchesSelf(
                Intent(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    setDataAndType(
                        Uri.parse("content://com.lumina.gallery.fileprovider/cad/part.dxf"),
                        "image/vnd.dxf"
                    )
                }
            )
        )
        // Standard application/dxf
        assertTrue(
            matchesSelf(
                Intent(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    setDataAndType(
                        Uri.parse("content://com.lumina.gallery.fileprovider/cad/part.dxf"),
                        "application/dxf"
                    )
                }
            )
        )
        // Lumina application/octet-stream fallback
        assertTrue(
            matchesSelf(
                Intent(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    setDataAndType(
                        Uri.parse("content://com.lumina.gallery.fileprovider/cad/archive.bin"),
                        "application/octet-stream"
                    )
                }
            )
        )
        // Extension-only .dxf and .pcad fallback without explicit MIME
        assertTrue(
            matchesSelf(
                Intent(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    data = Uri.parse("content://com.lumina.gallery.fileprovider/cad/bracket.dxf")
                }
            )
        )
        assertTrue(
            matchesSelf(
                Intent(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    data = Uri.parse("content://com.lumina.gallery.fileprovider/cad/housing.pcad")
                }
            )
        )
        // Unimplemented 3D / DWG formats MUST NOT resolve
        assertFalse(
            matchesSelf(
                Intent(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    setDataAndType(
                        Uri.parse("content://com.lumina.gallery.fileprovider/cad/engine.step"),
                        "application/step"
                    )
                }
            )
        )
        assertFalse(
            matchesSelf(
                Intent(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    setDataAndType(
                        Uri.parse("content://com.lumina.gallery.fileprovider/cad/mesh.stl"),
                        "model/stl"
                    )
                }
            )
        )
        assertFalse(
            matchesSelf(
                Intent(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    setDataAndType(
                        Uri.parse("content://com.lumina.gallery.fileprovider/cad/anything"),
                        "image/png"
                    )
                }
            )
        )
    }

    @Test
    fun `cold launch with FileProvider DXF URI imports directly into workspace, avoids home template, and does not re-import on recreate`() {
        val dxfBytes = buildDxfBytes("cold", endX = 180.0, radius = 18.0)
        val expectedHash = sha256(dxfBytes)
        val (_, _, launchIntent) = createSharedFileIntent("flange_plate.dxf", dxfBytes, "image/vnd.dxf")

        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            drainMainAndIdle()

            // Directly in workspace (not stuck on Home Screen)
            composeRule.onNodeWithTag("cad_technical_viewport").assertIsDisplayed()
            assertTrue(composeRule.onAllNodesWithTag("cad_home_screen").fetchSemanticsNodes().isEmpty())

            var firstRevision = 0L
            scenario.onActivity { activity ->
                val vmField = MainActivity::class.java.getDeclaredField("workspaceViewModel\$delegate").apply {
                    isAccessible = true
                }
                @Suppress("UNCHECKED_CAST")
                val vm = (vmField.get(activity) as Lazy<CadWorkspaceViewModel>).value
                val st = vm.uiState.value
                assertFalse(st.isHomeScreenVisible)
                assertTrue(st.hasActiveWorkspaceSession)
                assertEquals("flange_plate", st.currentProjectName)
                assertEquals(2, st.entities.size)
                assertEquals(expectedHash, vm.lastExternalImportSha256)
                assertTrue(st.incomingImportState is CadIncomingImportState.Ready)
                firstRevision = st.documentRevision
                assertTrue(CadIncomingDocumentRouter.isIntentAlreadyHandled(activity.intent))
            }

            // Simulate configuration change / screen rotation
            scenario.recreate()
            drainMainAndIdle()

            scenario.onActivity { activity ->
                val vmField = MainActivity::class.java.getDeclaredField("workspaceViewModel\$delegate").apply {
                    isAccessible = true
                }
                @Suppress("UNCHECKED_CAST")
                val vm = (vmField.get(activity) as Lazy<CadWorkspaceViewModel>).value
                assertEquals(firstRevision, vm.uiState.value.documentRevision)
                assertEquals(expectedHash, vm.lastExternalImportSha256)
            }
        }
    }

    @Test
    fun `warm launch onNewIntent switches cleanly between same-named distinct CAD files and protects unsaved changes`() {
        val firstDxf = buildDxfBytes("v1", endX = 110.0, radius = 10.0)
        val secondDxfSameName = buildDxfBytes("v2", endX = 260.0, radius = 33.0)
        val thirdPcadBytes = buildPcadBytes("MountPlate_V3", endX = 320.0)

        val (_, _, firstIntent) = createSharedFileIntent("assembly.dxf", firstDxf, "image/vnd.dxf")

        ActivityScenario.launch<MainActivity>(firstIntent).use { scenario ->
            drainMainAndIdle()

            // Deliver second same-named file via warm onNewIntent (first is clean -> direct switch)
            val (_, _, secondIntent) = createSharedFileIntent("assembly.dxf", secondDxfSameName, "image/vnd.dxf")
            scenario.onActivity { activity ->
                dispatchNewIntent(activity, secondIntent)
            }
            drainMainAndIdle()

            scenario.onActivity { activity ->
                val vm = extractViewModel(activity)
                assertEquals(sha256(secondDxfSameName), vm.lastExternalImportSha256)
                // Verify entity coordinates match second DXF (not cached first DXF!)
                val line = vm.uiState.value.entities.filterIsInstance<CadEntity.LineEntity>().first()
                assertEquals(260.0, line.end.x, 0.01)

                // Make unsaved active edits in workspace
                vm.activateCommand(CadCommandId.LINE)
                vm.submitCommandOrCoordinateInput("0,0")
                vm.submitCommandOrCoordinateInput("50,50")
                vm.finishCurrentCommand()
                assertTrue(vm.uiState.value.isDocumentDirty)
            }
            drainMainAndIdle()

            // Send third document (.pcad over octet-stream) while active drawing has unsaved edits
            val (thirdSourceFile, _, thirdIntent) = createSharedFileIntent(
                "mount_plate.pcad",
                thirdPcadBytes,
                "application/octet-stream"
            )
            scenario.onActivity { activity ->
                dispatchNewIntent(activity, thirdIntent)
            }
            drainMainAndIdle()

            // Prompt must protect active drawing first!
            composeRule.onNodeWithTag("unsaved_drawing_confirmation_dialog").assertIsDisplayed()

            // Prove staging durability: revoke/delete external source file AFTER staging, before tapping Discard!
            assertTrue(thirdSourceFile.delete())

            composeRule.onNodeWithTag("unsaved_dialog_discard_and_continue").performClick()
            drainMainAndIdle()

            // Verify import STILL completed cleanly from app-private staged copy!
            scenario.onActivity { activity ->
                val vm = extractViewModel(activity)
                assertEquals("MountPlate_V3", vm.uiState.value.currentProjectName)
                assertEquals(sha256(thirdPcadBytes), vm.lastExternalImportSha256)
                val line = vm.uiState.value.entities.filterIsInstance<CadEntity.LineEntity>().first()
                assertEquals(320.0, line.end.x, 0.01)
            }
        }
    }

    @Test
    fun `unsupported STEP over octet-stream displays visible error dialog without clobbering active drawing`() {
        val validDxf = buildDxfBytes("keep_me", endX = 140.0, radius = 15.0)
        val (_, _, initialIntent) = createSharedFileIntent("existing_plan.dxf", validDxf, "image/vnd.dxf")

        ActivityScenario.launch<MainActivity>(initialIntent).use { scenario ->
            drainMainAndIdle()

            val badStep = "ISO-10303-21;\nHEADER;\nDATA;\nEND-ISO-10303-21;".toByteArray(Charsets.UTF_8)
            val (_, _, badIntent) = createSharedFileIntent("solid_body.bin", badStep, "application/octet-stream")
            scenario.onActivity { activity ->
                dispatchNewIntent(activity, badIntent)
            }
            drainMainAndIdle()

            composeRule.onNodeWithTag("external_import_error_dialog").assertIsDisplayed()
            composeRule.onNodeWithTag("external_import_error_title").assertIsDisplayed()
            composeRule.onNodeWithTag("external_import_error_message").assertIsDisplayed()
            composeRule.onNodeWithTag("external_import_dismiss_button").performClick()
            drainMainAndIdle()

            scenario.onActivity { activity ->
                val vm = extractViewModel(activity)
                assertEquals("existing_plan", vm.uiState.value.currentProjectName)
                assertEquals(sha256(validDxf), vm.lastExternalImportSha256)
                assertTrue(vm.uiState.value.incomingImportState is CadIncomingImportState.Idle)
            }
        }
    }

    @Test
    fun `SavedStateHandle restores pending staged external import and surfaces explicit failure when killed mid-read`() {
        val app = context.applicationContext as Application
        val dxfBytes = buildDxfBytes("restore", endX = 210.0, radius = 20.0)
        val stagingDir = File(context.filesDir, CadIncomingDocumentRouter.STAGING_DIR_NAME).apply { mkdirs() }
        val stagedFile = File(stagingDir, "req-restored_recovered.dxf").apply { writeBytes(dxfBytes) }

        val savedState = SavedStateHandle(
            mapOf(
                CadWorkspaceViewModel.KEY_EXT_IN_FLIGHT to false,
                CadWorkspaceViewModel.KEY_EXT_AWAITING_DECISION to true,
                CadWorkspaceViewModel.KEY_EXT_REQUEST_ID to "req-restored",
                CadWorkspaceViewModel.KEY_EXT_URI_STRING to "content://com.lumina.gallery.fileprovider/recovered.dxf",
                CadWorkspaceViewModel.KEY_EXT_DECLARED_MIME to "image/vnd.dxf",
                CadWorkspaceViewModel.KEY_EXT_DISPLAY_NAME to "recovered.dxf",
                CadWorkspaceViewModel.KEY_EXT_STAGED_PATH to stagedFile.absolutePath,
                CadWorkspaceViewModel.KEY_EXT_SHA256 to sha256(dxfBytes),
                CadWorkspaceViewModel.KEY_EXT_BYTES to dxfBytes.size.toLong()
            )
        )

        val restoredVm = CadWorkspaceViewModel(app, savedState)
        drainMainAndIdle()

        val awaiting = restoredVm.uiState.value.incomingImportState as? CadIncomingImportState.AwaitingUnsavedDecision
        assertTrue(awaiting != null)
        assertEquals("recovered.dxf", awaiting!!.stagedDocument.resolvedDisplayName)
        assertNotEquals(null, restoredVm.uiState.value.pendingHomeReplacementAction)

        // Killed mid-read before staging completed -> explicit interrupted failure
        val midReadSavedState = SavedStateHandle(
            mapOf(
                CadWorkspaceViewModel.KEY_EXT_IN_FLIGHT to true,
                CadWorkspaceViewModel.KEY_EXT_AWAITING_DECISION to false,
                CadWorkspaceViewModel.KEY_EXT_REQUEST_ID to "req-midread",
                CadWorkspaceViewModel.KEY_EXT_DISPLAY_NAME to "interrupted.dxf"
            )
        )
        val midReadVm = CadWorkspaceViewModel(app, midReadSavedState)
        val failed = midReadVm.uiState.value.incomingImportState as? CadIncomingImportState.Failed
        assertTrue(failed != null)
        assertEquals(CadImportErrorKind.IO_ERROR, failed!!.errorKind)
    }

    private fun dispatchNewIntent(activity: MainActivity, newIntent: Intent) {
        val method = MainActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java).apply {
            isAccessible = true
        }
        method.invoke(activity, newIntent)
    }

    private fun extractViewModel(activity: MainActivity): CadWorkspaceViewModel {
        val field = MainActivity::class.java.getDeclaredField("workspaceViewModel\$delegate").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        return (field.get(activity) as Lazy<CadWorkspaceViewModel>).value
    }
}
