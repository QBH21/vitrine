#!/bin/bash
# Builds Vitrine and installs it on your TV over Wi-Fi ADB (replacing an earlier install, keeping its data).
# Usage: TV=192.168.1.50 ./build.sh            optimised (release) build + install
#        TV=192.168.1.50 ./build.sh --debug    debug build + install
#        ./build.sh --no-install               build only
# Needs JDK 17+ (JAVA_HOME) and the Android SDK (ANDROID_HOME, or sdk.dir in local.properties).
set -e
cd "$(dirname "$0")"
if [ "${1:-}" = "--debug" ]; then
  ./gradlew --quiet assembleDebug; APK=app/build/outputs/apk/debug/app-debug.apk
else
  ./gradlew --quiet assembleRelease; APK=app/build/outputs/apk/release/app-release.apk
fi
echo "Built $APK ($(du -h "$APK" | cut -f1))"
[ "${1:-}" = "--no-install" ] && exit 0
[ -n "${TV:-}" ] || { echo "Set TV to your TV's IP address, e.g. TV=192.168.1.50 ./build.sh"; exit 1; }
case "$TV" in *:*) ;; *) TV="$TV:5555" ;; esac
adb connect "$TV" >/dev/null
adb -s "$TV" install -r "$APK"
echo "Installed. Open Vitrine from the TV's app list to try it; see README.md to make it the home screen."
