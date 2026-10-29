#!/bin/bash
# Disable unwanted apps safely: always "pm disable-user" (never uninstall), at most 10 per run, logged, undoable.
# Usage: TV=192.168.1.50 ./debloat.sh com.vendor.ads com.vendor.demo ...   disable these (max 10)
#        TV=192.168.1.50 ./debloat.sh --undo                               re-enable everything this tool disabled
# After each run: reboot the TV, run ./health.sh, and check the remote, inputs and your apps before the next run.
# Names containing tv/input/system/vendor/setup/settings/provider/inputmethod/keyboard are refused unless you
# have checked what the package does and run with ALLOW_PROTECTED=1.
set -u; . "$(dirname "$0")/common.sh"
UNDO="$LOG_DIR/undo-debloat.sh"; LOG="$LOG_DIR/DEBLOAT-LOG.md"
if [ "${1:-}" = "--undo" ]; then
  [ -f "$UNDO" ] || { echo "Nothing to undo ($UNDO not found)"; exit 0; }
  TV="$TV" bash "$UNDO"; echo "Re-enabled. Reboot the TV."; exit 0
fi
[ $# -ge 1 ] || { sed -n '2,7p' "$0"; exit 1; }
[ $# -le 10 ] || { echo "At most 10 packages per batch (got $#). Split them and check the TV between batches."; exit 1; }
CORE='^(android|com\.android\.(systemui|settings|phone|shell|bluetooth|networkstack.*|providers\..*|packageinstaller|permissioncontroller|webview)|com\.google\.android\.(gms|gsf|webview|packageinstaller|permissioncontroller|ext\..*|inputmethod\.latin))$'
PROTECTED='tv|input|system|vendor|setup|settings|provider|inputmethod|keyboard'
for p in "$@"; do
  echo "$p" | grep -qE "$CORE" && { echo "Refusing core package $p"; exit 1; }
  if echo "$p" | grep -qiE "$PROTECTED" && [ "${ALLOW_PROTECTED:-0}" != 1 ]; then
    echo "Refusing $p: its name looks like a TV/input/setup/system component. Check it (adb shell dumpsys package $p), then rerun with ALLOW_PROTECTED=1."; exit 1; fi
  tv pm list packages | grep -qx "package:$p" || { echo "Not installed: $p"; exit 1; }
done
[ -f "$UNDO" ] || printf '#!/bin/bash\n# Re-enables every package debloat.sh disabled. Run via: ./debloat.sh --undo\nadb connect "$TV" >/dev/null\n' > "$UNDO"
{ echo; echo "## $(date '+%Y-%m-%d %H:%M') batch"; echo; } >> "$LOG"
for p in "$@"; do
  r=$(tv pm disable-user --user 0 "$p" | sed 's/.*new state: //')
  printf '%-55s %s\n' "$p" "$r"; echo "- \`$p\`: $r" >> "$LOG"
  echo "adb -s \"\$TV\" shell pm enable $p" >> "$UNDO"
done
echo "Logged to $LOG. Undo: ./debloat.sh --undo.  Now reboot (adb -s $TV reboot) and run ./health.sh"
