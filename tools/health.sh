#!/bin/bash
# After-reboot checklist: run it after every debloat batch (wait for the TV to finish booting first). Read-only.
# Usage: TV=192.168.1.50 ./health.sh       compare the numbers with the run before the batch
set -u; . "$(dirname "$0")/common.sh"
echo "booted:          $(tv getprop sys.boot_completed)"
echo "on screen:       $(tv dumpsys window | grep -m1 mCurrentFocus | grep -oE '[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+' | head -1)"
echo "home resolves:   $(tv cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1)"
echo "Wi-Fi:           $(tv cmd wifi status | grep -m1 -iE 'Wifi is')"
echo "TV inputs:       $(tv dumpsys tv_input | grep -oE 'id=[^,} ]+' | sort -u | wc -l | tr -d ' ') registered (must not drop)"
echo "Chromecast:      $(tv pidof com.google.android.apps.mediashell >/dev/null && echo running || echo 'not running')"
echo "AirPlay:         $(tv dumpsys activity services io.github.jqssun.airplay | grep -q isForeground=true && echo running || echo 'not installed / not running')"
echo "soundbar (CEC):  $(tv dumpsys hdmi_control | grep -E 'mArcEstablished|mSystemAudioActivated' | sed 's/^ *//' | paste -sd' ' -)"
echo "RAM available:   $(tv cat /proc/meminfo | awk '/MemAvailable/{printf "%d MB", $2/1024}')"
echo "recent crashes:"; tv logcat -d -b crash | grep -E '>>> .* <<<' | tail -3 | sed 's/^/  /'
echo "Now check by hand: remote, volume, input switching, and opening each app you use."
