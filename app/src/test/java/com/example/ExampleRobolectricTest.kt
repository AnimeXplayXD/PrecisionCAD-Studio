package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
    fun `verify app name and core CAD workspace components render and respond`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("PrecisionCAD", context.getString(R.string.app_name))

        composeTestRule.waitForIdle()

        // Verify 2D Technical Viewport and Dedicated Virtual Cursor Controller Deck are displayed
        composeTestRule.onNodeWithTag("cad_technical_viewport").assertIsDisplayed()
        composeTestRule.onNodeWithTag("virtual_cursor_controller_deck").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cursor_pick_point_button").assertIsDisplayed()

        // Switch controller mode between Touchpad and Joystick
        composeTestRule.onNodeWithTag("controller_mode_touchpad").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("controller_mode_joystick").performClick()
        composeTestRule.waitForIdle()

        // Open CAD Command Compatibility Matrix Dialog and verify it displays
        composeTestRule.onNodeWithTag("top_command_matrix_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("command_compatibility_matrix_dialog").assertIsDisplayed()
    }
}
