#!/usr/bin/env bash
# Runs the desktop E2E (see docs/testing/e2e.md) and ends it as soon as the report says it is done (DONE,
# READY_FOR_HOST_KILL or ABORTED). On READY it performs step 7: force-kills only the host and checks the guest's game
# exits by itself. Then it kills every leftover game and Gradle process and prints the summary. Windows (Git Bash)
# and macOS.
# Usage: tools/e2e/run-e2e.sh <log name> [-P...]   e.g. tools/e2e/run-e2e.sh focused -Pe2eOnly=C2
# The extra arguments go to Gradle: -Pe2eOnly=<ids>, -Pe2eBackend=opengl|vulkan, -Pe2eGuestBackend=opengl|vulkan,
# -Pe2eValidation=true.
if [ -z "$1" ]; then
  echo "usage: $0 <log name> [-P...]" >&2
  exit 2
fi
NAME=$1
shift
cd "$(dirname "$0")/../.." || exit 1
if [ -z "$JAVA_HOME" ] && [ -f .superpowers/env.sh ]; then
  source .superpowers/env.sh
fi
case "$(uname -s)" in
  MINGW*|MSYS*) WINDOWS=1 ;;
  *) WINDOWS= ;;
esac
LOGS=.superpowers/e2e-logs
mkdir -p "$LOGS"
R=fabric/run-e2e/hearthside-e2e-report.txt
rm -f "$R"
sh ./gradlew :fabric:runE2eClient "$@" > "$LOGS/$NAME.log" 2>&1 &
GRADLE=$!
while kill -0 $GRADLE 2>/dev/null; do
  if [ -f "$R" ] && grep -qE '^(READY_FOR_HOST_KILL|DONE$|ABORTED)' "$R"; then break; fi
  sleep 10
done
READY=$(grep -o "READY_FOR_HOST_KILL host=[0-9]* satellite=[0-9]*" "$R" 2>/dev/null)
if [ -n "$READY" ]; then
  HOST=$(echo "$READY" | sed 's/.*host=\([0-9]*\).*/\1/')
  SAT=$(echo "$READY" | sed 's/.*satellite=\([0-9]*\).*/\1/')
  if [ -n "$WINDOWS" ]; then
    powershell -NoProfile -Command "Stop-Process -Id $HOST -Force; \$sw = [Diagnostics.Stopwatch]::StartNew(); while ((Get-Process -Id $SAT -ErrorAction SilentlyContinue) -and \$sw.ElapsedMilliseconds -lt 15000) { Start-Sleep -Milliseconds 100 }; if (Get-Process -Id $SAT -ErrorAction SilentlyContinue) { 'STEP 7 FAIL: the satellite outlived the host by 15 s' } else { 'STEP 7 PASS: the satellite exited ' + \$sw.ElapsedMilliseconds + ' ms after the host was killed' }"
  else
    kill -9 "$HOST"
    TENTHS=0
    while kill -0 "$SAT" 2>/dev/null && [ $TENTHS -lt 150 ]; do
      sleep 0.1
      TENTHS=$((TENTHS + 1))
    done
    if kill -0 "$SAT" 2>/dev/null; then
      echo "STEP 7 FAIL: the satellite outlived the host by 15 s"
    else
      echo "STEP 7 PASS: the satellite exited about $((TENTHS * 100)) ms after the host was killed"
    fi
  fi
fi
if [ -n "$WINDOWS" ]; then
  powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { \$_.CommandLine -match 'KnotClient|hearthside.role|gradle-wrapper' -and \$_.Name -match 'java' } | ForEach-Object { try { Stop-Process -Id \$_.ProcessId -Force -ErrorAction Stop } catch {} }"
else
  pkill -9 -f 'java.*(KnotClient|hearthside\.role|gradle-wrapper)'
fi
kill $GRADLE 2>/dev/null
echo "PASS $(grep -c '^PASS' "$R")  FAIL $(grep -c '^FAIL' "$R")  SKIP $(grep -c '^SKIP' "$R")"
grep "^FAIL\|^ABORTED" "$R" || true
