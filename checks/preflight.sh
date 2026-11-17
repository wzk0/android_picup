#!/usr/bin/env bash
#
# 一键质量门：编译 → 单测 → lint → 打包 debug，并打印 APK 信息。
#
# 用法（在项目根目录）：
#   ./checks/preflight.sh
#
# 需要 ANDROID_HOME；JAVA_HOME 未设置时尝试 ~/SDK/jdk-21.0.12。

set -euo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$root"

if [ -z "${JAVA_HOME:-}" ] && [ -d "$HOME/SDK/jdk-21.0.12" ]; then
    export JAVA_HOME="$HOME/SDK/jdk-21.0.12"
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/SDK/Android" ]; then
    export ANDROID_HOME="$HOME/SDK/Android"
fi

# 16 GB 机器：不降堆会被 OOM kill（exit 143）
common=(-Dorg.gradle.jvmargs=-Xmx2560m -Dkotlin.daemon.jvmargs=-Xmx1536m --no-parallel)

echo "==> compileDebugKotlin"
./gradlew :app:compileDebugKotlin "${common[@]}"

echo "==> testDebugUnitTest"
./gradlew :app:testDebugUnitTest "${common[@]}"

echo "==> lintDebug"
./gradlew :app:lintDebug "${common[@]}"

echo "==> assembleDebug"
./gradlew :app:assembleDebug "${common[@]}"

echo
echo "==> results"
tests=$(find app/build/test-results/testDebugUnitTest -name '*.xml' -print0 2>/dev/null |
    xargs -0 -r grep -h -o 'tests="[0-9]*"' 2>/dev/null |
    sed 's/[^0-9]//g' | awk '{s+=$1} END {print s+0}')
failures=$(find app/build/test-results/testDebugUnitTest -name '*.xml' -print0 2>/dev/null |
    xargs -0 -r grep -h -o 'failures="[0-9]*"' 2>/dev/null |
    sed 's/[^0-9]//g' | awk '{s+=$1} END {print s+0}')
echo "unit tests : ${tests} (failures ${failures})"

lint_errors=0
if [ -f app/build/reports/lint-results-debug.xml ]; then
    lint_errors=$(grep -c 'severity="Error"' app/build/reports/lint-results-debug.xml || true)
fi
echo "lint errors: ${lint_errors}"

apk=app/build/outputs/apk/debug/app-debug.apk
if [ -f "$apk" ]; then
    size=$(du -h "$apk" | cut -f1)
    echo "apk        : $apk ($size)"
    md5sum "$apk"
else
    echo "apk        : MISSING" >&2
    exit 1
fi

if [ "$failures" -ne 0 ] || [ "$lint_errors" -ne 0 ]; then
    echo "quality gate: FAILED" >&2
    exit 1
fi
echo "quality gate: OK（真机验证仍需人工完成）"