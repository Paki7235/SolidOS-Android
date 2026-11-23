#!/bin/sh
# Avvia il telefono virtuale «SolidOS» (Android 15, processore Apple: veloce, senza emulazione).
#   sh strumenti/emulatore.sh            → con la finestra
#   sh strumenti/emulatore.sh nascosto   → senza finestra (per le prove automatiche)
. "$(dirname "$0")/ambiente.sh"
if ! avdmanager list avd 2>/dev/null | grep -q "Name: SolidOS"; then
	echo no | avdmanager create avd -n SolidOS -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_8 >/dev/null
fi
if [ "${1:-}" = nascosto ]; then
	exec emulator -avd SolidOS -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect
fi
exec emulator -avd SolidOS -no-boot-anim
