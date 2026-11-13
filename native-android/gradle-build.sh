#!/usr/bin/env bash
set -uo pipefail

cd android
set +e
./gradlew "$@" --stacktrace --console=plain 2>&1 | tee /tmp/botcraft-gradle-build.log
status=${PIPESTATUS[0]}
set -e
if [ "$status" -ne 0 ]; then
  echo "Gradle failed; exposing concise compiler diagnostics as GitHub annotations."
  grep -E '(^e: |\.java:[0-9]+: error:|error:|What went wrong:|Execution failed for task|Could not resolve|Could not find)' /tmp/botcraft-gradle-build.log \
    | tail -n 60 \
    | while IFS= read -r line; do echo "::error::$line"; done
fi
exit "$status"
