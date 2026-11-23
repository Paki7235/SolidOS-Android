#!/bin/sh
# Costruisce l'app SolidOS e la installa nel telefono virtuale (o nel telefono collegato),
# poi la rende la Home.
set -e
cd "$(dirname "$0")/.."
. strumenti/ambiente.sh
./gradlew --no-daemon -q :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd package set-home-activity it.solidos.home/.Home >/dev/null 2>&1 || true
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
echo "SolidOS installato."
