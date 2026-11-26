#!/bin/sh
set -eu

DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME="$DIR"

if command -v java >/dev/null 2>&1; then
  JAVA_EXE="java"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
else
  echo "ERROR: JAVA_HOME is not set and java is not in PATH." >&2
  exit 1
fi

exec "$JAVA_EXE" \
  "-Dorg.gradle.appname=gradlew" \
  -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
