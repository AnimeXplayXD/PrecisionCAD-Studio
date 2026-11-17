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
import com.precisioncad.studio.cad.update.ReleaseFeedFetcher
import com.precisioncad.studio.testsupport.ExternalScenarioOwnerRule
import com.precisioncad.studio.ui.CadWorkspaceViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExternalCadImportRobolectricTest {

    private val scenarioOwner = ExternalScenarioOwnerRule()
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(scenarioOwner).around(composeRule)

    private val context: Context
        get() = scenarioOwner.context

    private val exportsDir: File
        get() = scenarioOwner.exportsDir

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

    private fun pumpMainAndCompose() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
    }

    private fun waitForBoundedCondition(
        timeoutMs: Long = 10_000L,
        condition: () -> Boolean
    ) {
        composeRule.waitUntil(timeoutMillis = timeoutMs) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
        pumpMainAndCompose()
    }

    private fun waitForTagDisplayed(tag: String, timeoutMs: Long = 10_000L) {
        waitForBoundedCondition(timeoutMs) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun waitForScenarioViewModelCondition(
        scenario: ActivityScenario<MainActivity>,
        timeoutMs: Long = 10_000L,
        predicate: (CadWorkspaceViewModel) -> Boolean
    ) {
        waitForBoundedCondition(timeoutMs) {
            var matched = false
            scenario.onActivity { activity ->
                matched = predicate(extractViewModel(activity))
            }
            matched
        }
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

        val scenario = scenarioOwner.launch<MainActivity>(launchIntent)
        waitForScenarioViewModelCondition(scenario) { vm ->
            vm.uiState.value.incomingImportState is CadIncomingImportState.Ready &&
                vm.lastExternalImportSha256 == expectedHash &&
                !vm.uiState.value.isHomeScreenVisible
        }

        // Directly in workspace (not stuck on Home Screen)
        composeRule.onNodeWithTag("cad_technical_viewport").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("cad_home_screen").fetchSemanticsNodes().isEmpty())

        var firstRevision = 0L
        scenario.onActivity { activity ->
            val vm = extractViewModel(activity)
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
        waitForTagDisplayed("cad_technical_viewport")

        scenario.onActivity { activity ->
            val vm = extractViewModel(activity)
            assertEquals(firstRevision, vm.uiState.value.documentRevision)
            assertEquals(expectedHash, vm.lastExternalImportSha256)
        }
    }

    @Test
    fun `warm launch onNewIntent switches cleanly between same-named distinct CAD files and protects unsaved changes`() {
        val firstDxf = buildDxfBytes("v1", endX = 110.0, radius = 10.0)
        val secondDxfSameName = buildDxfBytes("v2", endX = 260.0, radius = 33.0)
        val thirdPcadBytes = buildPcadBytes("MountPlate_V3", endX = 320.0)
        val firstHash = sha256(firstDxf)
        val secondHash = sha256(secondDxfSameName)
        val thirdHash = sha256(thirdPcadBytes)

        val (_, _, firstIntent) = createSharedFileIntent("assembly.dxf", firstDxf, "image/vnd.dxf")

        val scenario = scenarioOwner.launch<MainActivity>(firstIntent)
        waitForScenarioViewModelCondition(scenario) { vm ->
            vm.uiState.value.incomingImportState is CadIncomingImportState.Ready &&
                vm.lastExternalImportSha256 == firstHash
        }

        // Deliver second same-named file via warm onNewIntent (first is clean -> direct switch)
        val (_, _, secondIntent) = createSharedFileIntent("assembly.dxf", secondDxfSameName, "image/vnd.dxf")
        scenario.onActivity { activity ->
            dispatchNewIntent(activity, secondIntent)
        }
        waitForScenarioViewModelCondition(scenario) { vm ->
            vm.uiState.value.incomingImportState is CadIncomingImportState.Ready &&
                vm.lastExternalImportSha256 == secondHash
        }

        scenario.onActivity { activity ->
            val vm = extractViewModel(activity)
            assertEquals(secondHash, vm.lastExternalImportSha256)
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
        pumpMainAndCompose()

        // Send third document (.pcad over octet-stream) while active drawing has unsaved edits
        val (thirdSourceFile, _, thirdIntent) = createSharedFileIntent(
            "mount_plate.pcad",
            thirdPcadBytes,
            "application/octet-stream"
        )
        scenario.onActivity { activity ->
            dispatchNewIntent(activity, thirdIntent)
        }
        waitForTagDisplayed("unsaved_drawing_confirmation_dialog")

        // Prove staging durability: revoke/delete external source file AFTER staging, before tapping Discard!
        assertTrue(thirdSourceFile.delete())

        composeRule.onNodeWithTag("unsaved_dialog_discard_and_continue").performClick()
        waitForScenarioViewModelCondition(scenario) { vm ->
            vm.uiState.value.incomingImportState is CadIncomingImportState.Ready &&
                vm.lastExternalImportSha256 == thirdHash
        }

        // Verify import STILL completed cleanly from app-private staged copy!
        scenario.onActivity { activity ->
            val vm = extractViewModel(activity)
            assertEquals("MountPlate_V3", vm.uiState.value.currentProjectName)
            assertEquals(thirdHash, vm.lastExternalImportSha256)
            val line = vm.uiState.value.entities.filterIsInstance<CadEntity.LineEntity>().first()
            assertEquals(320.0, line.end.x, 0.01)
        }
    }

    @Test
    fun `unsupported STEP over octet-stream displays visible error dialog without clobbering active drawing`() {
        val validDxf = buildDxfBytes("keep_me", endX = 140.0, radius = 15.0)
        val validHash = sha256(validDxf)
        val (_, _, initialIntent) = createSharedFileIntent("existing_plan.dxf", validDxf, "image/vnd.dxf")

        val scenario = scenarioOwner.launch<MainActivity>(initialIntent)
        waitForScenarioViewModelCondition(scenario) { vm ->
            vm.uiState.value.incomingImportState is CadIncomingImportState.Ready &&
                vm.lastExternalImportSha256 == validHash
        }

        val badStep = "ISO-10303-21;\nHEADER;\nDATA;\nEND-ISO-10303-21;".toByteArray(Charsets.UTF_8)
        val (_, _, badIntent) = createSharedFileIntent("solid_body.bin", badStep, "application/octet-stream")
        scenario.onActivity { activity ->
            dispatchNewIntent(activity, badIntent)
        }
        waitForTagDisplayed("external_import_error_dialog")

        composeRule.onNodeWithTag("external_import_error_title").assertIsDisplayed()
        composeRule.onNodeWithTag("external_import_error_message").assertIsDisplayed()
        composeRule.onNodeWithTag("external_import_dismiss_button").performClick()
        waitForScenarioViewModelCondition(scenario) { vm ->
            vm.uiState.value.incomingImportState is CadIncomingImportState.Idle
        }

        scenario.onActivity { activity ->
            val vm = extractViewModel(activity)
            assertEquals("existing_plan", vm.uiState.value.currentProjectName)
            assertEquals(validHash, vm.lastExternalImportSha256)
            assertTrue(vm.uiState.value.incomingImportState is CadIncomingImportState.Idle)
        }
    }

    @Test
    fun `SavedStateHandle restores pending staged external import and surfaces explicit failure when killed mid-read`() {
        val app = context.applicationContext as Application
        val offlineFetcher = ReleaseFeedFetcher { _, _, _, _ -> Result.success(null) }
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

        val restoredVm = CadWorkspaceViewModel(
            application = app,
            savedStateHandle = savedState,
            releaseFeedFetcher = offlineFetcher
        )
        waitForBoundedCondition {
            restoredVm.uiState.value.incomingImportState is CadIncomingImportState.AwaitingUnsavedDecision
        }

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
        val midReadVm = CadWorkspaceViewModel(
            application = app,
            savedStateHandle = midReadSavedState,
            releaseFeedFetcher = offlineFetcher
        )
        val failed = midReadVm.uiState.value.incomingImportState as? CadIncomingImportState.Failed
        assertTrue(failed != null)
        assertEquals(CadImportErrorKind.IO_ERROR, failed!!.errorKind)
    }

    @Test
    fun `ExternalScenarioOwnerRule preserves body failure and completes scenario teardown when test body throws`() {
        val nestedOwner = ExternalScenarioOwnerRule()
        val description = Description.createTestDescription(
            ExternalCadImportRobolectricTest::class.java,
            "simulatedFailingScenarioBody"
        )
        val dxfBytes = buildDxfBytes("fail_check", endX = 95.0, radius = 12.0)
        val secondBytes = buildPcadBytes("FailAfterWarmNewIntent", endX = 140.0)
        val (_, _, launchIntent) = createSharedFileIntent("throw_after_launch.dxf", dxfBytes, "image/vnd.dxf")
        val (_, _, warmIntent) = createSharedFileIntent("warm_before_throw.pcad", secondBytes, "application/octet-stream")

        var capturedScenario: ActivityScenario<MainActivity>? = null
        var caughtThrowable: Throwable? = null

        val failingStatement = object : Statement() {
            override fun evaluate() {
                val sc = nestedOwner.launch<MainActivity>(launchIntent)
                capturedScenario = sc
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                sc.onActivity { activity ->
                    dispatchNewIntent(activity, warmIntent)
                }
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                throw IllegalStateException("EXPECTED_HARNESS_BODY_FAILURE")
            }
        }

        try {
            nestedOwner.apply(failingStatement, description).evaluate()
        } catch (t: Throwable) {
            caughtThrowable = t
        }

        assertTrue(caughtThrowable is IllegalStateException)
        assertEquals("EXPECTED_HARNESS_BODY_FAILURE", caughtThrowable?.message)
        assertEquals("DONE", nestedOwner.currentPhase)
        assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, capturedScenario?.state)
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
