#!/bin/bash
# Performance tweaks that are safe and reversible.
# Usage: TV=192.168.1.50 ./tweaks.sh --apply    animations 0.5x, trim app caches, close cached background apps
#        TV=192.168.1.50 ./tweaks.sh --revert   animation scales back to what they were
set -u; . "$(dirname "$0")/common.sh"
KEYS="window_animation_scale transition_animation_scale animator_duration_scale"; SAVE="$LOG_DIR/tweaks-before.txt"
case "${1:-}" in
  --apply)
    [ -f "$SAVE" ] || for k in $KEYS; do echo "$k=$(tv settings get global $k)"; done > "$SAVE"
    for k in $KEYS; do tv settings put global $k 0.5; done
    free_before=$(tv df /data | awk 'NR==2{print $4}'); tv pm trim-caches 999G; tv am kill-all
    echo "Animations 0.5x; caches trimmed ($(( ($(tv df /data | awk 'NR==2{print $4}') - free_before) / 1024 )) MB freed); cached apps closed." ;;
  --revert)
    [ -f "$SAVE" ] || { echo "No saved values ($SAVE)"; exit 1; }
    while IFS='=' read -r k v; do if [ "$v" = null ]; then tv settings delete global "$k"; else tv settings put global "$k" "$v"; fi; done < "$SAVE"
    echo "Animation scales restored." ;;
  *) sed -n '2,4p' "$0"; exit 1 ;;
esac
