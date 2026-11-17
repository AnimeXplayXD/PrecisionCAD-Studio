# PrecisionCAD: CI activity teardown stall — separate fix plan

18 November 2026 • Based on the two supplied CI archives and the previously uploaded source snapshot.

## 1. Diagnosis

The observed stall is in **Robolectric UI-test activity cleanup**, specifically `ActivityScenario.close()` inside Compose's test execution environment. This is the first defect to fix. It is not evidence that STEP support or 3D modelling caused the failure.

The likely mechanism is lifecycle/test-scheduler coordination during nested cleanup. The dumps establish where execution is stuck; they do not prove whether all assertions completed before cleanup began. Kotlin `use` also invokes cleanup when an assertion throws, so an earlier failure may currently be hidden behind the teardown stall.

### Evidence

| Artifact | Finding |
|---|---|
| `TEST-com.precisioncad.studio.CadIncomingDocumentRouterTest.xml` | 5 tests, 0 failures, 0 errors, 0 skipped; suite time 6.752 seconds. These router tests passed. |
| `ExternalCadImportRobolectricTest-20261117T212453Z-sample1.txt` | SDK 36 main/test thread blocked in `ActivityScenario.waitForActivityToBecomeAnyOf → moveToState → close → CloseableKt.closeFinally`. |
| `ExternalCadImportRobolectricTest-20261117T212520Z-sample2.txt` | Same thread and same cleanup stack approximately 27 seconds later; CPU time increased only from 7496.46 to 7496.54 ms. Consistent with waiting, not active geometry calculation. |
| Both dumps | Caller is `warm launch onNewIntent switches cleanly between same-named distinct CAD files and protects unsaved changes`, source line 343. Compose `AndroidComposeTestRule` and its coroutine test environment wrap that execution. |
| First sample resources | Approximately 13,933 MiB available memory, zero swap used and 85 GiB free disk. No evidence of host RAM/disk exhaustion at that time. This does not rule out every JVM-specific resource issue. |
| Test-results archive | Only the router XML is present, plus zero-byte in-progress/output binary files. No completed XML proves the external-import class passed or failed normally. |

The dump's test worker is waiting on Robolectric's FutureTask; other idle Gradle threads are expected downstream waiters. Start with the application-specific cleanup stack, not the first generic WAITING thread.

AndroidX's own Compose test implementation places scenario closure after its test environment exits, explaining that closing inside that environment can get stuck. The source also cautions that application-owned Compose content can still keep invalidating. This supports the cleanup-order fix but does not prove a dependency upgrade alone will solve this project.

## 2. Relevant current source

Paths are relative to the source archive previously supplied; match the failing CI commit before editing. The diagnostics do not contain a commit SHA or resolved dependency lock, so version identity must be verified rather than assumed.

| File | Relevant implementation |
|---|---|
| `app/src/test/java/com/precisioncad/studio/ExternalCadImportRobolectricTest.kt` | `createEmptyComposeRule()`, SDK 36, and multiple `ActivityScenario.launch(...).use { ... }` blocks. Line 343 matches the warm-launch stack location. |
| Same file, `tearDown()` | Restores updater/timer test globals, deletes exported/staged files and resets FileProvider cache. These operations must occur after activity/composition shutdown. |
| Same file, wait helpers | Bounded condition helpers exist. They do not bound the blocking `close()` call in `use`. |
| `app/src/main/java/com/precisioncad/studio/MainActivity.kt` | Activity owns `setContent`; keep this production path under test. |
| `app/src/test/java/com/precisioncad/studio/PrecisionCADRobolectricTest.kt` | Audit for the same lifecycle-ownership pattern, without assuming it has the same observed failure. |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` | Test dependencies and execution setup; source snapshot declares Robolectric 4.17. Capture actual resolved Compose/Test dependencies. |
| `.github/workflows/build-signed-apk.yml` | Separate filtered invocations of the same `:app:testDebugUnitTest` task, each using the same report directories; 15-minute step timeouts. |
| `scripts/ci-gradle-diagnostics.sh` | Watchdog at 720 seconds captures two dumps, separated by 25 seconds. It collects evidence but does not terminate the hung command. |

## 3. Primary fix: move scenario ownership outside Compose teardown

Add `app/src/test/java/com/precisioncad/studio/testsupport/ExternalScenarioOwnerRule.kt` (proposed new file).

Use one explicit JUnit RuleChain:

1. Outer lifecycle/fixture-owner rule.
2. Inner existing empty Compose rule.
3. Test body launches the custom-intent activity through the owner while the Compose environment is active.
4. Test body exits without closing the scenario.
5. Inner Compose rule disposes its test environment and returns.
6. Outer rule closes every scenario it owns, then restores globals and removes test fixtures.

Expose only the RuleChain as the annotated rule. Do not also annotate the inner Compose rule, which would apply it twice. The owner must register a successfully launched scenario immediately, track every scenario if a test launches several, close in reverse order and clear references. Closure must not be performed inside `scenario.onActivity`, `runOnIdle`, a main-thread callback or the Compose test body's coroutine environment.

Replace every `launch(...).use` in this class with owner-managed launch. Preserve custom ACTION_VIEW intents, source URIs, hashes and actual `MainActivity.onCreate` behaviour. Replacing everything with a default-launch activity rule could accidentally stop testing cold-launch intent delivery.

Move cleanup that must follow destruction out of the current `@After` into the outer owner: test timer/updater overrides, source/staged files and provider cache reset. Preserve their previous values rather than assuming defaults. Use safe guards for partially initialized fixtures if setup fails.

The outer statement must preserve the test-body exception and attach cleanup exceptions as suppressed errors. Emit distinct start/end markers for body and cleanup, even if cleanup stalls. Do not swallow exceptions, omit scenario closure or kill the JVM from a test to obtain a green build.

First prove this change with the warm-launch test only. Android/Robolectric APIs have platform-specific threading exceptions; the fix is explicit lifecycle ownership and ordering, not a blanket assertion that every main-thread ActivityScenario call is illegal in Robolectric.

## 4. If closure still stalls: investigate lifecycle work, not longer sleeps

Because the app calls `setContent` itself, the rule may not own all composition disposal. If the minimal ownership fix still stalls:

- Log lifecycle stages, test phase, thread name and elapsed time without logging file contents or credentials.
- Capture new dumps before and after the inner Compose rule returns. Identify which callback or idle work prevents DESTROYED.
- Inspect activity-owned composition disposal, animation/timer loops, frame callbacks and ViewModel scope cancellation. Check that resource owners stop at their correct lifecycle boundary; a ViewModel retained for configuration change must not be destroyed merely to make a test idle.
- Use an injected test clock/timer for app-owned perpetual timers where appropriate. Keep visible production animations; do not disable all motion in the app to pass CI.
- Audit looper-pumping helpers. Keep bounded state waits; do not call blocking lifecycle waits from callbacks responsible for delivering lifecycle events. Virtual coroutine time does not automatically run every Android looper or real I/O operation.
- Inspect resolved dependency versions for compatibility and reproduce a minimal custom-intent ActivityScenario plus empty Compose rule test. Change one dependency at a time only if evidence implicates it; no speculative mass upgrades/downgrades.

Separate pure import/ViewModel tests from UI lifecycle assertions where that reduces unnecessary Compose setup. Keep cold launch, warm launch, dirty-document dialog, actual content display and recreation coverage in UI tests. If a proven Robolectric-specific limitation remains, run equivalent mandatory device/emulator instrumentation coverage; do not simply delete or ignore the failing tests.

## 5. Preserve the original regression coverage

The warm-launch test currently checks important behaviour. Keep all of it:

- Open first DXF and wait for its exact hash.
- Send a second, same-named DXF with different bytes and verify updated geometry/hash.
- Make unsaved edits.
- Send PCAD over generic octet-stream and verify the unsaved-change prompt.
- Delete the external source after staging, then confirm discard-and-continue.
- Verify the new project loads from its private staged copy with the expected coordinates/hash.

Do not change staging or unsaved-change production logic unless a completed test exposes a separate defect. The existing STEP-rejection tests are correct for the current 2D source. Update them only in the separate 3D feature implementation when STEP becomes genuinely supported.

## 6. CI reporting fix: retain each class's own results

The workflow repeatedly runs the same Gradle task with different `--tests` filters. Each invocation writes to the same results/report paths. The final upload therefore cannot reliably represent all prior classes, especially when the last invocation is interrupted. The provided archive demonstrates incomplete evidence; it does not prove an earlier class failed.

Keep filtered steps for diagnosis, but add a small wrapper or explicit collection logic that:

1. Uses a safe fixed class label for a unique destination under runner temporary storage.
2. Removes only this task's stale report/results directories before starting the class, after any previous results have been archived.
3. Runs Gradle, captures its real exit code and collects current XML, HTML and a sanitized step log into that class destination before returning the same status.
4. Writes a small manifest with class, commit, start/end times, completion status and available report names. Mark a killed/incomplete invocation as incomplete, never passed.
5. Uploads the aggregate per-class directories with `if: always()`. Include run attempt in names when artifacts could collide.

An ordinary trap may not run after a hard kill, so also provide a workflow-level always-run fallback collector for the current task and its phase metadata. Retain previous archived class reports untouched. Do not use `continue-on-error` to hide required failures. Missing expected class XML must not be interpreted as success.

## 7. Bound hangs and improve the existing watchdog

The current watchdog did useful work: both dumps identify the stall. Keep it, with these refinements:

- During diagnosis, capture earlier (for example after 90–120 seconds) and again 20–30 seconds later; tune thresholds from measured normal runtime afterward.
- Keep a larger independent hard deadline that fails the command and leaves time before the GitHub step timeout for diagnostics and upload. Do not rely on a coroutine timeout to interrupt a native/blocking lifecycle wait.
- Bound `jcmd`/`jstack` calls so diagnostic attachment cannot itself hang indefinitely.
- Track the wrapped command's owned process group/descendants. On cancellation or hard deadline, stop that owned tree and the watchdog, wait for exit, and retain a nonzero/cancellation status. Do not use broad `pkill java`.
- Current `cleanup_watchdog` stops the watchdog, not the Gradle command. Review signal forwarding explicitly.
- Current watchdog output is redirected to `/dev/null`, including warning annotations. Keep diagnostics in files but send concise phase/timeout notices through a visible logging path.
- Record the running class/method and body/cleanup phase. Do not dump environment variables, signing arguments or tokens.

These changes improve failure visibility and duration; they do not repair lifecycle ordering by themselves.

## 8. Validation and implementation sequence

1. Confirm source commit and capture Gradle dependency resolution for Compose UI tests, AndroidX Test, coroutine-test and Robolectric.
2. Reproduce only the named warm-launch test with a bounded CI/local command and phase markers.
3. Implement the outer owner rule and cleanup ordering. Re-run that test until it exits normally, not merely until body assertions are observed.
4. Run the warm-launch test 20 times serially with actual test execution (`--rerun-tasks` or equivalent); no cached/up-to-date pass counts as a repetition.
5. Run the complete external-import class, then the router class and existing Robolectric suite. Verify each has fresh completed XML.
6. Add a small harness regression that intentionally throws inside a scenario-owning body and verifies cleanup preserves the original failure and completes. Run it as an expected-failure harness check, not an always-red production test.
7. Test cancellation and a deliberately blocked throwaway command against the watchdog; verify bounded failure, correct exit status and retained diagnostics.
8. Run the normal workflow including required lint/build steps and archive all class outputs separately.

Suggested focused command, executed from the repository root:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.precisioncad.studio.ExternalCadImportRobolectricTest.*warm launch*' --rerun-tasks --no-daemon --no-parallel --max-workers=2 --no-configuration-cache --console=plain --info --stacktrace
```

Confirm the filter actually matches and executes the intended test; use the exact full method filter if required by the resolved Gradle version. Apply the external hard timeout described above.

Acceptance: the original assertions remain, cleanup reaches completion, fresh XML contains all expected tests, repeated runs do not stall, no activity/test-resource leak persists between tests, cancellation preserves diagnostics, and required workflow checks pass. Set a runtime budget from measured successful runs rather than asserting an unmeasured universal duration.

## 9. AI-agent task boundaries

Implement a dedicated test/CI repair change. Primary files: the external-import test, new scenario-owner support rule, workflow report collection and watchdog script. Audit the other Robolectric test for the same pattern. Change production lifecycle code or dependency versions only if the next diagnostic/reproduction demonstrates they are necessary.

Do not merge this with the 3D engine, control-pad, dimension or card-animation work. Do not add sleeps, increase timeouts as the sole fix, remove animations, skip tests or report incomplete XML as passing. Report test body and cleanup results separately during diagnosis. No fixes have been executed in this review; this is an evidence-grounded implementation plan.

## References

- Supplied archives: `unit-test-results-and-reports.zip` and `precisioncad-ci-diagnostics.zip`.
- Previously uploaded PrecisionCAD source; verify against the failing commit.
- [AndroidX Compose test lifecycle implementation](https://android.googlesource.com/platform/frameworks/support/+/a95ab06e2d6ce8f2ae6a7e9c3c0073ed8fd0f96b/compose/ui/ui-test/src/androidMain/kotlin/androidx/compose/ui/test/ComposeUiTest.android.kt): scenario cleanup ordering and its limitations. This is supporting upstream evidence, not proof that the failing build resolves that exact revision.
- [ActivityScenario API](https://developer.android.com/reference/androidx/test/core/app/ActivityScenario): lifecycle/cleanup and Robolectric-specific threading notes.
