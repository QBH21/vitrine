# Add AirPlay to your Android TV

Android TV and Google TV have no AirPlay; Apple only licenses it to some TV makers. This folder turns any
Android TV into an **AirPlay receiver** with the open-source [android-airplay-server](https://github.com/jqssun/android-airplay-server)
by jqssun (GPL-3, built on [UxPlay](https://github.com/FDH2/UxPlay)), changed so it behaves like a built-in feature:

- **Invisible.** No app icon, nothing to open. It starts at boot as a background service, like Chromecast.
- **No interface, ever.** During a session you see only the mirrored screen or video; when the iPhone, iPad
  or Mac disconnects, the TV goes straight back to its home screen. Music plays with the home screen up.
- **Your name.** It shows under Screen Mirroring with the name you choose.
- **Light.** About 25–47 MB of RAM while idle on a 1.3–1.7 GB TV.

Works: screen mirroring (H.264 and HEVC), audio (AAC and ALAC), video "AirPlay" from apps such as Photos.
Doesn't: DRM-protected video (Apple TV+, Netflix) shows black; Apple allows that only on licensed receivers.

## Install (one command)

```bash
# once, on a Mac with Homebrew:
brew install --cask android-platform-tools android-commandlinetools
brew install openjdk@21
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
sdkmanager "ndk;27.0.12077973" "cmake;3.22.1" "platforms;android-36"

# then, with ADB over Wi-Fi on (TV: Settings -> Device Preferences -> Developer options):
TV=192.168.1.50 NAME="Living Room TV" ./install-airplay.sh
```

The script reads your TV's CPU type (32-bit `armeabi-v7a` on most budget TVs, `arm64-v8a` on newer ones),
fetches the upstream receiver at the exact version this patch was tested on, applies `tv-build.patch`,
builds it for that chip only, installs it, allows it to show video from the background (`SYSTEM_ALERT_WINDOW`),
names it and starts it. The first build takes 10–25 minutes (it compiles OpenSSL, FFmpeg and UxPlay).

Then on an iPhone: **Control Centre → Screen Mirroring → your TV's name.**

## What `tv-build.patch` changes

| File | Change |
|---|---|
| `AndroidManifest.xml` | Removes the launcher entry, so there's no icon |
| `ui/TvScreen.kt` (new) | Replaces the receiver's interface with a black screen that holds only the video (and a PIN if you turn PINs on) |
| `MainActivity.kt` | Goes to the TV's home screen when the last device disconnects, or after 6 s of an audio-only session |
| `service/AirPlayService.kt` | Opens the video screen only when mirroring or a video actually starts, not on every connection (Apple devices connect just to look a TV up) |
| `Prefs.kt`, `strings.xml` | Neutral default name and label |
| `build.gradle.kts`, `Permissions.kt` | Builds against SDK 36, signs with the debug key, skips Play-store lint |

## Settings without an interface

```bash
# rename (takes effect after a reboot)
adb shell am broadcast -n io.github.jqssun.airplay/.SettingsReceiver -a io.github.jqssun.airplay.SET_SETTING --es key server_name --es value "Bedroom TV"
# require a code shown on the TV before a device can connect
adb shell am broadcast -n io.github.jqssun.airplay/.SettingsReceiver -a io.github.jqssun.airplay.SET_SETTING --es key require_pin --es value true
# list every setting
adb shell am broadcast -n io.github.jqssun.airplay/.SettingsReceiver -a io.github.jqssun.airplay.LIST_SETTINGS
```

Remove it: `adb uninstall io.github.jqssun.airplay`

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Not in the iPhone's list | Phone and TV on different networks, or guest/isolated Wi-Fi that blocks multicast. Check `adb logcat \| grep NsdServiceManager` shows `_airplay._tcp registered` |
| Connects but no picture | The `SYSTEM_ALERT_WINDOW` grant is missing (rerun the script), or another app holds the TV's only video decoder: force-stop the last video app (see `docs/PLAYBOOK.md`) |
| Picture but no sound on the soundbar | HDMI ARC isn't established: `adb shell dumpsys hdmi_control` should show `mArcEstablished: true` |
| Build fails in FFmpeg | The build folder path contains a space; set `AIRPLAY_BUILD_DIR` to a path without one |
| `UnsatisfiedLinkError` | Built for the wrong CPU; the script detects it, so rerun it |

## License

The receiver is © jqssun and contributors, GPL-3.0. `tv-build.patch` modifies it and is therefore GPL-3.0
as well (see `LICENSE` in this folder). The rest of this repository (Vitrine) is MIT.
