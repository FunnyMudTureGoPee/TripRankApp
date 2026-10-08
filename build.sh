#!/bin/sh
set -eu
cd "$(dirname "$0")"
SDK="${TRIPRANK_ANDROID_SDK:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}"
[ -n "$SDK" ] || { echo "Set ANDROID_HOME or TRIPRANK_ANDROID_SDK" >&2; exit 1; }
TOOLS="${TRIPRANK_BUILD_TOOLS:-$SDK/build-tools/35.0.0}"
PLATFORM="$SDK/platforms/android-35/android.jar"
[ -f "$PLATFORM" ] || PLATFORM="$SDK/android-35/android.jar"
[ -f "$PLATFORM" ] && [ -x "$TOOLS/aapt2" ] || { echo "Install Android platform 35 and build-tools 35.0.0" >&2; exit 1; }
# Remove generated files so a rebuilt APK cannot contain stale classes.
rm -rf build/classes build/generated build/dex
mkdir -p build/classes build/generated build/dex
"$TOOLS/aapt2" compile --dir res -o build/resources.zip
"$TOOLS/aapt2" link -o build/base.apk -I "$PLATFORM" --manifest AndroidManifest.xml --java build/generated -A assets build/resources.zip
java com.sun.tools.javac.Main -source 8 -target 8 -encoding UTF-8 -classpath "$PLATFORM:libs/zxing-core-3.5.3.jar" -d build/classes src/app/triprank/*.java build/generated/app/triprank/R.java
python3 - <<'PYCODE'
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
with ZipFile('build/classes.jar','w',ZIP_DEFLATED) as z:
 for p in Path('build/classes').rglob('*.class'):z.write(p,p.relative_to('build/classes'))
PYCODE
java -cp "$TOOLS/lib/d8.jar" com.android.tools.r8.D8 --lib "$PLATFORM" --min-api 26 --output build/dex build/classes.jar libs/zxing-core-3.5.3.jar
python3 - <<'PYCODE'
from zipfile import ZipFile,ZIP_DEFLATED
from pathlib import Path
import shutil
shutil.copy('build/base.apk','build/unsigned.apk')
with ZipFile('build/unsigned.apk','a',ZIP_DEFLATED) as z:
 for p in Path('build/dex').glob('*.dex'):z.write(p,p.name)
PYCODE
"$TOOLS/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
if [ -n "${TRIPRANK_KEYSTORE:-}" ]; then
 : "${TRIPRANK_KEYSTORE_PASSWORD_FILE:?Set password file path}"
 KEYSTORE="$TRIPRANK_KEYSTORE"
 PASSWORD="file:$TRIPRANK_KEYSTORE_PASSWORD_FILE"
else
 mkdir -p build/debug-signing
 KEYSTORE=build/debug-signing/debug.p12
 PASSWORD=pass:android
 if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -storepass android -keypass android -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000
 fi
fi
java -jar "$TOOLS/lib/apksigner.jar" sign --ks "$KEYSTORE" --ks-pass "$PASSWORD" --out build/trip-rank.apk build/aligned.apk
java -jar "$TOOLS/lib/apksigner.jar" verify --verbose build/trip-rank.apk
"$TOOLS/zipalign" -c 4 build/trip-rank.apk
