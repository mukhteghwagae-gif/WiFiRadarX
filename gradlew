#!/bin/sh

#
# Copyright 2015 the original author or authors.
# Licensed under the Apache License, Version 2.0
#

APP_HOME=$( cd "${0%/*}" && pwd -P )
APP_BASE_NAME=${0##*/}

# Add default JVM options — NO quotes around individual opts
DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

warn() { echo "$*" >&2; }
die()  { echo; echo "$*" >&2; echo; exit 1; }

# Find java
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
    [ -x "$JAVACMD" ] || die "ERROR: JAVA_HOME points to invalid dir: $JAVA_HOME"
else
    JAVACMD=java
    command -v java > /dev/null 2>&1 || \
        die "ERROR: JAVA_HOME not set and 'java' not found in PATH."
fi

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Build argument list — each token is a separate word, no embedded quotes
set -- \
    $DEFAULT_JVM_OPTS \
    $JAVA_OPTS \
    $GRADLE_OPTS \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"

exec "$JAVACMD" "$@"
