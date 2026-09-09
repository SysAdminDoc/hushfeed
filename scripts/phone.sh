#!/bin/bash
# Drives the test phone one step at a time for the device checks in Roadmap_Blocked.md.
#
# Every tap is refused unless TikTok is in the foreground, because a phone in somebody's hand
# takes the foreground between two adb calls and a queued tap then lands in another app. Every
# step can be followed by a screenshot, which is the evidence a device check produces.
#
# Coordinates are given in the space of a screenshot viewed at 933 px wide (what an image
# viewer shows for a 1080 px panel) and scaled here, so numbers read off the picture can be
# used as they are. Set PHONE_SCALE=1 to give panel pixels instead.
#
#   scripts/phone.sh shot 01-feed                 screenshot to $PHONE_SHOTS/01-feed.png
#   scripts/phone.sh tap 464 393                  tap, then wait 2 s
#   scripts/phone.sh swipe 466 1500 466 500       a scroll up
#   scripts/phone.sh key KEYCODE_BACK
#   scripts/phone.sh text hello
#   scripts/phone.sh top                          the foreground activity
#   scripts/phone.sh logcat "morphe|hushfeed"     recent payload lines
#   scripts/phone.sh gfx                          frame stats for the perf device check
#
# PHONE_SERIAL selects the device; it must be the test phone, never the owner's own.
set -eu
S="${PHONE_SERIAL:?set PHONE_SERIAL to the test phone serial}"
if [[ "$S" != "R5CT139QJ5F" ]]; then
    echo "REFUSED: device $S is not the S22 test phone" >&2
    exit 2
fi
ADB="${ADB:-$(command -v adb || ls "$LOCALAPPDATA"/Microsoft/WinGet/Packages/Google.PlatformTools*/platform-tools/adb.exe 2>/dev/null | head -1)}"
SP="${PHONE_SHOTS:-$TEMP/hushfeed-device/shots}"
PKG=com.zhiliaoapp.musically
SCALE="${PHONE_SCALE:-1.15756}"
mkdir -p "$SP"

top() { timeout 30 "$ADB" -s "$S" shell dumpsys activity activities 2>/dev/null | grep -m1 -o "topResumedActivity=ActivityRecord{[^}]*}" | sed 's/.*u0 //;s/ t[0-9]*}//'; }
shot() { timeout 60 "$ADB" -s "$S" exec-out screencap -p > "$SP/$1.png" 2>/dev/null; echo "shot $SP/$1.png top=$(top)"; }
guard() { local t; t=$(top); case "$t" in $PKG/*) ;; *) echo "REFUSED: foreground is $t"; exit 2;; esac; }
px() { awk "BEGIN{printf \"%d\", $1*$SCALE}"; }
tap() { guard; timeout 30 "$ADB" -s "$S" shell input tap "$(px "$1")" "$(px "$2")"; sleep "${3:-2}"; }
swipe() { guard; timeout 30 "$ADB" -s "$S" shell input swipe "$(px "$1")" "$(px "$2")" "$(px "$3")" "$(px "$4")" "${5:-300}"; sleep "${6:-2}"; }
key() { guard; timeout 30 "$ADB" -s "$S" shell input keyevent "$1"; sleep "${2:-2}"; }
text() { guard; timeout 30 "$ADB" -s "$S" shell input text "$1"; sleep 1; }
logcat() { timeout 60 "$ADB" -s "$S" logcat -d 2>/dev/null | grep -iE "$1" | tail -"${2:-20}"; }
gfx() { timeout 60 "$ADB" -s "$S" shell dumpsys gfxinfo $PKG "${1:-}" 2>/dev/null | grep -E "Total frames|Janky|50th|90th|99th|Number Frame|Uptime" | head -12; }
"$@"
