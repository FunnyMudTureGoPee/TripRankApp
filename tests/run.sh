#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
SDK="${TRIPRANK_ANDROID_SDK:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}"
PLATFORM="$SDK/platforms/android-35/android.jar"
[ -f "$PLATFORM" ] || PLATFORM="$SDK/android-35/android.jar"
[ -d build/classes ] || { echo "Run ./build.sh first" >&2; exit 1; }
JSON="${JSON_JAR:-$PWD/build/test-libs/json-20240303.jar}"
if [ ! -f "$JSON" ]; then
 mkdir -p "$(dirname "$JSON")"
 curl --fail --location --retry 3 https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar -o "$JSON"
fi
mkdir -p build/test-classes
CP="build/classes:libs/zxing-core-3.5.3.jar:$JSON:$PLATFORM"
java com.sun.tools.javac.Main -encoding UTF-8 -cp "$CP" -d build/test-classes tests/*.java
for test in MergeTest SheetsTest AutoSyncTest QRTest; do
 java -cp "build/test-classes:$CP" "app.triprank.$test"
done
