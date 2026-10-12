#!/usr/bin/env bash
# Runs the desktop E2E (see docs/testing/e2e.md) and ends it as soon as the report says it is done (DONE,
# READY_FOR_HOST_KILL or ABORTED). On READY it performs step 7: force-kills only the host and checks the guest's game
# exits by itself. Then it kills this checkout's leftover game and Gradle processes and prints the summary. Windows
# (Git Bash) and macOS.
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
# Only this checkout's leftovers: a game or Gradle wrapper whose command line names a path inside this repository (its
# build output, loom cache or wrapper jar), so a Minecraft being played, or another checkout's run, is left alone.
if [ -n "$WINDOWS" ]; then
  # Command lines spell the repository C:\..., C:/... or /c/...; compared with \ as / and in lower case.
  KILL_LEFTOVERS='$roots = @($env:E2E_ROOT_WIN, $env:E2E_ROOT_POSIX) | ForEach-Object { $_.Replace([string][char]92, "/").TrimEnd("/").ToLowerInvariant() }; Get-CimInstance Win32_Process | Where-Object { $_.Name -match "java" -and $_.CommandLine -match "KnotClient|hearthside.role|gradle-wrapper" } | Where-Object { $line = $_.CommandLine.Replace([string][char]92, "/").ToLowerInvariant(); @(foreach ($r in $roots) { foreach ($d in "/fabric/", "/.gradle/", "/gradle/") { if ($line.Contains($r + $d)) { $true } } }).Count -gt 0 } | ForEach-Object { try { Stop-Process -Id $_.ProcessId -Force -ErrorAction Stop } catch {} }'
  E2E_ROOT_WIN="$(pwd -W)" E2E_ROOT_POSIX="$(pwd)" powershell -NoProfile -Command "$KILL_LEFTOVERS"
else
  ROOT="$(pwd)"
  ps -axww -o pid=,command= | grep -E 'java.*(KnotClient|hearthside\.role|gradle-wrapper)' \
    | grep -F -e "$ROOT/fabric/" -e "$ROOT/.gradle/" -e "$ROOT/gradle/" | awk '{print $1}' \
    | while read -r PID; do kill -9 "$PID" 2>/dev/null; done
fi
kill $GRADLE 2>/dev/null
echo "PASS $(grep -c '^PASS' "$R")  FAIL $(grep -c '^FAIL' "$R")  SKIP $(grep -c '^SKIP' "$R")"
grep "^FAIL\|^ABORTED" "$R" || true
