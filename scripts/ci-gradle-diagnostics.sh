#!/usr/bin/env bash
set -uo pipefail

# Non-secret CI diagnostics, hard-deadline watchdog, and per-class report archiver for Gradle steps.
# - Captures two spaced JVM thread dumps after WATCHDOG_SECONDS (default 120s).
# - Enforces an independent HARD_TIMEOUT_SECONDS (default 780s = 13m) by terminating only the
#   wrapped command's owned process tree so step-level reports and diagnostics can still upload.
# - Bounds jcmd/jstack invocations with `timeout 15s` so a stuck JVM cannot freeze diagnostics.
# - When --class-fqcn is provided, cleans stale Gradle test outputs before running, then archives
#   that class's XML, HTML, sanitized step log, and status manifest into a dedicated per-class folder
#   under ${RUNNER_TEMP:-/tmp}/precisioncad-test-archives/<label>/.

WATCHDOG_SECONDS=120
SECOND_SAMPLE_DELAY_SECONDS=25
HARD_TIMEOUT_SECONDS=780
LABEL="gradle-step"
CLASS_FQCN=""
SNAPSHOT_NOW="false"
FINALIZE_INCOMPLETE="false"
OUT_DIR="${RUNNER_TEMP:-/tmp}/precisioncad-ci-diagnostics"
ARCHIVE_ROOT="${RUNNER_TEMP:-/tmp}/precisioncad-test-archives"
STATE_FILE="${OUT_DIR}/current-active-step.env"

while [ $# -gt 0 ]; do
  case "$1" in
    --watchdog-seconds)
      WATCHDOG_SECONDS="${2:-120}"
      shift 2
      ;;
    --second-sample-delay)
      SECOND_SAMPLE_DELAY_SECONDS="${2:-25}"
      shift 2
      ;;
    --hard-timeout-seconds)
      HARD_TIMEOUT_SECONDS="${2:-780}"
      shift 2
      ;;
    --label)
      LABEL="${2:-gradle-step}"
      shift 2
      ;;
    --class-fqcn)
      CLASS_FQCN="${2:-}"
      shift 2
      ;;
    --snapshot-now)
      SNAPSHOT_NOW="true"
      if [ $# -ge 2 ] && [[ "${2:-}" != --* ]]; then
        LABEL="$2"
        shift 2
      else
        shift 1
      fi
      ;;
    --finalize-incomplete)
      FINALIZE_INCOMPLETE="true"
      shift 1
      ;;
    --)
      shift
      break
      ;;
    *)
      break
      ;;
  esac
done

mkdir -p "$OUT_DIR" "$ARCHIVE_ROOT"

run_bounded() {
  local sec="$1"
  shift
  if command -v timeout >/dev/null 2>&1; then
    timeout "${sec}s" "$@"
  else
    "$@"
  fi
}

terminate_owned_tree() {
  local root_pid="$1"
  local sig="${2:-TERM}"
  if [ -z "$root_pid" ]; then
    return 0
  fi
  local children
  children=$(pgrep -P "$root_pid" 2>/dev/null || true)
  for child in $children; do
    terminate_owned_tree "$child" "$sig"
  done
  if kill -0 "$root_pid" 2>/dev/null; then
    kill -s "$sig" "$root_pid" 2>/dev/null || true
  fi
}

extract_last_phase_markers() {
  local log_file="$1"
  if [ -n "$log_file" ] && [ -f "$log_file" ]; then
    grep -E "\[ScenarioOwner\]| > .* (STARTED|PASSED|FAILED|SKIPPED)" "$log_file" 2>/dev/null | tail -n 25 || true
  fi
}

capture_sample() {
  local sample_tag="$1"
  local step_log="${2:-}"
  local ts
  ts=$(date -u +"%Y%m%dT%H%M%SZ")
  local out_file="${OUT_DIR}/${LABEL}-${ts}-${sample_tag}.txt"

  {
    echo "=== PrecisionCAD CI Diagnostics (${LABEL} / ${sample_tag}) ==="
    echo "UTC Timestamp: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    echo "Class Filter: ${CLASS_FQCN:-none}"
    echo ""

    if [ -n "$step_log" ] && [ -f "$step_log" ]; then
      echo "--- Recent Test & ScenarioOwner Phase Markers ---"
      extract_last_phase_markers "$step_log"
      echo ""
    fi

    if command -v free >/dev/null 2>&1; then
      echo "--- Memory (free -m) ---"
      free -m || true
      echo ""
    fi

    if command -v df >/dev/null 2>&1; then
      echo "--- Disk (df -h) ---"
      df -h || true
      echo ""
    fi

    echo "--- Processes (comm only, no args/secrets) ---"
    ps -eo pid,ppid,stat,etime,%cpu,%mem,comm 2>/dev/null || true
    echo ""

    if command -v jcmd >/dev/null 2>&1; then
      echo "--- Owned JVM Processes (PID + main class only) ---"
      run_bounded 10 jcmd -l 2>/dev/null | awk '{print $1, $2}' || true
      echo ""

      local java_pids
      java_pids=$(run_bounded 10 jcmd -l 2>/dev/null | awk '/Gradle|Worker|Kotlin|lint|Precision|Main/ {print $1}')
      for pid in $java_pids; do
        local main_cls
        main_cls=$(run_bounded 5 jcmd -l 2>/dev/null | awk -v p="$pid" '$1 == p {print $2; exit}')
        echo "=== Thread.print for PID ${pid} (${main_cls:-unknown}) ==="
        run_bounded 15 jcmd "$pid" Thread.print -l 2>&1 || echo "[WARN] jcmd Thread.print timed out or failed for PID ${pid}"
        echo ""
      done
    elif command -v jstack >/dev/null 2>&1; then
      local pids
      pids=$(ps -eo pid,comm 2>/dev/null | awk '/java/ {print $1}')
      for pid in $pids; do
        echo "=== jstack for PID ${pid} ==="
        run_bounded 15 jstack -l "$pid" 2>&1 || echo "[WARN] jstack timed out or failed for PID ${pid}"
        echo ""
      done
    fi
  } >"$out_file" 2>&1
}

write_class_manifest_and_collect() {
  local status="$1"
  local exit_code="$2"
  local start_utc="$3"
  local end_utc="$4"
  local step_log="$5"
  local dest_dir="${ARCHIVE_ROOT}/${LABEL}"
  mkdir -p "$dest_dir/xml" "$dest_dir/html"

  local xml_copied=0
  if [ -d "app/build/test-results/testDebugUnitTest" ]; then
    while IFS= read -r -d '' xml_file; do
      cp -f "$xml_file" "$dest_dir/xml/" 2>/dev/null && xml_copied=$((xml_copied + 1))
    done < <(find "app/build/test-results/testDebugUnitTest" -maxdepth 1 -name "TEST-*.xml" -print0 2>/dev/null)
  fi

  if [ -d "app/build/reports/tests/testDebugUnitTest" ]; then
    cp -Rf app/build/reports/tests/testDebugUnitTest/. "$dest_dir/html/" 2>/dev/null || true
  fi

  if [ -n "$step_log" ] && [ -f "$step_log" ]; then
    cp -f "$step_log" "$dest_dir/${LABEL}-step.log" 2>/dev/null || true
    extract_last_phase_markers "$step_log" > "$dest_dir/phase-markers.txt" 2>/dev/null || true
  fi

  local expected_xml_present="true"
  if [ -n "$CLASS_FQCN" ]; then
    local expected_xml="$dest_dir/xml/TEST-${CLASS_FQCN}.xml"
    if [ ! -s "$expected_xml" ]; then
      expected_xml_present="false"
    fi
  fi

  local commit_sha
  commit_sha=$(git rev-parse --short=12 HEAD 2>/dev/null || echo "${GITHUB_SHA:-unknown}")

  cat > "$dest_dir/manifest.json" <<EOF
{
  "label": "${LABEL}",
  "classFqcn": "${CLASS_FQCN}",
  "commitSha": "${commit_sha}",
  "startUtc": "${start_utc}",
  "endUtc": "${end_utc}",
  "status": "${status}",
  "exitCode": ${exit_code},
  "xmlReportCount": ${xml_copied},
  "expectedClassXmlPresent": ${expected_xml_present}
}
EOF
}

if [ "$SNAPSHOT_NOW" = "true" ]; then
  capture_sample "manual" ""
  exit 0
fi

if [ "$FINALIZE_INCOMPLETE" = "true" ]; then
  if [ -f "$STATE_FILE" ]; then
    # shellcheck disable=SC1090
    source "$STATE_FILE"
    LABEL="${ACTIVE_LABEL:-interrupted-step}"
    CLASS_FQCN="${ACTIVE_CLASS_FQCN:-}"
    echo "::warning::[CI Watchdog] Finalizing interrupted step '${LABEL}' (${CLASS_FQCN:-no-class}) as INCOMPLETE." >&2
    capture_sample "fallback-interrupted" "${ACTIVE_STEP_LOG:-}"
    write_class_manifest_and_collect \
      "INCOMPLETE" \
      "137" \
      "${ACTIVE_START_UTC:-unknown}" \
      "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" \
      "${ACTIVE_STEP_LOG:-}"
    rm -f "$STATE_FILE"
  fi
  exit 0
fi

if [ $# -eq 0 ]; then
  echo "Usage: $0 [--watchdog-seconds N] [--hard-timeout-seconds M] [--label NAME] [--class-fqcn FQCN] -- <command...>" >&2
  exit 2
fi

START_UTC=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
STEP_LOG="${OUT_DIR}/${LABEL}-live.log"
rm -f "$STEP_LOG" "${OUT_DIR}/${LABEL}.timed_out"

if [ -n "$CLASS_FQCN" ]; then
  rm -rf "app/build/test-results/testDebugUnitTest" "app/build/reports/tests/testDebugUnitTest"
fi

cat > "$STATE_FILE" <<EOF
ACTIVE_LABEL="${LABEL}"
ACTIVE_CLASS_FQCN="${CLASS_FQCN}"
ACTIVE_START_UTC="${START_UTC}"
ACTIVE_STEP_LOG="${STEP_LOG}"
EOF

# Stream wrapped command stdout/stderr both to console and to sanitized live log file
"$@" > >(tee -a "$STEP_LOG") 2> >(tee -a "$STEP_LOG" >&2) &
CMD_PID=$!

(
  elapsed=0
  interval=5
  dumped_sample1="false"
  dumped_sample2="false"
  sample2_at=$((WATCHDOG_SECONDS + SECOND_SAMPLE_DELAY_SECONDS))

  while kill -0 "$CMD_PID" 2>/dev/null; do
    sleep "$interval"
    elapsed=$((elapsed + interval))

    if [ "$dumped_sample1" = "false" ] && [ "$elapsed" -ge "$WATCHDOG_SECONDS" ]; then
      dumped_sample1="true"
      echo "::warning::[${LABEL}] Command active after ${elapsed}s; capturing watchdog JVM diagnostics (sample 1/2)." >&2
      capture_sample "sample1" "$STEP_LOG"
    fi

    if [ "$dumped_sample1" = "true" ] && [ "$dumped_sample2" = "false" ] && [ "$elapsed" -ge "$sample2_at" ]; then
      dumped_sample2="true"
      if kill -0 "$CMD_PID" 2>/dev/null; then
        echo "::warning::[${LABEL}] Command active after ${elapsed}s; capturing watchdog JVM diagnostics (sample 2/2)." >&2
        capture_sample "sample2" "$STEP_LOG"
      fi
    fi

    if [ "$HARD_TIMEOUT_SECONDS" -gt 0 ] && [ "$elapsed" -ge "$HARD_TIMEOUT_SECONDS" ]; then
      if kill -0 "$CMD_PID" 2>/dev/null; then
        echo "::error::[${LABEL}] Hard deadline (${HARD_TIMEOUT_SECONDS}s) reached; capturing pre-kill dump and terminating owned process tree (PID ${CMD_PID})." >&2
        touch "${OUT_DIR}/${LABEL}.timed_out"
        capture_sample "hard-timeout" "$STEP_LOG"
        terminate_owned_tree "$CMD_PID" TERM
        sleep 5
        terminate_owned_tree "$CMD_PID" KILL
      fi
      exit 0
    fi
  done
) &
WATCHDOG_PID=$!

cleanup_watchdog() {
  if [ -n "${WATCHDOG_PID:-}" ] && kill -0 "$WATCHDOG_PID" 2>/dev/null; then
    terminate_owned_tree "$WATCHDOG_PID" TERM
    wait "$WATCHDOG_PID" 2>/dev/null || true
  fi
}

handle_signal() {
  local sig="$1"
  echo "::warning::[${LABEL}] Received ${sig}; capturing cancellation diagnostics and stopping owned command tree." >&2
  capture_sample "cancelled-${sig}" "$STEP_LOG" || true
  terminate_owned_tree "$CMD_PID" TERM
  sleep 2
  terminate_owned_tree "$CMD_PID" KILL
  cleanup_watchdog
  write_class_manifest_and_collect \
    "CANCELLED" \
    "130" \
    "$START_UTC" \
    "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" \
    "$STEP_LOG"
  rm -f "$STATE_FILE"
  exit 130
}

trap 'handle_signal INT' INT
trap 'handle_signal TERM' TERM

wait "$CMD_PID"
EXIT_CODE=$?
cleanup_watchdog
trap - INT TERM

END_UTC=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
STATUS="PASSED"

if [ -f "${OUT_DIR}/${LABEL}.timed_out" ]; then
  STATUS="TIMED_OUT"
  if [ "$EXIT_CODE" -eq 0 ]; then
    EXIT_CODE=124
  fi
elif [ "$EXIT_CODE" -ne 0 ]; then
  STATUS="FAILED"
  capture_sample "on-failure" "$STEP_LOG" || true
fi

write_class_manifest_and_collect "$STATUS" "$EXIT_CODE" "$START_UTC" "$END_UTC" "$STEP_LOG"
rm -f "$STATE_FILE"

if [ -n "$CLASS_FQCN" ]; then
  EXPECTED_XML="${ARCHIVE_ROOT}/${LABEL}/xml/TEST-${CLASS_FQCN}.xml"
  if [ "$EXIT_CODE" -eq 0 ] && [ ! -s "$EXPECTED_XML" ]; then
    echo "::error::[${LABEL}] Gradle exited 0 but expected test XML '${EXPECTED_XML}' is missing or empty." >&2
    write_class_manifest_and_collect "INCOMPLETE_MISSING_XML" "1" "$START_UTC" "$END_UTC" "$STEP_LOG"
    exit 1
  fi
fi

exit "$EXIT_CODE"
