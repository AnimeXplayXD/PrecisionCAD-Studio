package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
    fun `verify redesigned workspace, contextual command line, retractable drawer, controller dock, fit drawing, and PDF export`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("PrecisionCAD", context.getString(R.string.app_name))

        composeTestRule.waitForIdle()

        // 1. Verify 2D Technical Viewport, Virtual Cursor Controller Deck, and live X/Y readouts are displayed
        composeTestRule.onNodeWithTag("cad_technical_viewport").assertIsDisplayed()
        composeTestRule.onNodeWithTag("virtual_cursor_controller_deck").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cursor_pick_point_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cursor_coord_x_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cursor_coord_y_text").assertIsDisplayed()

        // 2. Verify Retractable Tool Drawer is collapsed by default and expands on tap
        composeTestRule.onNodeWithTag("tool_drawer_handle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tool_drawer_handle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tool_drawer_panel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tool_drawer_handle").performClick()
        composeTestRule.waitForIdle()

        // 3. Verify Contextual Command Line & Prefix Autocomplete ("L" -> LINE ranked first)
        composeTestRule.onNodeWithTag("cmd_activate_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cmd_activate_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("command_line_input_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("command_line_input_field").performTextInput("L")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("command_suggestion_strip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("suggestion_chip_LINE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("suggestion_chip_LINE").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("active_command_prompt_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_cmd_cancel_button").performClick()
        composeTestRule.waitForIdle()

        // 4. Verify Collapsible Virtual Cursor Controller Dock keeps mode selectors and X/Y visible when collapsed
        composeTestRule.onNodeWithTag("controller_collapse_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("controller_mode_touchpad").assertIsDisplayed()
        composeTestRule.onNodeWithTag("controller_mode_joystick").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cursor_coord_x_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cursor_coord_y_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("controller_collapse_button").performClick()
        composeTestRule.waitForIdle()

        // 5. Verify Fit Drawing button triggers transient notification banner
        composeTestRule.onNodeWithTag("top_zoom_extents_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("transient_notification_banner").assertIsDisplayed()

        // 6. Verify + Action Launcher opens and displays
        composeTestRule.onNodeWithTag("top_action_launcher_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("action_launcher_dialog").assertIsDisplayed()

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
