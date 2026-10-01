#!/bin/sh
# ---------------------------------------------------------------------------
# Maven Wrapper.
#
# Downloads the Maven distribution named in .mvn/wrapper/maven-wrapper.properties
# into the user's Maven home on first use, then runs it. A contributor needs only
# a JDK 17 on PATH - Maven itself is not a prerequisite, and no binary is
# committed to this repository.
#
# Regenerate the official Apache scripts at any time with:
#   mvn -N wrapper:wrapper -Dmaven=3.9.9
# ---------------------------------------------------------------------------
set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
PROPERTIES="$SCRIPT_DIR/.mvn/wrapper/maven-wrapper.properties"

if [ ! -f "$PROPERTIES" ]; then
  echo "mvnw: missing $PROPERTIES" >&2
  exit 1
fi

# The trailing tr strips any backslash: a .properties file may escape the URL as
# "https\://", and a URL never legitimately contains one.
DISTRIBUTION_URL=$(grep '^distributionUrl=' "$PROPERTIES" | cut -d'=' -f2- | tr -d '\r' | tr -d '\\')
if [ -z "$DISTRIBUTION_URL" ]; then
  echo "mvnw: distributionUrl is not set in $PROPERTIES" >&2
  exit 1
fi

ARCHIVE=$(basename "$DISTRIBUTION_URL")
DIST_NAME=$(echo "$ARCHIVE" | sed 's/-bin\.zip$//')

MAVEN_USER_HOME="${MAVEN_USER_HOME:-$HOME/.m2}"
MAVEN_HOME="$MAVEN_USER_HOME/wrapper/dists/$DIST_NAME"

if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  echo "mvnw: downloading $DISTRIBUTION_URL"
  mkdir -p "$MAVEN_HOME"

  if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$MAVEN_HOME/$ARCHIVE" "$DISTRIBUTION_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$MAVEN_HOME/$ARCHIVE" "$DISTRIBUTION_URL"
  else
    echo "mvnw: neither curl nor wget is available to download Maven" >&2
    exit 1
  fi

  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$MAVEN_HOME/$ARCHIVE" -d "$MAVEN_HOME"
  elif command -v tar >/dev/null 2>&1; then
    (cd "$MAVEN_HOME" && tar -xf "$ARCHIVE")
  else
    echo "mvnw: neither unzip nor tar is available to unpack Maven" >&2
    exit 1
  fi

  # The archive unpacks into a single apache-maven-x.y.z directory; flatten it.
  if [ -d "$MAVEN_HOME/$DIST_NAME" ]; then
    (cd "$MAVEN_HOME/$DIST_NAME" && tar cf - .) | (cd "$MAVEN_HOME" && tar xf -)
    rm -rf "$MAVEN_HOME/$DIST_NAME"
  fi

  rm -f "$MAVEN_HOME/$ARCHIVE"
  chmod +x "$MAVEN_HOME/bin/mvn"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
