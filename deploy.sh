#!/bin/sh
# figly · deploy — builds the release and installs it on the phone.
# Takes the wire if one is plugged in, the air if the phone was connected
# before (see below). Quiet on success, honest on failure.
#
# air setup, once, with the cable in:
#   adb tcpip 5555
#   adb shell ip -f inet addr show wlan0   # note the phone's ip
#   echo <ip>:5555 > .deploy-phone
# the phone listens until it reboots; after a reboot, plug the wire once
# and run the two lines again.
set -e
cd "$(dirname "$0")"

JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export JAVA_HOME
ADB="${ADB:-adb}"
APK=app/build/outputs/apk/release/app-release.apk

echo "· growing the apk"
./gradlew -q :app:assembleRelease

usb=$($ADB devices | sed 1d | awk '$2=="device" && $1 !~ /:/ {print $1; exit}')
if [ -n "$usb" ]; then
  echo "· wire: $usb"
  $ADB -s "$usb" install -r "$APK"
  echo "· planted."
  exit 0
fi

if [ -f .deploy-phone ]; then
  addr=$(cat .deploy-phone)
  echo "· air: $addr"
  $ADB connect "$addr" >/dev/null 2>&1 || true
  if $ADB devices | sed 1d | grep -q "^$addr[[:space:]]*device"; then
    $ADB -s "$addr" install -r "$APK"
    echo "· planted."
    exit 0
  fi
  echo "  $addr did not answer (phone rebooted? wire it once: adb tcpip 5555)"
fi

echo "no phone on wire or air."
exit 1
