#!/bin/bash
# Add AirPlay to your Android TV.
# Builds the open-source AirPlay receiver jqssun/android-airplay-server (GPL-3, built on UxPlay) with
# Vitrine's TV changes (tv-build.patch) for your TV's chip, installs it as an invisible service that starts
# at boot, and names it. iPhones, iPads and Macs then see your TV under Screen Mirroring.
#
# Usage:  TV=192.168.1.50 NAME="Living Room TV" ./install-airplay.sh
#   TV    your TV's IP address (ADB over Wi-Fi must be on: Developer options -> USB/Network debugging)
#   NAME  the name Apple devices show (default: the TV's own device name)
# Needs:  git, JDK 17+ (JAVA_HOME), Android SDK (ANDROID_HOME) with:
#         sdkmanager "ndk;27.0.12077973" "cmake;3.22.1" "platforms;android-36"
# First build compiles OpenSSL, FFmpeg and UxPlay for your TV: 10-25 minutes. Later builds take ~1 minute.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
UPSTREAM="https://github.com/jqssun/android-airplay-server"
COMMIT="a46cca0a6a1274e9e33c0e750f0f1c1eb1d881ea"   # the upstream version this patch was made and tested against
WORK="${AIRPLAY_BUILD_DIR:-$HOME/.cache/vitrine-airplay}"   # must not contain spaces: FFmpeg's build breaks on them
PKG="io.github.jqssun.airplay"

die(){ echo "error: $*" >&2; exit 1; }
[ -n "${TV:-}" ] || die "set TV to your TV's IP address, e.g. TV=192.168.1.50 $0"
case "$TV" in *:*) ;; *) TV="$TV:5555" ;; esac
case "$WORK" in *" "*) die "AIRPLAY_BUILD_DIR must not contain spaces ($WORK)";; esac
: "${ANDROID_HOME:=$HOME/Library/Android/sdk}"; export ANDROID_HOME
[ -d "$ANDROID_HOME/ndk/27.0.12077973" ] && [ -d "$ANDROID_HOME/cmake/3.22.1" ] && [ -d "$ANDROID_HOME/platforms/android-36" ] ||
  die "missing SDK parts; run: sdkmanager \"ndk;27.0.12077973\" \"cmake;3.22.1\" \"platforms;android-36\""
command -v adb >/dev/null || die "adb not found (brew install --cask android-platform-tools)"

adb connect "$TV" >/dev/null
[ "$(adb -s "$TV" get-state 2>/dev/null)" = "device" ] || die "can't reach $TV over ADB (approve the prompt on the TV, tick 'Always allow')"
tv(){ adb -s "$TV" shell "$@" | tr -d '\r'; }
ABI="$(tv getprop ro.product.cpu.abilist | cut -d, -f1)"
NAME="${NAME:-$(tv settings get global device_name)}"
echo "TV: $(tv getprop ro.product.model), Android $(tv getprop ro.build.version.release), ABI $ABI; AirPlay name: $NAME"

# 1. Source: upstream at the tested commit, plus its submodules
if [ ! -d "$WORK/.git" ]; then git clone -q "$UPSTREAM" "$WORK"; fi
cd "$WORK"
git fetch -q --depth 1 origin "$COMMIT"
git checkout -q -f "$COMMIT"
git clean -fdq app/src/main/kotlin
git submodule update -q --init --depth 1

# 2. Vitrine's TV changes, then build only for this TV's chip
git apply "$HERE/tv-build.patch"
sed -i.bak "s/^val allAbis = listOf(.*)/val allAbis = listOf(\"$ABI\")/" app/build.gradle.kts && rm -f app/build.gradle.kts.bak
echo "sdk.dir=$ANDROID_HOME" > local.properties
echo "Building (first time: 10-25 minutes)..."
./gradlew --no-daemon --quiet assembleRelease
APK="$WORK/app/build/outputs/apk/release/app-release.apk"

# 3. Install, allow it to show video from the background, name it, start it
adb -s "$TV" install -r "$APK" >/dev/null
tv appops set "$PKG" SYSTEM_ALERT_WINDOW allow
q=${NAME//\'/\'\\\'\'}
tv "am broadcast -n $PKG/.SettingsReceiver -a $PKG.SET_SETTING --es key server_name --es value '$q'" >/dev/null
# The service isn't exported; its boot receiver starts it (QUICKBOOT_POWERON is the one boot action a shell may send)
tv am broadcast -a android.intent.action.QUICKBOOT_POWERON -n "$PKG/.BootReceiver" >/dev/null
sleep 6
if tv dumpsys activity services "$PKG" | grep -q "isForeground=true"; then
  echo "Done: AirPlay is running. On an iPhone: Control Centre -> Screen Mirroring -> \"$NAME\"."
else
  echo "Installed, but the service isn't running yet; check: adb -s $TV logcat | grep -E 'AirPlayService|NsdServiceManager'"
fi
