# PrecisionCAD Studio — source-by-source GitHub Actions stall remediation plan

**Analysis date:** 2026-11-17  
**Source of truth:** user-supplied PrecisionCAD Studio ZIP, inspected locally.  
**Affected GitHub Actions run:** `44074955185`, job `134010471463`, commit shown as `e6d5c6b`.  
**Scope:** CI/test/lint stability for **PrecisionCAD Studio only**. Do not change application CAD behavior, package ID, published APK names, production keystore identity, certificate validation, versioning, or release gates.

## Evidence and degree of certainty

- **Confirmed:** `.github/workflows/build-signed-apk.yml:336-344` runs `:app:testDebugUnitTest` and `:app:lintRelease` in **one Gradle invocation**. Its GitHub job step remains `in_progress`, and subsequent APK assembly steps have not begun.
- **Confirmed:** the pasted log shows both `> Task :app:testDebugUnitTest` and `> Task :app:lintRelease`, with a lint HTML report written, but **no** `BUILD SUCCESSFUL`, `BUILD FAILED`, or process-exit message.
- **Not established:** the exact blocked thread. Seeing `> Task :app:lintRelease` last does **not** establish that lint is the blocked task. The task log is not a per-task completion ledger.
- **Strong candidate:** large Robolectric/Compose UI tests that repeatedly call `waitForIdle()`, combined with app-initiated background work and repeating animation timers.
- **Other candidates:** Gradle test worker cannot exit, lint/Gradle worker deadlock, or resource exhaustion. Confirm by separating executions and collecting JVM thread dumps before changing application logic.
- **Not causal evidence:** seven Kotlin redundant-cast/null assertion/conversion warnings; two deprecated Compose test-rule warnings. Cleanup is useful but secondary.
- **Limitation:** direct job log download returned a 404 while the run was live; a complete log and thread dump were not available. The project was inspected statically, not executed against the Android SDK in this environment.

## Phase 0 — first diagnostic CI run (priority P0)

### 1. `.github/workflows/build-signed-apk.yml` (lines 336–344): separate work and identify the blocking test

**Before:** one step named `Run Unit Tests & Release Lint Before Assembly` runs:

```bash
./gradlew :app:testDebugUnitTest :app:lintRelease --no-daemon --stacktrace
```

**Implement:**

1. Replace the combined step with separately named **serial** steps for (a) unit tests and (b) release lint. Keep the same release-signing environment variables already passed to the combined step. Run test first and lint second; both must succeed before assembly. Do **not** use `continue-on-error`.
2. During the diagnostic run, divide unit tests by their actual four classes so that a stalled class can be identified:

   ```bash
   ./gradlew :app:testDebugUnitTest --tests 'com.precisioncad.studio.PrecisionCADUnitTest' --no-daemon --no-parallel --max-workers=2 --no-configuration-cache --console=plain --info --stacktrace
   ./gradlew :app:testDebugUnitTest --tests 'com.precisioncad.studio.CadIncomingDocumentRouterTest' --no-daemon --no-parallel --max-workers=2 --no-configuration-cache --console=plain --info --stacktrace
   ./gradlew :app:testDebugUnitTest --tests 'com.precisioncad.studio.ExternalCadImportRobolectricTest' --no-daemon --no-parallel --max-workers=2 --no-configuration-cache --console=plain --info --stacktrace
   ./gradlew :app:testDebugUnitTest --tests 'com.precisioncad.studio.PrecisionCADRobolectricTest' --no-daemon --no-parallel --max-workers=2 --no-configuration-cache --console=plain --info --stacktrace
   ```

   Use **separate Actions steps**, not four commands chained into one step, so the UI identifies exactly which group is blocked. These class filters together cover the four unit-test classes present in the uploaded archive. Confirm coverage if new classes are added before implementing.
3. Give each group `timeout-minutes: 15` (or a measured generous limit), the lint step `timeout-minutes: 15`, and the job an overall limit (e.g. `timeout-minutes: 60`). A GitHub timeout prevents infinite CI consumption; it is **not** itself a root-cause fix.
4. Preserve `--no-daemon` and `--stacktrace`; add `--console=plain --info` for visible test method progress. `--no-parallel` and `--no-configuration-cache` are **diagnostic**, not proof that those Gradle features are faulty.
5. Make report uploads `if: always()` so test XML, HTML, lint output, and debug diagnostics are uploaded even when one step fails or times out. Retain current upload/sign/publish ordering, production signing checks, and release artifact names.
6. During initial triage, keep release publication disabled on a manual workflow run to avoid publishing before the verified pipeline passes. On `push`, retain the existing fail-closed gate.
7. After isolating and repairing the culprit, consolidate pure unit tests into one invocation and keep heavy UI integration tests in their own step. Keep lint separate.

**Success criterion:** Every build visibly reports which class is executing; a blocked class is contained and times out with evidence; no APK assembly occurs until test and lint gates pass.

### 2. `app/build.gradle.kts` (lines 133–155): make the Gradle `Test` tasks observable

**Current:** `android.testOptions.unitTests.all { testTask -> ... }` configures JVM `--add-opens` and a path-specific Robolectric offline cache, but not test-level logging or fork isolation.

**Add test-task configuration** (inside the existing `all` block, without deleting the `jvmArgs` and cache logic):

```kotlin
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
// imports belong at the beginning of app/build.gradle.kts

// Inside unitTests.all { testTask -> ... }:
testTask.maxParallelForks = 1
testTask.testLogging {
    events(TestLogEvent.STARTED, TestLogEvent.PASSED,
           TestLogEvent.FAILED, TestLogEvent.SKIPPED)
    exceptionFormat = TestExceptionFormat.FULL
    showStandardStreams = true
}
```

**Optional diagnosis only:** `testTask.forkEvery = 1` for strict process isolation if a previous class contaminates later tests; revert after investigation because Robolectric process startup is expensive. Consider limiting per-worker heap **after** measuring actual memory consumption. Do not set `ignoreFailures = true`.

**Success criterion:** The log includes the full class and method being run immediately before a stall. Gradle still fails on test failures.

### 3. `gradle.properties` (lines 12–29): temporarily reduce concurrency for reproducibility

**Current:** `org.gradle.parallel=true`, `org.gradle.workers.max=4`, `org.gradle.configuration-cache=true`, Gradle process heap `-Xmx4g`, and Kotlin in-process compilation.

**Implement:** Prefer diagnostics scoped to CI command-line flags (`--no-parallel --max-workers=2 --no-configuration-cache`) rather than permanently degrading local development. If the stall disappears, reintroduce these one at a time and compare repeat runs. Record CPU/RAM before deciding whether to lower the 4 GiB Gradle daemon heap. Leave the JVM toolchain and SDK targeting unchanged.

**Success criterion:** The same tests complete predictably in isolation, then with normal caching/concurrency restored; any problematic flag is supported by repeatable evidence.

### 4. `scripts/ci-gradle-diagnostics.sh` (**new**, optional but strongly recommended)

Create a non-secret diagnostics helper to be called **while** a Gradle step is still running, ideally by a watchdog that captures data shortly *before* its timeout:

- Capture `date -u`, `free -m`, `df -h`, `ps -eo pid,ppid,stat,etime,%cpu,%mem,args`, `jcmd -l`, and `jstack`/`jcmd <pid> Thread.print -l` for Java processes identifiable as the Gradle daemon or test worker. Collect a sample twice (e.g., 20–30 seconds apart) to distinguish a live worker from a deadlock.
- Log only process **names/PIDs** and thread states; do **not** dump the environment, keystore passwords, secrets, signed keystores, or entire Java command lines if they contain credentials. Sanitize `ps` arguments as needed. Use `jcmd` only for owned runner processes.
- Save diagnostics under `${RUNNER_TEMP}/precisioncad-ci-diagnostics/` and upload with `if: always()` and `retention-days: 7`.
- Use an actual execution watchdog to trigger diagnostics before termination. A step-level timeout alone may terminate the runner step before its cleanup trap can create a dump.

**Success criterion:** Timeout produces sufficient evidence to determine whether the blocked thread is in `Gradle Test Executor`, Compose idling, Robolectric, network I/O, or Android Lint.

## Phase 1 — focused fixes to tests / production testability (priority P1, conditional on Phase 0)

### 5. `app/src/test/java/com/precisioncad/studio/PrecisionCADRobolectricTest.kt` (lines 37–45 and 50–228)

**Current:** one unusually long test exercises first-run Home, release catalogue, virtual controller mode, command drawer, previews, coordinate editor, QDIM, file saves, navigation and dialogs; it repeatedly invokes `composeTestRule.waitForIdle()`. The rule on line 42 uses the deprecated Compose v1 `createAndroidComposeRule`.

**Implement:**

1. Split the single scenario into independent tests grouped by behavior (Home/updates; controller mode; command preview; coordinate editor; QDIM; file save/restore). Each must arrange its own initial state and not depend on the previous test.
2. Add focused progress markers or per-scenario test names. Use bounded condition-based synchronization (`waitUntil(timeoutMillis = ..., condition = { ... })`) for asynchronous state, rather than assuming every coroutine is finished after `waitForIdle()`.
3. Ensure every test destroys/closes its ActivityScenario and that saved prefs/files are reset intentionally; preserve the production launch path.
4. Migrate to `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule` **as a separately validated change**. Android's v2 APIs use `StandardTestDispatcher`; tests depending on immediate coroutine execution may need explicit scheduling. Avoid combining the migration with broad behavior rewrites.
5. If the activity has ongoing timers/animations, disable them through a test-only injected clock/driver, not a global production behavior change.

**Success criterion:** No single UI test hides unrelated scenarios, all UI tests finish repeatedly, and behavior assertions remain equivalent or stronger.

### 6. `app/src/test/java/com/precisioncad/studio/ExternalCadImportRobolectricTest.kt` (lines 47–69, 146–152, 252–436)

**Current:** `createEmptyComposeRule()` v1, helper `drainMainAndIdle()` invokes `Looper.idle()`, `composeRule.waitForIdle()`, `Thread.sleep(150)`, `Looper.idle()`, `waitForIdle()`; multiple tests launch/recreate `MainActivity`, deliver intents, and import on I/O coroutines.

**Implement:**

1. Replace the fixed `Thread.sleep(150)` with deterministic **state-based, bounded waits** for `CadIncomingImportState.Ready`, expected document revision, or expected error dialog; explicitly synchronize UI and background I/O rather than hoping 150 ms is enough.
2. Keep `ActivityScenario.use {}` lifecycle closure; ensure cleanup is performed even on test failure.
3. Migrate `createEmptyComposeRule` to `androidx.compose.ui.test.junit4.v2.createEmptyComposeRule` as a validated separate step, accounting for queued coroutine execution.
4. Inject a test dispatcher/import worker or await state transitions so `Dispatchers.IO` does not race assertions. Keep tests covering cold launch, warm intent, content URI, saved state recovery and unsupported format.

**Success criterion:** Tests pass without `Thread.sleep` and without relying on external network access or arbitrary timing.

### 7. `app/src/test/java/com/precisioncad/studio/CadIncomingDocumentRouterTest.kt` (lines 216 and 259)

**Current:** the staging tests use `runTest {}` while production code performs `withContext(Dispatchers.IO)` and file/content-provider operations. An uncontrolled real dispatcher can make virtual-time assumptions unreliable.

**Implement:** If this class is shown as stalled/flaky, inject the router's I/O dispatcher for tests, use deterministic fixtures, and set explicit bounded coroutine-test timeouts. Preserve all rejection and SHA-256 validation cases; do not remove security-related validations or lower file-size limits.

**Success criterion:** Repeated import tests terminate and their content/security assertions remain intact.

### 8. `app/src/main/java/com/precisioncad/studio/ui/components/CadBinaryToggleCard.kt` (lines 440–474 and callers at 534, 995, 1229)

**Current:** a `LaunchedEffect` runs `while (state.isEligibleForTimer)` with recurring `delay(state.idleIntervalMs)` and `delay(state.idlePeekHoldMs)`; transition/peek animation state feeds UI rendering.

**Implement only if Compose idleness is the culprit:**

1. Provide an injectable timer driver / test CompositionLocal or an explicit test-only dependency so Robolectric scenarios can use a controllable clock, pause the idle-peek timer, and advance animation states deterministically.
2. Preserve the production **60-second idle-peek and 4-second peek behavior**, lifecycle pause/resume rules, manual card exchange, and reduced-motion functionality.
3. Add focused tests for timer eligibility and cancellation and verify `LaunchedEffect` cancels on disposal.

**Success criterion:** Compose tests have a known idle state and production animations behave identically.

### 9. `app/src/main/java/com/precisioncad/studio/ui/CadWorkspaceViewModel.kt` (lines 300–306, 364–375, 602+)

**Current:** ViewModel initialization schedules Room cleanup and **automatically calls** `updateRepository.checkForUpdates(manual = false)`. Quick save is a 250-ms coroutine; import dispatch uses `viewModelScope.launch`.

**Implement:** Add injectable updater/repository and dispatcher dependencies (or a small factory abstraction) so tests can supply a fake updater and deterministic coroutine dispatchers. Do **not** remove the automatic update check from production. Tests should be able to finish without performing real GitHub API requests, while still testing that a startup check is requested.

**Success criterion:** UI/import tests do not depend on network status and app startup/update behavior remains unchanged in production.

### 10. `app/src/main/java/com/precisioncad/studio/cad/update/CadAppUpdater.kt` (lines 933–990)

**Current:** `checkForUpdates` launches work and switches to `Dispatchers.IO`, and HTTP calls use bounded connect and read timeouts (10s and 15s). These timeouts limit a single request but are not deterministic for tests.

**Implement:** Introduce an interface or transport seam to mock `fetchReleasesFromGitHub()` in tests, including success, HTTP failure, offline and not-modified cases. Preserve the actual HTTP timeouts and existing cached-catalogue fallback for app users.

**Success criterion:** CI never needs live GitHub API availability to execute Robolectric view tests.

## Phase 2 — lint workflow and documentation (priority P2)

### 11. `.github/workflows/lint.yml` (lines 61–110)

**Current:** separate lint-only workflow already runs `:app:lintRelease` but is triggered by every push and PR, as is the signed-APK workflow. The same lint checks are therefore run in separate jobs, wasting CI resources.

**Implement:**

1. Apply its own `timeout-minutes: 15`, `--no-parallel --max-workers=2 --console=plain --info --stacktrace` (and temporarily `--no-configuration-cache` for diagnostic parity).
2. Keep its **throwaway keystore** restricted to lint; never substitute it for production release signing.
3. Choose **one** non-duplicated architecture after diagnosis: (A) run lint within the signed workflow, and restrict the standalone lint workflow to PR-only checks, or (B) integrate a dedicated `lint` job in the APK workflow with an explicit `needs:` gating the assembly job. Do not attempt `needs:` directly across two different workflow files; GitHub Actions does not support that.
4. Retain HTML/XML/TXT uploads even on lint failure.

**Success criterion:** An independent `lintRelease` run shows its own success/failure unambiguously; no redundant lint on routine release pushes once the chosen CI topology is deployed.

### 12. `docs/BUILD_AND_RELEASE.md` (CI sections 3–4)

Document the separated tests/lint gates, exact CI commands, timeouts, diagnostics artifact locations, expected report paths, production signing safeguards, and the process for reproducing a stuck class locally.

### 13. Kotlin compiler warnings (separate cleanup; not a stall fix)

Once the build is stable, remove redundant constructs while preserving behavior in:

- `cad/io/CadDxfInterchange.kt:1636`, `cad/io/CadIncomingDocumentRouter.kt:358`, `cad/io/CadPcadPersistence.kt:908`
- `cad/kernel/CadGeometryKernel.kt:509`, `cad/kernel/CadModificationEngine.kt:70`
- `ui/CadWorkspaceScreen.kt:4029`, `ui/components/CadDialogsAndPanels.kt:3654`

Do **not** put this cosmetic pass in the first debugging commit. Deprecated Compose test rules are addressed independently above.

## Decision tree for the next run

| Observation | Conclusion / action |
|---|---|
| Standalone lint succeeds, one Robolectric class stalls | Fix that test class, its scheduler, animation, updater, or background worker. |
| Unit tests complete, standalone lint stalls | Inspect lint JVM/thread dump, try disabling configuration cache and work parallelism only for lint, and check Gradle/AGP-specific behavior. |
| Both individually succeed but combined fails | Resource contention or concurrent task/worker interaction; keep steps separate, compare `--no-parallel`/worker count and memory. |
| Both finish but shell doesn't exit | Investigate remaining non-daemon thread/worker processes via `jcmd` and process tree. |
| Test fails with assertion/exception rather than hangs | Fix the actual failing assertion/logic; do not suppress the test or swallow the error. |
| A runner OOM or disk error appears | Gather runner metrics and adjust heap/workers and cache strategy using measured evidence. |

## Final verification / acceptance checklist

- [ ] All four diagnostic test classes complete within their timeouts, then pass again on a fresh runner.
- [ ] `:app:lintRelease` succeeds in its own CI step and emits XML/HTML/TXT reports.
- [ ] At least three repeat CI runs succeed, including a **clean-cache** run and a run with normal caching settings restored.
- [ ] Tests still fail the workflow on assertion failures; lint errors are not hidden by `abortOnError=false` or `continue-on-error`.
- [ ] Timeout yields useful thread/process diagnostics and the job fails promptly rather than running for hours.
- [ ] `:app:assembleRelease`, APK signature/certificate validation, package identity/version checks, and all five signing secret requirements remain unchanged and fail-closed.
- [ ] No production APK is built/published until both test and lint gates pass.
- [ ] `docs/BUILD_AND_RELEASE.md` matches the implemented CI pipeline.

## Recommended implementation order / commit scope

**Commit A (observability only):** modify `.github/workflows/build-signed-apk.yml`, `app/build.gradle.kts`, optionally create `scripts/ci-gradle-diagnostics.sh`; upload complete test reports. Run a manual no-publish CI invocation.  
**Commit B (the real bug):** modify **only** the test(s) and minimal production testability seams identified by Commit A's evidence. Re-run class-isolated tests.  
**Commit C (harden/optimize):** refine `.github/workflows/lint.yml`, choose final CI topology, restore cache/concurrency after validation, and update docs.  
**Commit D (optional):** address 7 Kotlin warnings and deprecations separately.

**Do not** delete `lintRelease`, skip the slow UI tests, set `continue-on-error: true`, change production signing keys, publish from a failing job, or treat an increased GitHub Actions timeout as a fix.
