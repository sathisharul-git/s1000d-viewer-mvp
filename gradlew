#!/usr/bin/env sh
set -e

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
LOCAL_GRADLE="$SCRIPT_DIR/.tools/gradle/gradle-8.10.2/bin/gradle"
LOCAL_JAVA_HOME="$SCRIPT_DIR/.tools/jdk17/jdk-17.0.18+8"

if [ -x "$LOCAL_JAVA_HOME/bin/java" ]; then
  export JAVA_HOME="$LOCAL_JAVA_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if [ -x "$LOCAL_GRADLE" ]; then
  exec "$LOCAL_GRADLE" "$@"
fi

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

echo "gradle executable not found. Install Gradle or keep .tools/gradle present." >&2
exit 1