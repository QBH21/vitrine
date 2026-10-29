# Android TV playbook: debloat, speed up and make it yours, without root

Everything here was done on two budget Android TVs (MStar/MediaTek chips, 1.3 GB and 1.7 GB of RAM, Android 11,
one of them Google TV) over Wi-Fi ADB from a Mac. Nothing touches the firmware, and every step can be undone.

## Ground rules

1. **Never touch the OS image.** No bootloader unlock, root, fastboot, flashing or `dd`. As long as the image is
   untouched, Settings → Device Preferences → Reset returns the TV to factory state.
2. **Disable, don't uninstall:** `pm disable-user --user 0 <package>`. Undo with `pm enable <package>`.
3. **At most 10 packages per batch**, then reboot, run `tools/health.sh`, try the remote, inputs and apps.
4. **Names containing tv, input, system, vendor, setup, settings, provider, inputmethod or keyboard** are off
   limits until you've checked what the package does (`adb shell dumpsys package <p>`).
5. **Log everything and write the undo line before you reboot.** `tools/debloat.sh` does both.
6. **Record a setting's old value before changing it**, so the undo is exact.
7. **Have the soundbar on and set to the TV/ARC input when you reboot** (see "Soundbar" below).

## Connecting

Settings → Device Preferences → About → press OK on *Build* seven times → Developer options → turn on
*USB debugging* and *Network debugging*. Then `adb connect <TV-IP>:5555` and accept the prompt on the TV with
*Always allow*. Find the TV's IP without the remote: `dns-sd -B _androidtvremote2._tcp local.` lists TVs by name.

- ADB drops when the TV sleeps; reconnect. "No route to host" means the TV is off.
- Screenshots: `adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png` (`exec-out` breaks on some MStar TVs).
- Drive menus with `adb shell input keyevent` (19–22 arrows, 23 OK, 4 Back, 3 Home), but take a screenshot and
  check what's focused before pressing OK.

## What to disable

How to judge a package: `dumpsys package <p>` shows where it lives, its permissions and receivers; `ps -A` shows
whether it's running; `dumpsys meminfo` shows what it costs. Names with factory, test, demo, store, acr,
feedback, recommendation, autoinstall or partner are rarely wanted.

| Usually junk | Usually ask the owner | Never touch |
|---|---|---|
| ACR viewing trackers (e.g. `tv.anoki.acr.controller`), store/demo modes, factory and service menus, Google Feedback, partner auto-installers (`android.autoinstalls.config.*`), launcher recommendation feeds, printing, unused streaming apps | Assistant (`katniss`) and text-to-speech, Netflix, screensavers, Miracast/DLNA cast apps, the vendor's help/manual, remote firmware updaters, calendar sync | TV inputs and tuner (`*.tvinput`, `*.tv.service`, the Live TV app: on MediaTek TVs it is also the player for HDMI), Settings, keyboards, network and Wi-Fi, Bluetooth and remote pairing, Play services and Play Store, WebView, setup wizards, `providers.*`, SystemUI |

Before disabling a "Live TV" app, open an HDMI input and check which app shows it.

## Making a custom launcher the home screen

On Android TV 11 the stock launcher's HOME filter has priority 2 and wins: `cmd package set-home-activity` and
the HOME role don't change what the Home button opens. Two ways that work:

1. **Redirect** (keeps the stock launcher): Vitrine's accessibility service starts Vitrine whenever the stock
   launcher's window appears. Costs the stock launcher's RAM (30–145 MB) and a brief flash.
2. **Disable the stock launcher** (`com.google.android.tvlauncher`, or `com.google.android.apps.tv.launcherx` on
   Google TV). **On Google TV** the setup wizard's `com.google.android.tungsten.setupwraith/.RecoveryActivity`
   then claims HOME at priority 4; the shell can't disable one component, so disable `setupwraith` too (setup is
   long finished; a factory reset brings it back). Check with
   `adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN`.

## Performance

| Tweak | Command | Worth it |
|---|---|---|
| Light launcher | Vitrine release build | Yes: ~27 MB vs 30–145 MB for stock launchers |
| Animations | `settings put global window_animation_scale 0.5` (+ transition, animator) | Yes, the most visible speed-up |
| Trim caches | `pm trim-caches 999G` | Once (hundreds of MB of storage) |
| Close cached apps | `am kill-all` | Harmless; Android refills it |
| Stop a background service | `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore` | For things you can't disable |
| Screensaver off | `settings put secure screensaver_enabled 0` | If nobody wants it |

Measure RAM on a settled TV (MemAvailable a minute after boot), not right after a reboot or `am kill-all`.

## Soundbar (HDMI ARC / CEC)

Rebooting the TV while the soundbar is in standby can leave the soundbar without an HDMI address: the remote
still changes its volume, but sound comes out of the TV. `adb shell dumpsys hdmi_control` shows it: healthy is
`mArcEstablished: true` and the audio system at `physical_address: 0x3000` (or its HDMI port); broken is
`0x0000`. Software can't fix it; unplug the soundbar for 30 seconds, then select its TV/ARC input.

For the cleanest sound, set the TV's Sound menu → Digital Output to **Bypass** (passes Dolby untouched to the
soundbar) and turn the TV's own surround and auto-volume off. These settings only take effect from the TV's
Sound menu, not by writing `settings` keys.

## The single video decoder trap

Some TVs allow only one hardware video decoder (`/vendor/etc/media_codecs.xml`: `concurrent-instances max="1"`).
If a player app keeps its process alive after playback, video stops working in every other app until it is
force-stopped (Settings → Apps → the app → Force stop). `tools/measure.sh` prints the limit. A normal app can't
fix this for you: `killBackgroundProcesses` is refused on these TVs.

## Firmware and backups

On a locked retail TV (`ro.build.type=user`, `vbmeta` locked, SELinux enforcing) there is no way to back up
the OS image from Android, and unlocking wipes the TV and can erase its DRM keys. For the worst case, ask the
maker for the USB firmware file for your exact model, board and panel. For everything in this playbook the
factory reset is a complete undo.
