#!/usr/bin/env bash
set -u

# Keep the test exit status while collecting screenshots before the emulator stops.
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR \
  ${PROFILE_ARGUMENT:-} ${TEST_ARGUMENT:-} --no-daemon
test_result=$?
mkdir -p macrobenchmark/build/interface-evidence
adb pull /sdcard/Android/data/tachiyomi.macrobenchmark/files/interface-evidence \
  macrobenchmark/build/interface-evidence || true
exit "$test_result"
