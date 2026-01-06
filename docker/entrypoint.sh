#!/bin/bash

# Common JVM flags that can be used for both checkpoint and restore
COMMON_FLAGS="-Djava.security.egd=file:/dev/./urandom"

# Heap flags - must be set consistently between checkpoint and restore
HEAP_FLAGS="-Xmx${JASPER_HEAP:-512m} -Xms${JASPER_HEAP:-512m}"

# JVM flags that can only be set during checkpoint, not during restore
CHECKPOINT_ONLY_FLAGS="-XX:+UseStringDeduplication -XX:+UseCompactObjectHeaders"

if [ -z "$(ls -A /cr 2> /dev/null)" ]; then
  echo "Creating checkpoint..."
  # Use onStartup checkpoint mode to avoid open connections
  java \
    $COMMON_FLAGS \
    $HEAP_FLAGS \
    $CHECKPOINT_ONLY_FLAGS \
    -Dspring.context.checkpoint=onStartup \
    -XX:CRaCCheckpointTo=/cr \
    -XX:CRaCMinPid=128 \
    org.springframework.boot.loader.launch.JarLauncher
fi

echo "Restoring the application..."
# Do NOT include UseStringDeduplication or UseCompactObjectHeaders on restore
java \
  $COMMON_FLAGS \
  $HEAP_FLAGS \
  -XX:CRaCRestoreFrom=/cr \
  org.springframework.boot.loader.launch.JarLauncher
