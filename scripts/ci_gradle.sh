#!/usr/bin/env bash
set -euo pipefail

# The public Linux runner has 16 GiB RAM. Keep compilation and Gradle in one
# process so their heaps do not compete; leave room for tests and APK tools.
exec ./gradlew "$@" --no-daemon --no-parallel --max-workers=2 \
  -Pkotlin.compiler.execution.strategy=in-process \
  '-Dorg.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8'
