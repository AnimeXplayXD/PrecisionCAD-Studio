package com.precisioncad.studio.testsupport

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.precisioncad.studio.cad.io.CadIncomingDocumentRouter
import com.precisioncad.studio.cad.update.CadGitHubUpdateRepository
import com.precisioncad.studio.cad.update.ReleaseFeedFetcher
import com.precisioncad.studio.ui.components.CadDeckIdleTimerState
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.Shadows
import java.io.File

/**
 * Outer JUnit [TestRule] that owns [ActivityScenario] lifecycles and test fixtures outside
 * the inner Compose test rule (`createEmptyComposeRule()`).
 *
 * Why this ordering matters:
 * Calling `ActivityScenario.close()` inside a `createEmptyComposeRule()` statement (for example via
 * `ActivityScenario.launch(...).use { ... }` inside the test body) invokes `moveToState(DESTROYED)`
 * while Compose's `AndroidComposeTestRule` / `ComposeRootRegistry` and coroutine test environment
 * are still active and intercepting frame/idle synchronization. By placing [ExternalScenarioOwnerRule]
 * as the outer link of a `RuleChain` and `createEmptyComposeRule()` as the inner link:
 * 1. [ExternalScenarioOwnerRule] prepares fixtures and test-only overrides before Compose starts.
 * 2. The inner Compose rule starts its test environment and runs the test body.
 * 3. The test body launches one or more [ActivityScenario] instances via [launch] and exits
 *    WITHOUT closing them inside the Compose test environment.
 * 4. The inner Compose rule tears down its test environment and returns.
 * 5. [ExternalScenarioOwnerRule] closes all registered [ActivityScenario] instances in reverse
 *    launch order outside the Compose test environment, then restores previous global settings
 *    and deletes temporary files.
 */
class ExternalScenarioOwnerRule(
    private val clearAppPreferencesAndFiles: Boolean = false,
    private val onReleaseFeedRequested: (() -> Unit)? = null
) : TestRule {

    private val scenarios = mutableListOf<ActivityScenario<out Activity>>()

    lateinit var context: Context
        private set

    lateinit var exportsDir: File
        private set

    @Volatile
    var currentPhase: String = "IDLE"
        private set

    /**
     * Launches an Activity with an explicit [intent] and immediately registers the resulting
     * [ActivityScenario] for outer-rule teardown after the inner Compose rule finishes.
     */
    fun <A : Activity> launch(intent: Intent): ActivityScenario<A> {
        val scenario = ActivityScenario.launch<A>(intent)
        scenarios.add(scenario)
        return scenario
    }

    /**
     * Launches an Activity by class and immediately registers the resulting
     * [ActivityScenario] for outer-rule teardown after the inner Compose rule finishes.
     */
    fun <A : Activity> launch(activityClass: Class<A>): ActivityScenario<A> {
        val scenario = ActivityScenario.launch(activityClass)
        scenarios.add(scenario)
        return scenario
    }

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val testName = "${description.className}#${description.methodName}"
                val startNs = System.nanoTime()
                val prevFetcher = CadGitHubUpdateRepository.defaultFetcherOverrideForTesting
                val prevTimerEnabled = CadDeckIdleTimerState.timerDriverEnabledForTesting
                var initializedContext: Context? = null
                var initializedExportsDir: File? = null
                var primaryError: Throwable? = null

                currentPhase = "SETUP"
                println("[ScenarioOwner] PHASE=SETUP_START test=$testName thread=${Thread.currentThread().name}")
                try {
                    resetFileProviderCache()
                    val appContext = ApplicationProvider.getApplicationContext<Context>()
                    context = appContext
                    initializedContext = appContext
                    val expDir = File(appContext.cacheDir, "exports").apply { mkdirs() }
                    exportsDir = expDir
                    initializedExportsDir = expDir

                    if (clearAppPreferencesAndFiles) {
                        clearLocalAppStorage(appContext)
                    }

                    CadGitHubUpdateRepository.defaultFetcherOverrideForTesting =
                        ReleaseFeedFetcher { _, _, _, _ ->
                            onReleaseFeedRequested?.invoke()
                            Result.success(null)
                        }
                    CadDeckIdleTimerState.timerDriverEnabledForTesting = false

                    currentPhase = "BODY"
                    val bodyStartNs = System.nanoTime()
                    println("[ScenarioOwner] PHASE=BODY_START test=$testName thread=${Thread.currentThread().name}")
                    base.evaluate()
                    val bodyElapsedMs = (System.nanoTime() - bodyStartNs) / 1_000_000L
                    println("[ScenarioOwner] PHASE=BODY_END status=PASSED elapsedMs=$bodyElapsedMs test=$testName")
                } catch (t: Throwable) {
                    val bodyElapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                    println(
                        "[ScenarioOwner] PHASE=BODY_END status=FAILED elapsedMs=$bodyElapsedMs " +
                            "error=${t::class.java.simpleName} test=$testName"
                    )
                    primaryError = t
                } finally {
                    currentPhase = "CLEANUP"
                    val cleanupStartNs = System.nanoTime()
                    println(
                        "[ScenarioOwner] PHASE=CLEANUP_START scenarios=${scenarios.size} " +
                            "test=$testName thread=${Thread.currentThread().name}"
                    )

                    val snapshot = scenarios.toList().asReversed()
                    scenarios.clear()
                    for ((idx, scenario) in snapshot.withIndex()) {
                        try {
                            prepareScenarioForClose(scenario)
                            scenario.close()
                            Shadows.shadowOf(Looper.getMainLooper()).idle()
                        } catch (closeErr: Throwable) {
                            println(
                                "[ScenarioOwner] PHASE=SCENARIO_CLOSE_ERROR index=$idx " +
                                    "error=${closeErr::class.java.simpleName} test=$testName"
                            )
                            if (primaryError == null) {
                                primaryError = closeErr
                            } else if (primaryError !== closeErr) {
                                primaryError.addSuppressed(closeErr)
                            }
                        }
                    }

                    try {
                        CadGitHubUpdateRepository.defaultFetcherOverrideForTesting = prevFetcher
                        CadDeckIdleTimerState.timerDriverEnabledForTesting = prevTimerEnabled
                        initializedExportsDir?.listFiles()?.forEach { it.delete() }
                        initializedContext?.let { ctx ->
                            File(ctx.filesDir, CadIncomingDocumentRouter.STAGING_DIR_NAME)
                                .listFiles()
                                ?.forEach { it.delete() }
                            if (clearAppPreferencesAndFiles) {
                                clearLocalAppStorage(ctx)
                            }
                        }
                        resetFileProviderCache()
                    } catch (fixtureErr: Throwable) {
                        if (primaryError == null) {
                            primaryError = fixtureErr
                        } else if (primaryError !== fixtureErr) {
                            primaryError.addSuppressed(fixtureErr)
                        }
                    } finally {
                        val cleanupElapsedMs = (System.nanoTime() - cleanupStartNs) / 1_000_000L
                        val totalElapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                        currentPhase = "DONE"
                        println(
                            "[ScenarioOwner] PHASE=CLEANUP_END cleanupElapsedMs=$cleanupElapsedMs " +
                                "totalElapsedMs=$totalElapsedMs test=$testName"
                        )
                    }
                }

                if (primaryError != null) {
                    throw primaryError
                }
            }
        }
    }

    private fun prepareScenarioForClose(scenario: ActivityScenario<out Activity>) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        runCatching {
            val startIntentField = ActivityScenario::class.java.getDeclaredField("startActivityIntent").apply {
                isAccessible = true
            }
            val currentActivityField = ActivityScenario::class.java.getDeclaredField("currentActivity").apply {
                isAccessible = true
            }
            val startIntent = startIntentField.get(scenario) as? Intent
            val currentActivity = currentActivityField.get(scenario) as? Activity
            if (startIntent != null && currentActivity != null && currentActivity.intent !== startIntent) {
                currentActivity.intent = startIntent
            }
        }
    }

    private fun clearLocalAppStorage(appContext: Context) {
        appContext.getSharedPreferences("precision_cad_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        appContext.getSharedPreferences("precisioncad_github_updater_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        File(appContext.filesDir, "quick_save.pcad").delete()
        File(appContext.filesDir, "quick_save").deleteRecursively()
        File(appContext.filesDir, "Documents/PrecisionCAD").deleteRecursively()
    }

    companion object {
        fun resetFileProviderCache() {
            runCatching {
                val cacheField = FileProvider::class.java.getDeclaredField("sCache").apply {
                    isAccessible = true
                }
                (cacheField.get(null) as? MutableMap<*, *>)?.clear()
            }
        }
    }
}
