#!/usr/bin/env bash
set -uo pipefail

# Non-secret CI diagnostics wrapper for Gradle steps.
# Captures host resource metrics and two spaced JVM thread dumps if a wrapped step
# stays active past WATCHDOG_SECONDS, without logging environment variables or CLI secrets.

WATCHDOG_SECONDS=720
LABEL="gradle-step"
SNAPSHOT_NOW="false"
OUT_DIR="${RUNNER_TEMP:-/tmp}/precisioncad-ci-diagnostics"

while [ $# -gt 0 ]; do
  case "$1" in
    --watchdog-seconds)
      WATCHDOG_SECONDS="${2:-720}"
      shift 2
      ;;
    --label)
      LABEL="${2:-gradle-step}"
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
    --)
      shift
      break
      ;;
    *)
      break
      ;;
  esac
done

mkdir -p "$OUT_DIR"

capture_sample() {
  local sample_tag="$1"
  local ts
  ts=$(date -u +"%Y%m%dT%H%M%SZ")
  local out_file="${OUT_DIR}/${LABEL}-${ts}-${sample_tag}.txt"

  {
    echo "=== PrecisionCAD CI Diagnostics (${LABEL} / ${sample_tag}) ==="
    echo "UTC Timestamp: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    echo ""

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
      jcmd -l 2>/dev/null | awk '{print $1, $2}' || true
      echo ""

      local java_pids
      java_pids=$(jcmd -l 2>/dev/null | awk '/Gradle|Worker|Kotlin|lint|Precision|Main/ {print $1}')
      for pid in $java_pids; do
        local main_cls
        main_cls=$(jcmd -l 2>/dev/null | awk -v p="$pid" '$1 == p {print $2; exit}')
        echo "=== Thread.print for PID ${pid} (${main_cls:-unknown}) ==="
        jcmd "$pid" Thread.print -l 2>&1 || true
        echo ""
      done
    elif command -v jstack >/dev/null 2>&1; then
      local pids
      pids=$(ps -eo pid,comm 2>/dev/null | awk '/java/ {print $1}')
      for pid in $pids; do
        echo "=== jstack for PID ${pid} ==="
        jstack -l "$pid" 2>&1 || true
        echo ""
      done
    fi
  } >"$out_file" 2>&1
}

if [ "$SNAPSHOT_NOW" = "true" ]; then
  capture_sample "manual"
  exit 0
fi

if [ $# -eq 0 ]; then
  echo "Usage: $0 [--watchdog-seconds N] [--label NAME] -- <command...>" >&2
  exit 2
fi

"$@" &
CMD_PID=$!

(
  elapsed=0
  interval=5
  while [ "$elapsed" -lt "$WATCHDOG_SECONDS" ]; do
    if ! kill -0 "$CMD_PID" 2>/dev/null; then
      exit 0
    fi
    sleep "$interval"
    elapsed=$((elapsed + interval))
  done

  if kill -0 "$CMD_PID" 2>/dev/null; then
    echo "::warning::[${LABEL}] Command still running after ${WATCHDOG_SECONDS}s; capturing watchdog JVM diagnostics (sample 1/2)." >&2
    capture_sample "sample1"
    sleep 25
    if kill -0 "$CMD_PID" 2>/dev/null; then
      echo "::warning::[${LABEL}] Capturing watchdog JVM diagnostics (sample 2/2)." >&2
      capture_sample "sample2"
    fi
  fi
) </dev/null >/dev/null 2>&1 &
WATCHDOG_PID=$!

cleanup_watchdog() {
  if kill -0 "$WATCHDOG_PID" 2>/dev/null; then
    pkill -P "$WATCHDOG_PID" 2>/dev/null || true
    kill "$WATCHDOG_PID" 2>/dev/null || true
    wait "$WATCHDOG_PID" 2>/dev/null || true
  fi
}

trap cleanup_watchdog EXIT INT TERM

wait "$CMD_PID"
EXIT_CODE=$?
cleanup_watchdog
trap - EXIT INT TERM

if [ "$EXIT_CODE" -ne 0 ]; then
  capture_sample "on-failure" || true
fi

exit "$EXIT_CODE"
