#!/usr/bin/env bash
# Build the debug variant, install it on the connected device and launch it.
# Usage: scripts/install-debug.sh [device-serial]
set -euo pipefail
cd "$(dirname "$0")/.."

# Use Android Studio's bundled JDK if no suitable JAVA_HOME is set
if [[ -z "${JAVA_HOME:-}" ]]; then
  for jbr in "$HOME/.local/share/JetBrains/Toolbox/apps/android-studio/jbr" /opt/android-studio/jbr; do
    [[ -d "$jbr" ]] && export JAVA_HOME="$jbr" && break
  done
fi

ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
[[ -n "${1:-}" ]] && export ANDROID_SERIAL="$1"

./gradlew :app:app:installDefaultDebug
"$ADB" shell am start -n de.mm20.launcher2.debug/de.mm20.launcher2.ui.launcher.LauncherActivity
