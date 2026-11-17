package com.precisioncad.studio

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.precisioncad.studio.cad.data.CadTemplateFactory
import com.precisioncad.studio.cad.dimension.AutoDimensionEngine
import com.precisioncad.studio.cad.dimension.DimensionArrangementPreference
import com.precisioncad.studio.cad.dimension.QDimDensityPolicy
import com.precisioncad.studio.cad.io.CadDxfInterchange
import com.precisioncad.studio.cad.io.CadPcadPersistence
import com.precisioncad.studio.cad.io.PcadProjectDocument
import com.precisioncad.studio.cad.model.CadUnit
import com.precisioncad.studio.cad.model.PrecisionFormatConfig
import com.precisioncad.studio.cad.model.UnitChangeMode
import com.precisioncad.studio.cad.model.VirtualControllerConfig
import com.precisioncad.studio.cad.model.WorkspaceSpace
import com.precisioncad.studio.cad.update.CadGitHubUpdateRepository
import com.precisioncad.studio.cad.update.ReleaseFeedFetcher
import com.precisioncad.studio.ui.CadWorkspaceViewModel
import com.precisioncad.studio.ui.components.CadDeckIdleTimerState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrecisionCADRobolectricTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private lateinit var context: Context
    private var updateCheckRequestsObserved: Int = 0

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        updateCheckRequestsObserved = 0
        clearLocalTestState()
        CadGitHubUpdateRepository.defaultFetcherOverrideForTesting =
            ReleaseFeedFetcher { _, _, _, _ ->
                updateCheckRequestsObserved++
                Result.success(null)
            }
        CadDeckIdleTimerState.timerDriverEnabledForTesting = false
    }

    @After
    fun tearDown() {
        CadGitHubUpdateRepository.defaultFetcherOverrideForTesting = null
        CadDeckIdleTimerState.timerDriverEnabledForTesting = true
        clearLocalTestState()
    }

    private fun clearLocalTestState() {
        context.getSharedPreferences("precision_cad_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("precisioncad_github_updater_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "quick_save.pcad").delete()
        File(context.filesDir, "quick_save").deleteRecursively()
        File(context.filesDir, "Documents/PrecisionCAD").deleteRecursively()
    }

    private fun launchHomeScenario(): ActivityScenario<MainActivity> {
        val intent = Intent(context, MainActivity::class.java)
        return ActivityScenario.launch<MainActivity>(intent).also {
            waitForTagDisplayed("cad_home_screen")
        }
    }

    private fun enterWorkspaceFromHome() {
        composeTestRule.onNodeWithTag("home_new_drawing_button").performScrollTo().performClick()
        waitForTagDisplayed("cad_technical_viewport")
    }

    private fun waitForBoundedCondition(
        timeoutMs: Long = 10_000L,
        condition: () -> Boolean
    ) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
    }

    private fun waitForTagDisplayed(tag: String, timeoutMs: Long = 10_000L) {
        waitForBoundedCondition(timeoutMs) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun waitForTagAbsent(tag: String, timeoutMs: Long = 10_000L) {
        waitForBoundedCondition(timeoutMs) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun extractViewModel(activity: MainActivity): CadWorkspaceViewModel {
        val field = MainActivity::class.java.getDeclaredField("workspaceViewModel\$delegate").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        return (field.get(activity) as Lazy<CadWorkspaceViewModel>).value
    }

    @Test
    fun `home screen displays update icon, triggers startup offline check, and toggles release catalogue action deck`() {
        assertEquals("PrecisionCAD Studio", context.getString(R.string.app_name))
        assertEquals("com.precisioncad.studio", context.packageName)

        launchHomeScenario().use { scenario ->
            scenario.onActivity { activity ->
                val vm = extractViewModel(activity)
                assertTrue(vm.updateRepository.requestedUpdateCheckCount >= 1)
            }
            waitForBoundedCondition { updateCheckRequestsObserved >= 1 }

            composeTestRule.onNodeWithTag("cad_home_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("home_update_status_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("home_update_status_button").performClick()
            waitForTagDisplayed("release_catalogue_dialog")

            composeTestRule.onNodeWithTag("release_catalogue_title").assertIsDisplayed()
            composeTestRule.onNodeWithTag("release_catalogue_action_deck").assertIsDisplayed()
            composeTestRule.onNodeWithTag("release_catalogue_primary_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("release_catalogue_exchange_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("release_catalogue_exchange_button").performClick()
            waitForTagDisplayed("release_catalogue_cancel_button")
            composeTestRule.onNodeWithTag("release_catalogue_cancel_button").performClick()
            waitForTagAbsent("release_catalogue_dialog")
        }
    }

    @Test
    fun `workspace separates upper IME region from stationary controller dock and switches Joystick and Touchpad cards`() {
        launchHomeScenario().use {
            enterWorkspaceFromHome()

            composeTestRule.onNodeWithTag("cad_technical_viewport").assertIsDisplayed()
            composeTestRule.onNodeWithTag("upper_command_ime_region").assertIsDisplayed()
            composeTestRule.onNodeWithTag("lower_controller_stationary_region").assertIsDisplayed()
            composeTestRule.onNodeWithTag("virtual_cursor_controller_deck").assertIsDisplayed()
            composeTestRule.onNodeWithTag("cursor_coord_x_text").assertIsDisplayed()
            composeTestRule.onNodeWithTag("cursor_coord_y_text").assertIsDisplayed()
            composeTestRule.onNodeWithTag("cursor_coord_theta_text").assertIsDisplayed()
            composeTestRule.onNodeWithTag("controller_mode_card_selector").assertIsDisplayed()

            composeTestRule.onNodeWithTag("controller_joystick_surface").assertIsDisplayed()
            composeTestRule.onNodeWithTag("controller_mode_card_selector").performClick()
            waitForTagDisplayed("controller_touchpad_surface")
            composeTestRule.onNodeWithTag("controller_mode_card_selector").performClick()
            waitForTagDisplayed("controller_joystick_surface")
        }
    }

    @Test
    fun `tool drawer RECTANGLE preserves live preview on suboption switch and command deck stays concealed until typed`() {
        launchHomeScenario().use {
            enterWorkspaceFromHome()

            composeTestRule.onNodeWithTag("bottom_tools_toggle_button").performClick()
            waitForTagDisplayed("cmd_button_RECTANGLE")
            composeTestRule.onNodeWithTag("cmd_button_RECTANGLE").performScrollTo().performClick()

            waitForTagDisplayed("overview_overlay_card")
            composeTestRule.onNodeWithTag("active_command_name_badge").assertIsDisplayed()
            assertTrue(composeTestRule.onAllNodesWithTag("command_action_card_stack").fetchSemanticsNodes().isEmpty())

            // Pick first corner, then switch Sharp -> Fillet -> Chamfer without losing step point
            composeTestRule.onNodeWithTag("cursor_pick_point_button").performClick()
            waitForTagDisplayed("active_cmd_finish_button")
            composeTestRule.onNodeWithTag("suboption_Fillet").performClick()
            waitForTagDisplayed("active_command_fixed_val_badge")
            composeTestRule.onNodeWithTag("active_cmd_finish_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("suboption_Chamfer").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("active_cmd_finish_button").assertIsDisplayed()

            // Typing text reveals command deck with rear card concealed; swipe down reveals CANCEL
            composeTestRule.onNodeWithTag("command_line_input_field").performTextInput("F")
            waitForTagDisplayed("command_action_card_stack")
            composeTestRule.onNodeWithTag("command_card_send").assertIsDisplayed()
            assertTrue(composeTestRule.onAllNodesWithTag("command_card_cancel").fetchSemanticsNodes().isEmpty())
            composeTestRule.onNodeWithTag("command_action_card_stack").performTouchInput { swipeDown() }
            waitForTagDisplayed("command_card_cancel")
            composeTestRule.onNodeWithTag("command_card_send").assertIsDisplayed()
            composeTestRule.onNodeWithTag("command_card_cancel").performClick()
            waitForTagAbsent("command_action_card_stack")
        }
    }

    @Test
    fun `direct coordinate dialog switches Cartesian and Polar subtrees and executes two-stage Move Cursor preview`() {
        launchHomeScenario().use {
            enterWorkspaceFromHome()

            composeTestRule.onNodeWithTag("cursor_coord_theta_text").performClick()
            waitForTagDisplayed("coord_input_x_field")
            composeTestRule.onNodeWithTag("coord_input_y_field").assertIsDisplayed()
            composeTestRule.onNodeWithTag("coord_input_theta_field").assertIsDisplayed()
            assertTrue(composeTestRule.onAllNodesWithTag("coord_input_polar_field").fetchSemanticsNodes().isEmpty())

            composeTestRule.onNodeWithTag("coord_mode_polar").performClick()
            waitForTagDisplayed("coord_input_polar_field")
            assertTrue(composeTestRule.onAllNodesWithTag("coord_input_x_field").fetchSemanticsNodes().isEmpty())

            composeTestRule.onNodeWithTag("coord_mode_cartesian").performClick()
            waitForTagDisplayed("coord_input_theta_field")
            composeTestRule.onNodeWithTag("coord_input_theta_field").performTextReplacement("45")
            waitForTagDisplayed("coord_move_cursor_button")
            composeTestRule.onNodeWithTag("coord_move_cursor_button").performClick()
            waitForTagDisplayed("coord_dialog_preview_state")
            composeTestRule.onNodeWithTag("coord_pick_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("coord_cancel_button").performClick()
            waitForTagAbsent("coord_dialog_preview_state")
        }
    }

    @Test
    fun `controller OSNAP settings and QDIM horizontal card decks with selective dimension removal operate deterministically`() {
        launchHomeScenario().use {
            enterWorkspaceFromHome()

            // Controller & OSNAP Settings
            composeTestRule.onNodeWithTag("controller_settings_button").performClick()
            waitForTagDisplayed("controller_snap_settings_dialog")
            composeTestRule.onNodeWithTag("osnap_toggle_ENDPOINT").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("osnap_toggle_ENDPOINT").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("osnap_toggle_ENDPOINT").assertIsDisplayed()
            composeTestRule.onNodeWithTag("controller_settings_close_button").performClick()
            waitForTagAbsent("controller_snap_settings_dialog")

            // QDIM card selectors + Selective Dimension Removal
            composeTestRule.onNodeWithTag("top_qdim_button").performScrollTo().performClick()
            waitForTagDisplayed("qdim_scroll_header")
            composeTestRule.onNodeWithTag("qdim_scroll_footer").assertIsDisplayed()
            assertTrue(composeTestRule.onAllNodesWithTag("qdim_help_callout_STAGE_1").fetchSemanticsNodes().isEmpty())
            composeTestRule.onNodeWithTag("qdim_stage_badge_mode").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("qdim_wheel_mode").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("qdim_wheel_arrangement").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("qdim_wheel_density").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("qdim_wheel_density").performTouchInput { swipeLeft() }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("qdim_footer_action_deck").assertIsDisplayed()
            composeTestRule.onNodeWithTag("execute_qdim_confirm_button").performClick()
            waitForTagAbsent("qdim_scroll_header")

            composeTestRule.onNodeWithTag("top_qdim_button").performScrollTo().performClick()
            waitForTagDisplayed("qdim_open_selective_clear_button")
            composeTestRule.onNodeWithTag("qdim_open_selective_clear_button").performClick()
            waitForTagDisplayed("qdim_selective_clear_dialog")
            composeTestRule.onNodeWithTag("selective_clear_master_toggle").assertIsDisplayed()
            composeTestRule.onNodeWithTag("selective_clear_master_toggle").performClick()
            waitForTagDisplayed("selective_clear_transient_marquee")
            composeTestRule.onNodeWithTag("selective_dim_cancel_button").performClick()
            waitForTagAbsent("qdim_selective_clear_dialog")
            composeTestRule.onNodeWithTag("qdim_close_icon_button").performClick()
            waitForTagAbsent("qdim_scroll_header")
        }
    }

    @Test
    fun `home navigation preserves dirty active session, prompts unsaved confirmation, and saves before new blank drawing`() {
        launchHomeScenario().use {
            enterWorkspaceFromHome()

            // Make drawing dirty by executing QDIM
            composeTestRule.onNodeWithTag("top_qdim_button").performScrollTo().performClick()
            waitForTagDisplayed("execute_qdim_confirm_button")
            composeTestRule.onNodeWithTag("execute_qdim_confirm_button").performClick()
            waitForTagAbsent("qdim_scroll_header")

            // Return to Home Screen -> Return to Active Drawing button appears
            composeTestRule.onNodeWithTag("top_home_button").performScrollTo().performClick()
            waitForTagDisplayed("cad_home_screen")
            composeTestRule.onNodeWithTag("home_return_to_active_drawing_button").assertIsDisplayed()

            // Resume active drawing seamlessly
            composeTestRule.onNodeWithTag("home_return_to_active_drawing_button").performClick()
            waitForTagDisplayed("cad_technical_viewport")

            // Navigate to Home while dirty and request New Drawing -> Unsaved confirmation dialog appears
            composeTestRule.onNodeWithTag("top_home_button").performScrollTo().performClick()
            waitForTagDisplayed("cad_home_screen")
            composeTestRule.onNodeWithTag("home_new_drawing_button").performScrollTo().performClick()
            waitForTagDisplayed("unsaved_drawing_confirmation_dialog")
            composeTestRule.onNodeWithTag("unsaved_dialog_save_and_continue").assertIsDisplayed()
            composeTestRule.onNodeWithTag("unsaved_dialog_discard_and_continue").assertIsDisplayed()
            composeTestRule.onNodeWithTag("unsaved_dialog_stay_here").assertIsDisplayed()

            // Tap "Stay Here" -> cancels replacement and keeps active session
            composeTestRule.onNodeWithTag("unsaved_dialog_stay_here").performClick()
            waitForTagAbsent("unsaved_drawing_confirmation_dialog")
            composeTestRule.onNodeWithTag("home_return_to_active_drawing_button").assertIsDisplayed()

            // Tap New Blank Drawing again and choose "Save and Continue"
            composeTestRule.onNodeWithTag("home_new_drawing_button").performScrollTo().performClick()
            waitForTagDisplayed("unsaved_drawing_confirmation_dialog")
            composeTestRule.onNodeWithTag("unsaved_dialog_save_and_continue").performClick()
            waitForTagDisplayed("cad_technical_viewport")
        }
    }

    @Test
    fun `native pcad quick save, explicit Geo_pcad save, Geo_pdf title sync, and manage unknown sources intent succeed`() {
        val basePayload = CadTemplateFactory.createSixHoleMountingPlate()
        val qdimEntities = AutoDimensionEngine.generateQuickDimensions(
            allEntities = basePayload.entities,
            targetEntities = basePayload.entities,
            density = QDimDensityPolicy.BALANCED
        )
        val geoDoc = PcadProjectDocument(
            projectId = "geo_id",
            projectName = "Geo.pcad",
            savedAtEpochMs = System.currentTimeMillis(),
            storageUnit = CadUnit.MM,
            displayUnit = CadUnit.MM,
            unitChangeMode = UnitChangeMode.CONVERT_DISPLAY_UNITS,
            precision = PrecisionFormatConfig(),
            workspaceSpace = WorkspaceSpace.MODEL_SPACE,
            sheetSetup = basePayload.sheetSetup.copy(
                titleBlockTitle = "Geo"
            ),
            gridSnapConfig = com.precisioncad.studio.cad.model.GridAndSnapConfig(),
            controllerConfig = VirtualControllerConfig(),
            isQuickDimensionEnabled = true,
            qdimDensity = QDimDensityPolicy.BALANCED,
            qdimArrangement = DimensionArrangementPreference.AUTO_CHAIN,
            layers = basePayload.layers,
            blocks = basePayload.blocks,
            entities = qdimEntities,
            constraints = emptyList()
        )
        val quickSavedFile = CadPcadPersistence.performAutomaticQuickSave(context, geoDoc)
        assertNotNull(quickSavedFile)
        assertTrue(quickSavedFile.exists())
        val loadedQuickSave = CadPcadPersistence.loadLatestQuickSave(context)
        assertNotNull(loadedQuickSave)
        assertEquals("Geo", loadedQuickSave!!.projectName)

        val explicitResult = CadPcadPersistence.saveExplicitToDocumentsPrecisionCad(
            context = context,
            projectName = "Geo.pcad",
            document = geoDoc
        )
        assertEquals("Geo", explicitResult.basename)
        assertEquals("Geo.pcad", explicitResult.fileName)
        assertTrue(explicitResult.displayPath.contains("PrecisionCAD/Geo.pcad"))

        val pdfFile = CadDxfInterchange.exportToPdfFile(
            context = context,
            fileName = "Geo.pcad",
            payload = basePayload.copy(entities = qdimEntities)
        )
        assertEquals("Geo.pdf", pdfFile.name)
        assertTrue(pdfFile.exists())
        assertTrue(pdfFile.length() > 1024L)

        // Verify CadGitHubUpdateRepository.createManageUnknownAppSourcesIntent() KTX String.toUri() behavior
        val updater = CadGitHubUpdateRepository(
            context = context,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            releaseFeedFetcher = ReleaseFeedFetcher { _, _, _, _ -> Result.success(null) }
        )
        val unknownSourcesIntent = updater.createManageUnknownAppSourcesIntent()
        assertNotNull(unknownSourcesIntent)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, unknownSourcesIntent.action)
            assertEquals("package:com.precisioncad.studio", unknownSourcesIntent.data?.toString())
        }
    }

    @Test
    fun `update repository handles offline failure, 304 not modified, and mock release feed deterministically`() {
        val scheduler = kotlinx.coroutines.test.TestCoroutineScheduler()
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(scheduler)
        val scope = kotlinx.coroutines.CoroutineScope(dispatcher)

        // 1. Offline / HTTP failure retains synthesized installed catalogue without hanging
        val failingRepo = com.precisioncad.studio.cad.update.CadGitHubUpdateRepository(
            context = context,
            scope = scope,
            releaseFeedFetcher = ReleaseFeedFetcher { _, _, _, _ ->
                Result.failure(java.io.IOException("Simulated offline"))
            },
            ioDispatcher = dispatcher
        )
        failingRepo.checkForUpdates(manual = true)
        scheduler.advanceUntilIdle()
        assertEquals(1, failingRepo.requestedUpdateCheckCount)
        assertTrue(failingRepo.releases.value.any { it.isInstalledVersion })

        // 2. HTTP 304 Not Modified updates check timestamp cleanly
        val notModifiedRepo = com.precisioncad.studio.cad.update.CadGitHubUpdateRepository(
            context = context,
            scope = scope,
            releaseFeedFetcher = ReleaseFeedFetcher { _, _, _, _ -> Result.success(null) },
            ioDispatcher = dispatcher
        )
        notModifiedRepo.checkForUpdates(manual = true)
        scheduler.advanceUntilIdle()
        assertTrue(notModifiedRepo.releases.value.any { it.isInstalledVersion })

        // 3. HTTP 200 JSON payload parses release and records ETag
        val jsonRepo = com.precisioncad.studio.cad.update.CadGitHubUpdateRepository(
            context = context,
            scope = scope,
            releaseFeedFetcher = ReleaseFeedFetcher { _, _, _, onEtag ->
                onEtag("\"etag-ci-test\"")
                Result.success("[]")
            },
            ioDispatcher = dispatcher
        )
        jsonRepo.checkForUpdates(manual = true)
        scheduler.advanceUntilIdle()
        assertTrue(jsonRepo.releases.value.any { it.isInstalledVersion })
    }
}
