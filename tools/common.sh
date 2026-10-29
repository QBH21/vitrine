# Shared by the tools: connects to the TV and keeps logs in $LOG_DIR (default ./tv-logs).
[ -n "${TV:-}" ] || { echo "Set TV to your TV's IP address, e.g. TV=192.168.1.50 $0" >&2; exit 1; }
case "$TV" in *:*) ;; *) TV="$TV:5555" ;; esac
LOG_DIR="${LOG_DIR:-$PWD/tv-logs}"; mkdir -p "$LOG_DIR"
adb connect "$TV" >/dev/null 2>&1
[ "$(adb -s "$TV" get-state 2>/dev/null)" = "device" ] || { echo "Can't reach $TV over ADB. Turn on Developer options -> USB/Network debugging and accept the prompt on the TV." >&2; exit 1; }
tv(){ adb -s "$TV" shell "$@" 2>/dev/null | tr -d '\r'; }
