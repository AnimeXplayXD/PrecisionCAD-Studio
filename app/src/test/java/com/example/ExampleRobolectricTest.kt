package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.example.cad.data.CadTemplateFactory
import com.example.cad.dimension.AutoDimensionEngine
import com.example.cad.dimension.DimensionArrangementPreference
import com.example.cad.dimension.QDimDensityPolicy
import com.example.cad.io.CadDxfInterchange
import com.example.cad.io.CadPcadPersistence
import com.example.cad.io.PcadProjectDocument
import com.example.cad.model.CadUnit
import com.example.cad.model.PrecisionFormatConfig
import com.example.cad.model.UnitChangeMode
import com.example.cad.model.VirtualControllerConfig
import com.example.cad.model.WorkspaceSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `verify v17 PrecisionCAD Studio Workspace, IME Separation, Live-Preview Preservation, Editable Theta Coordinate Dialog, Vertical-Peek Controller Selector, QDIM Selective Removal, and Native PCAD + Geo PDF Sync`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("PrecisionCAD Studio", context.getString(R.string.app_name))
        assertEquals("com.precisioncad.studio", context.packageName)

        composeTestRule.waitForIdle()

        // 1. Verify First-Run Home Screen, Top-Right Update Status Icon, and Stacked Release Catalogue Action Deck (Requirement B)
        composeTestRule.onNodeWithTag("cad_home_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_update_status_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_update_status_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("release_catalogue_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("release_catalogue_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("release_catalogue_action_deck").assertIsDisplayed()
        composeTestRule.onNodeWithTag("release_catalogue_primary_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("release_catalogue_exchange_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("release_catalogue_exchange_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("release_catalogue_cancel_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("release_catalogue_cancel_button").performClick()
        composeTestRule.waitForIdle()

        // Enter workspace via New Drawing
        composeTestRule.onNodeWithTag("home_new_drawing_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // 2. Verify Separated Upper Command Region vs Stationary Lower Controller Region & Live X, Y, Theta HUD
        composeTestRule.onNodeWithTag("cad_technical_viewport").assertIsDisplayed()
        composeTestRule.onNodeWithTag("upper_command_ime_region").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lower_controller_stationary_region").assertIsDisplayed()
        composeTestRule.onNodeWithTag("virtual_cursor_controller_deck").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cursor_coord_x_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cursor_coord_y_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cursor_coord_theta_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("controller_mode_card_selector").assertIsDisplayed()

        // 3. Verify Vertical-Peek Two-Card Controller Mode Selector (Joystick <-> Touchpad)
        composeTestRule.onNodeWithTag("controller_joystick_surface").assertIsDisplayed()
        composeTestRule.onNodeWithTag("controller_mode_card_selector").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("controller_touchpad_surface").assertIsDisplayed()
        composeTestRule.onNodeWithTag("controller_mode_card_selector").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("controller_joystick_surface").assertIsDisplayed()

        // 4. Verify activating a CAD command from the Tool Drawer does NOT force
        // command_action_card_stack open while commandLineInput is empty and keyboard is closed, AND
        // verify sub-option switching preserves picked step points and live preview!
        composeTestRule.onNodeWithTag("bottom_tools_toggle_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cmd_button_RECTANGLE").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Running RECTANGLE command shows OverviewOverlayCard, while command_action_card_stack stays idle-hidden!
        composeTestRule.onNodeWithTag("overview_overlay_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_command_name_badge").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("command_action_card_stack").fetchSemanticsNodes().isEmpty())

        // Pick first rectangle corner at cursor, then switch Sharp -> Fillet -> Chamfer without losing step point
        composeTestRule.onNodeWithTag("cursor_pick_point_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("active_cmd_finish_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("suboption_Fillet").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("active_cmd_finish_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_command_fixed_val_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("suboption_Chamfer").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("active_cmd_finish_button").assertIsDisplayed()

        // Engage command bar by typing text -> command_action_card_stack appears with rear card 100% concealed at rest; swipe down triggers 4.0s post-exchange peek & cancel
        composeTestRule.onNodeWithTag("command_line_input_field").performTextInput("F")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("command_action_card_stack").assertIsDisplayed()
        composeTestRule.onNodeWithTag("command_card_send").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("command_card_cancel").fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithTag("command_action_card_stack").performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("command_card_cancel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("command_card_send").assertIsDisplayed()
        composeTestRule.onNodeWithTag("command_card_cancel").performClick()
        composeTestRule.waitForIdle()

        // 5. Verify Direct Coordinate Input Dialog: mutually exclusive Cartesian vs Polar subtrees, ABS/REL deck, and two-stage Move Cursor -> Pick flow
        composeTestRule.onNodeWithTag("cursor_coord_theta_text").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("coord_input_x_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("coord_input_y_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("coord_input_theta_field").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("coord_input_polar_field").fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithTag("coord_mode_polar").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("coord_input_polar_field").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("coord_input_x_field").fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithTag("coord_mode_cartesian").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("coord_input_theta_field").performTextReplacement("45")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("coord_move_cursor_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("coord_move_cursor_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("coord_dialog_preview_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("coord_pick_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("coord_cancel_button").performClick()
        composeTestRule.waitForIdle()

        // 6. Section 12: Verify Controller & OSNAP Settings Dialog with hoisted scroll, ENABLE/DISABLE cards, and 3-region OSNAP marquee cards
        composeTestRule.onNodeWithTag("controller_settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("controller_snap_settings_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("osnap_toggle_ENDPOINT").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("osnap_toggle_ENDPOINT").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("osnap_toggle_ENDPOINT").assertIsDisplayed()
        composeTestRule.onNodeWithTag("controller_settings_close_button").performClick()
        composeTestRule.waitForIdle()

        // 7. Section 9 & 10: Verify Quick Dimension (QDIM) Horizontal Card Selectors, Stage Badges, Two-Card Footer Deck, and Selective Dimension Removal
        composeTestRule.onNodeWithTag("top_qdim_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("qdim_scroll_header").assertIsDisplayed()
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
        composeTestRule.waitForIdle()

        // Re-open QDIM dialog after generating dimensions to verify Selective Dimension Removal dialog,
        // header SELECT ALL / DESELECT ALL chip, and non-layout-shifting transient marquee notification
        composeTestRule.onNodeWithTag("top_qdim_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("qdim_open_selective_clear_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("qdim_selective_clear_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("selective_clear_master_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("selective_clear_master_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("selective_clear_transient_marquee").assertIsDisplayed()
        composeTestRule.onNodeWithTag("selective_dim_cancel_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("qdim_close_icon_button").performClick()
        composeTestRule.waitForIdle()

        // 8. Requirement C: Verify Non-Destructive Home Navigation, Automatic Quick-Save Flush, Return to Active Drawing, and Unsaved Replacement Confirmation Dialog
        // Generating QDIM above incremented documentRevision so isDocumentDirty == true!
        composeTestRule.onNodeWithTag("top_home_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cad_home_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_return_to_active_drawing_button").assertIsDisplayed()

        // Return to active drawing without losing entities or undo history
        composeTestRule.onNodeWithTag("home_return_to_active_drawing_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cad_technical_viewport").assertIsDisplayed()

        // Navigate back to Home while document is dirty and request a New Blank Drawing -> Unsaved confirmation dialog appears!
        composeTestRule.onNodeWithTag("top_home_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_new_drawing_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("unsaved_drawing_confirmation_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unsaved_dialog_save_and_continue").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unsaved_dialog_discard_and_continue").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unsaved_dialog_stay_here").assertIsDisplayed()

        // Tap "Stay Here" -> cancels replacement and keeps active session
        composeTestRule.onNodeWithTag("unsaved_dialog_stay_here").performClick()
        composeTestRule.waitForIdle()
        assertTrue(composeTestRule.onAllNodesWithTag("unsaved_drawing_confirmation_dialog").fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithTag("home_return_to_active_drawing_button").assertIsDisplayed()

        // Tap New Blank Drawing again and choose "Save and Continue" -> saves current project and creates new blank drawing
        composeTestRule.onNodeWithTag("home_new_drawing_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("unsaved_dialog_save_and_continue").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cad_technical_viewport").assertIsDisplayed()

        // 9. Verify Automatic Quick Save (.pcad), Explicit Save (Documents/PrecisionCAD/Geo.pcad), and Geo.pdf Title Sync
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
            gridSnapConfig = com.example.cad.model.GridAndSnapConfig(),
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

        // 10. Verify V18.1 CadGitHubUpdateRepository.createManageUnknownAppSourcesIntent() KTX String.toUri() behavior
        val updater = com.example.cad.update.CadGitHubUpdateRepository(
            context = context,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )
        val unknownSourcesIntent = updater.createManageUnknownAppSourcesIntent()
        assertNotNull(unknownSourcesIntent)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, unknownSourcesIntent.action)
            assertEquals("package:com.precisioncad.studio", unknownSourcesIntent.data?.toString())
        }
    }
}
