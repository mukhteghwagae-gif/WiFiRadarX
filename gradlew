#!/bin/sh
#
# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0
#
APP_NAME="WiFiRadarX"
APP_BASE_NAME=$(basename "$0")

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

set -e

CDPATH=""

warn() {
    echo "$*"
}

die() {
    echo
    echo "$*"
    echo
    exit 1
}

if [ "$APP_HOME" ]; then
    APP_HOME=$(dirname "$(readlink -f "$0" 2>/dev/null || echo "$0")")
else
    APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
fi

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

JAVACMD=${JAVA_HOME:+$JAVA_HOME/bin/}java
[ -n "$JAVA_HOME" ] || warn "JAVA_HOME is not set; results may not be as expected."

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
