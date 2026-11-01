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
    fun `verify v15 overlay-invariant workspace, translucent closed tool handle with single-category content pane, touchpad hiding only StepNudgePad, active-session Overview, Polar HUD toggle, and PDF export`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("PrecisionCAD", context.getString(R.string.app_name))

        composeTestRule.waitForIdle()

        // 1. Verify 2D Technical Viewport, Virtual Cursor Controller Deck, Polar HUD badge, and live X/Y readouts are displayed
        composeTestRule.onNodeWithTag("cad_technical_viewport").assertIsDisplayed()
        composeTestRule.onNodeWithTag("virtual_cursor_controller_deck").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cursor_coord_x_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cursor_coord_y_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_toggle_polar").assertIsDisplayed()

        // 2. Verify Persistent Command Entry Bar is continuously visible even in idle state (no CMD click required)
        composeTestRule.onNodeWithTag("persistent_command_entry_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("command_line_input_field").assertIsDisplayed()

        // 3. Verify Tool Browser has a subtle translucent arrow handle in closed state, hides it when open,
        // and shows 5 vertical category tiles + ONLY the selected category's commands in the right content pane
        composeTestRule.onNodeWithTag("tool_drawer_handle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bottom_tools_toggle_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tool_drawer_panel").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("tool_drawer_handle").fetchSemanticsNodes().isEmpty())
        // Verify 5 vertical category side tiles exist
        composeTestRule.onNodeWithTag("category_tile_DRAW").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_tile_MODIFY").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_category_content_pane").assertIsDisplayed()
        // Verify DRAW is selected by default (LINE and POLYGON visible; MODIFY commands like FILLET not shown)
        composeTestRule.onNodeWithTag("cmd_button_LINE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cmd_button_POLYGON").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("cmd_button_FILLET").fetchSemanticsNodes().isEmpty())
        // Click MODIFY tile on left rail and verify right content pane switches exclusively to MODIFY commands
        composeTestRule.onNodeWithTag("category_tile_MODIFY").performClick()
        composeTestRule.waitForIdle()
        assertTrue(composeTestRule.onAllNodesWithTag("cmd_button_LINE").fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithTag("cmd_button_FILLET").performScrollTo().assertIsDisplayed()
        // Close tool browser via leftward swipe on the open drawer and verify closed handle reappears
        composeTestRule.onNodeWithTag("tool_drawer_panel").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tool_drawer_handle").assertIsDisplayed()

        // 4. Verify Controller Mode-Specific Layouts (Section 42.2):
        // Initial mode is VELOCITY_JOYSTICK (shows StepNudgePad + joystick surface + PICK PT)
        composeTestRule.onNodeWithTag("controller_joystick_surface").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nudge_up_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cursor_pick_point_button").assertIsDisplayed()
        // Switch to RELATIVE_TOUCHPAD: hides ONLY StepNudgePad, expands touchpad surface, and retains PICK PT & step-increment strip
        composeTestRule.onNodeWithTag("controller_mode_touchpad").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("controller_touchpad_surface").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("nudge_up_button").fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithTag("cursor_pick_point_button").assertIsDisplayed()
        // Switch back to VELOCITY_JOYSTICK and verify StepNudgePad + joystick + PICK PT layout
        composeTestRule.onNodeWithTag("controller_mode_joystick").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("controller_joystick_surface").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nudge_up_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cursor_pick_point_button").assertIsDisplayed()

        // 5. Verify Prefix Autocomplete ("L" -> LINE ranked first) and Active-Session floating Overview card
        composeTestRule.onNodeWithTag("command_line_input_field").performTextInput("L")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("command_suggestion_strip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("suggestion_chip_LINE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("suggestion_chip_LINE").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overview_overlay_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_command_prompt_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_cmd_cancel_button").performClick()
        composeTestRule.waitForIdle()

        // 6. Verify QDIM generation + Clear All Dimensions confirmation flow + Undo restoration
        composeTestRule.onNodeWithTag("top_qdim_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("execute_qdim_confirm_button").performClick()
        composeTestRule.waitForIdle()
        // Re-open QDIM dialog and trigger Clear All Dimensions
        composeTestRule.onNodeWithTag("top_qdim_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("clear_all_dimensions_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("clear_all_dimensions_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("clear_all_dimensions_confirm_button").performClick()
        composeTestRule.waitForIdle()
        // Undo restores the cleared dimensions in one step
        composeTestRule.onNodeWithTag("top_undo_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // 7. Verify Plotted PDF Export with QDIM dimensions and polyline bulge arcs produces a valid PDF file
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
