#!/bin/bash

if [ -z "$(ls -A /cr 2> /dev/null)" ]; then
  echo "Creating checkpoint..."
  java \
    -Djava.security.egd=file:/dev/./urandom \
    "-Xmx${JASPER_HEAP:-512m}" \
    "-Xms${JASPER_HEAP:-512m}" \
    -XX:+UseStringDeduplication \
    -XX:+UseCompactObjectHeaders \
    -Dspring.context.checkpoint=onRefresh \
    -XX:CRaCCheckpointTo=/cr \
    -XX:CRaCMinPid=128 \
    org.springframework.boot.loader.launch.JarLauncher
fi

echo "Restoring the application..."
java \
  -Djava.security.egd=file:/dev/./urandom \
  "-Xmx${JASPER_HEAP:-512m}" \
  "-Xms${JASPER_HEAP:-512m}" \
  -XX:CRaCRestoreFrom=/cr \
  org.springframework.boot.loader.launch.JarLauncher
