package com.precisioncad.studio.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.precisioncad.studio.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test verifying the application context and package identity on an Android device.
 */
@RunWith(AndroidJUnit4::class)
class PrecisionCADInstrumentedTest {
  @Test
  fun useAppContext() {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals("com.precisioncad.studio", appContext.packageName)
    assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
  }
}
