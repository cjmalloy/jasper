#!/bin/bash

if [ -z "$(ls -A /cr 2> /dev/null)" ]; then
  echo "Creating checkpoint..."
  # Use onStartup checkpoint mode to avoid open connections
  java \
    -Djava.security.egd=file:/dev/./urandom \
    "-Xmx${JASPER_HEAP:-512m}" \
    "-Xms${JASPER_HEAP:-512m}" \
    -XX:+UseStringDeduplication \
    -XX:+UseCompactObjectHeaders \
    -Dspring.context.checkpoint=onStartup \
    -XX:CRaCCheckpointTo=/cr \
    -XX:CRaCMinPid=128 \
    org.springframework.boot.loader.launch.JarLauncher
fi

echo "Restoring the application..."
# Do NOT include UseStringDeduplication or UseCompactObjectHeaders on restore
java \
  -Djava.security.egd=file:/dev/./urandom \
  "-Xmx${JASPER_HEAP:-512m}" \
  "-Xms${JASPER_HEAP:-512m}" \
  -XX:CRaCRestoreFrom=/cr \
  org.springframework.boot.loader.launch.JarLauncher
