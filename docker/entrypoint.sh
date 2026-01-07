#!/bin/bash

if [ -z "$(ls -A /cr 2> /dev/null)" ]; then
  echo "Creating checkpoint..."
  
  # Create log file for application output
  APP_LOG="/tmp/app-startup.log"
  
  # Start the application in the background without automatic checkpoint
  java \
    -Djava.security.egd=file:/dev/./urandom \
    "-Xmx${JASPER_HEAP:-512m}" \
    "-Xms${JASPER_HEAP:-512m}" \
    -XX:+UseStringDeduplication \
    -XX:+UseCompactObjectHeaders \
    -XX:CRaCCheckpointTo=/cr \
    -XX:CRaCMinPid=128 \
    org.springframework.boot.loader.launch.JarLauncher > "$APP_LOG" 2>&1 &
  
  APP_PID=$!
  echo "Application started with PID $APP_PID, waiting for startup..."
  echo "Application logs: $APP_LOG"
  
  # Wait for application to be fully ready (check health endpoint)
  WAIT_TIME=0
  MAX_WAIT=120
  MANAGEMENT_PORT=${MANAGEMENT_PORT:-8081}
  
  until curl -f -s http://localhost:${MANAGEMENT_PORT}/management/health > /dev/null 2>&1; do
    # Check if the process is still running
    if ! kill -0 $APP_PID 2>/dev/null; then
      echo "ERROR: Application process (PID $APP_PID) has exited unexpectedly"
      echo "Last 50 lines of application log:"
      tail -50 "$APP_LOG"
      exit 1
    fi
    
    sleep 2
    WAIT_TIME=$((WAIT_TIME + 2))
    
    if [ $WAIT_TIME -ge $MAX_WAIT ]; then
      echo "ERROR: Application failed to start within ${MAX_WAIT} seconds"
      echo "Last 50 lines of application log:"
      tail -50 "$APP_LOG"
      # Attempt graceful shutdown first
      kill -TERM $APP_PID 2>/dev/null
      sleep 5
      # Force kill if still running
      kill -KILL $APP_PID 2>/dev/null
      exit 1
    fi
    
    # Show progress every 10 seconds with log sample
    if [ $((WAIT_TIME % 10)) -eq 0 ]; then
      echo "Waiting for application startup... (${WAIT_TIME}s/${MAX_WAIT}s)"
      echo "Recent log sample:"
      tail -5 "$APP_LOG"
    fi
  done
  
  echo "Application is ready. Creating checkpoint..."
  # Trigger manual checkpoint using jcmd
  jcmd $APP_PID JDK.checkpoint
  
  # Wait for checkpoint to complete (process will exit)
  wait $APP_PID
  CHECKPOINT_EXIT_CODE=$?
  
  if [ $CHECKPOINT_EXIT_CODE -eq 0 ]; then
    echo "Checkpoint created successfully"
  else
    echo "ERROR: Checkpoint creation failed with exit code $CHECKPOINT_EXIT_CODE"
    echo "Last 50 lines of application log:"
    tail -50 "$APP_LOG"
    exit $CHECKPOINT_EXIT_CODE
  fi
fi

echo "Restoring the application..."
java \
  -Djava.security.egd=file:/dev/./urandom \
  "-Xmx${JASPER_HEAP:-512m}" \
  "-Xms${JASPER_HEAP:-512m}" \
  -XX:CRaCRestoreFrom=/cr \
  org.springframework.boot.loader.launch.JarLauncher
