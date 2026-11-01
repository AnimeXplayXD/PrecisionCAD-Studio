package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.example.cad.data.CadTemplateFactory
import com.example.cad.dimension.AutoDimensionEngine
import com.example.cad.dimension.QDimDensityPolicy
import com.example.cad.io.CadDxfInterchange
import org.junit.Assert.assertEquals
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
    fun `verify v16 first-run Home Screen, New Drawing entry, stacked Send Cancel command card deck, action-only marquee with fixed VAL badge, 3-part coordinate dialog, and 3-format Project Hub export deck`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("PrecisionCAD", context.getString(R.string.app_name))

        composeTestRule.waitForIdle()

        // 1. Section 62: Verify First-Run Home Screen is the initial screen with empty Saved Drawings
        composeTestRule.onNodeWithTag("cad_home_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_new_drawing_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_open_drawing_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_empty_saved_drawings").performScrollTo().assertIsDisplayed()

        // Start a New Blank Drawing to enter the 2D CAD Workspace
        composeTestRule.onNodeWithTag("home_new_drawing_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // 2. Verify 2D Technical Viewport, Virtual Cursor Controller Deck, Polar HUD badge, and live X/Y readouts
        composeTestRule.onNodeWithTag("cad_technical_viewport").assertIsDisplayed()
        composeTestRule.onNodeWithTag("virtual_cursor_controller_deck").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cursor_coord_x_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cursor_coord_y_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_toggle_polar").assertIsDisplayed()

        // 3. Section 61: Verify Persistent Command Entry Bar is idle-clean (only text field, no CMD: prefix button)
        composeTestRule.onNodeWithTag("persistent_command_entry_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("command_line_input_field").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("cmd_activate_button").fetchSemanticsNodes().isEmpty())

        // 4. Verify Tool Browser translucent closed handle, 5 category tiles, and single-category content pane
        composeTestRule.onNodeWithTag("tool_drawer_handle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bottom_tools_toggle_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tool_drawer_panel").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("tool_drawer_handle").fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithTag("category_tile_DRAW").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_tile_MODIFY").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_category_content_pane").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cmd_button_LINE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cmd_button_POLYGON").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("cmd_button_FILLET").fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithTag("category_tile_MODIFY").performClick()
        composeTestRule.waitForIdle()
        assertTrue(composeTestRule.onAllNodesWithTag("cmd_button_LINE").fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithTag("cmd_button_FILLET").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("tool_drawer_panel").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tool_drawer_handle").assertIsDisplayed()

        // 5. Verify Controller Mode-Specific Layouts
        composeTestRule.onNodeWithTag("controller_joystick_surface").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nudge_up_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cursor_pick_point_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("controller_mode_touchpad").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("controller_touchpad_surface").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("nudge_up_button").fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithTag("cursor_pick_point_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("controller_mode_joystick").performClick()
        composeTestRule.waitForIdle()

        // 6. Sections 51, 55.2 & 61: Verify Prefix Autocomplete, Active-Session Overview with fixed badge + action-only marquee,
        // and Stacked Send / Cancel Action-Card Compartment (downward swipe swaps Send <-> Cancel)
        composeTestRule.onNodeWithTag("command_line_input_field").performTextInput("F")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("command_action_card_stack").assertIsDisplayed()
        composeTestRule.onNodeWithTag("command_card_send").assertIsDisplayed()
        composeTestRule.onNodeWithTag("command_suggestion_strip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("suggestion_chip_FILLET").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overview_overlay_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_command_name_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_command_fixed_val_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_command_marquee_text").assertIsDisplayed()

        // Swipe downward on command_action_card_stack to bring Cancel card to front, then tap Cancel
        composeTestRule.onNodeWithTag("command_action_card_stack").performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("command_card_cancel").performClick()
        composeTestRule.waitForIdle()

        // 7. Section 50: Verify Structured 3-Part Direct Coordinate & Polar Expression Dialog
        composeTestRule.onNodeWithTag("cursor_coord_x_text").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("coord_mode_cartesian").assertIsDisplayed()
        composeTestRule.onNodeWithTag("coord_mode_polar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("coord_input_x_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("coord_input_y_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("coord_input_polar_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("coord_cancel_button").performClick()
        composeTestRule.waitForIdle()

        // 8. Sections 63, 65 & 66: Verify Project Hub Three-Format (DXF -> PDF -> SVG) Stacked Export Card Deck
        composeTestRule.onNodeWithTag("top_project_hub_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("project_hub_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("project_hub_export_subtitle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("project_hub_export_card_deck").assertIsDisplayed()
        composeTestRule.onNodeWithTag("project_hub_export_card_dxf").assertIsDisplayed()
        // Swipe downward on the export card deck to cycle DXF -> PDF -> SVG -> DXF
        composeTestRule.onNodeWithTag("project_hub_export_card_deck").performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("project_hub_export_card_pdf").assertIsDisplayed()
        composeTestRule.onNodeWithTag("project_hub_export_card_deck").performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("project_hub_export_card_svg").assertIsDisplayed()

        // 9. Verify Plotted PDF Export produces a valid PDF file
        val basePayload = CadTemplateFactory.createSixHoleMountingPlate()
        val qdimEntities = AutoDimensionEngine.generateQuickDimensions(
            allEntities = basePayload.entities,
            targetEntities = basePayload.entities,
            density = QDimDensityPolicy.BALANCED
        )
        val pdfFile = CadDxfInterchange.exportToPdfFile(
            context = context,
            fileName = "test_plotted_sheet.pdf",
            payload = basePayload.copy(entities = qdimEntities)
        )
        assertTrue(pdfFile.exists())
        assertTrue(pdfFile.length() > 1024L)
    }
}

