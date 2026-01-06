#!/bin/bash

if [ -z "$(ls -A /cr 2> /dev/null)" ]; then
  echo "Creating checkpoint..."
  
  # Start the application in the background without automatic checkpoint
  java \
    -Djava.security.egd=file:/dev/./urandom \
    "-Xmx${JASPER_HEAP:-512m}" \
    "-Xms${JASPER_HEAP:-512m}" \
    -XX:+UseStringDeduplication \
    -XX:+UseCompactObjectHeaders \
    -XX:CRaCCheckpointTo=/cr \
    -XX:CRaCMinPid=128 \
    org.springframework.boot.loader.launch.JarLauncher &
  
  APP_PID=$!
  echo "Application started with PID $APP_PID, waiting for startup..."
  
  # Wait for application to be fully ready (check health endpoint)
  WAIT_TIME=0
  MAX_WAIT=120
  until curl -f -s http://localhost:8081/management/health > /dev/null 2>&1; do
    sleep 2
    WAIT_TIME=$((WAIT_TIME + 2))
    if [ $WAIT_TIME -ge $MAX_WAIT ]; then
      echo "ERROR: Application failed to start within ${MAX_WAIT} seconds"
      kill $APP_PID 2>/dev/null
      exit 1
    fi
    echo "Waiting for application startup... (${WAIT_TIME}s/${MAX_WAIT}s)"
  done
  
  echo "Application is ready. Creating checkpoint..."
  # Trigger manual checkpoint using jcmd
  jcmd $APP_PID JDK.checkpoint
  
  # Wait for checkpoint to complete (process will exit)
  wait $APP_PID
  echo "Checkpoint created successfully"
fi

echo "Restoring the application..."
java \
  -Djava.security.egd=file:/dev/./urandom \
  "-Xmx${JASPER_HEAP:-512m}" \
  "-Xms${JASPER_HEAP:-512m}" \
  -XX:CRaCRestoreFrom=/cr \
  org.springframework.boot.loader.launch.JarLauncher
