# Preload Component File Watching - Manual Test Guide

## Overview
The Preload component now watches the preload folder for changes and automatically loads new or changed zip files.

## Test Setup

### 1. Start the Application with Preload Profile

Using Docker Compose (recommended):
```bash
docker compose up
```

Or locally with preload profile:
```bash
SPRING_PROFILES_ACTIVE=dev,storage,preload \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/jasper \
SPRING_DATASOURCE_USERNAME=jasper \
SPRING_DATASOURCE_PASSWORD=jasper \
./mvnw spring-boot:run
```

### 2. Prepare Test Zip Files

Create a test zip file with sample data:
```bash
# Create sample JSON files
mkdir -p /tmp/test-preload
echo '[]' > /tmp/test-preload/ref.json
echo '[]' > /tmp/test-preload/ext.json
echo '[]' > /tmp/test-preload/user.json
echo '[]' > /tmp/test-preload/plugin.json
echo '[]' > /tmp/test-preload/template.json

# Create zip file
cd /tmp/test-preload
zip test-data.zip *.json
```

## Test Scenarios

### Test 1: Load Existing Files on Startup

**Steps:**
1. Copy test zip to preload directory before starting the app:
   ```bash
   mkdir -p /var/lib/jasper/preload
   cp /tmp/test-preload/test-data.zip /var/lib/jasper/preload/
   ```

2. Start the application

**Expected Results:**
- Application logs should show:
  ```
  Preloading static files test-data.zip
  Watching preload directory: /var/lib/jasper/preload
  Finished Preload in PT...
  ```

### Test 2: Detect New Files

**Steps:**
1. With application running, copy a new zip file:
   ```bash
   cp /tmp/test-preload/test-data.zip /var/lib/jasper/preload/new-file.zip
   ```

2. Wait up to 5 seconds (scheduled polling interval)

**Expected Results:**
- Application logs should show:
  ```
  Detected new file: new-file.zip
  Preloading static files new-file.zip
  Finished Preload in PT...
  ```

### Test 3: Detect Changed Files

**Steps:**
1. Modify an existing zip file:
   ```bash
   # Add more data to make file size different
   echo '{"url": "test"}' > /tmp/test-preload/ref.json
   cd /tmp/test-preload
   zip -u /var/lib/jasper/preload/test-data.zip ref.json
   ```

2. Wait up to 5 seconds

**Expected Results:**
- Application logs should show:
  ```
  Detected changed file: test-data.zip
  Preloading static files test-data.zip
  Finished Preload in PT...
  ```

### Test 4: Ignore Unchanged Files

**Steps:**
1. Touch an existing file without changing content:
   ```bash
   touch /var/lib/jasper/preload/test-data.zip
   ```

2. Wait up to 5 seconds

**Expected Results:**
- No preloading should occur
- File size hasn't changed, so it's skipped

### Test 5: WatchService Real-time Detection

**Steps:**
1. Create a new file and observe timing:
   ```bash
   time cp /tmp/test-preload/test-data.zip /var/lib/jasper/preload/realtime-test.zip
   ```

2. Observe log timestamps

**Expected Results:**
- File should be detected almost immediately (< 1 second) via WatchService
- If WatchService is unavailable, fallback polling will detect within 5 seconds

## Monitoring

### View Logs
```bash
# Docker
docker compose logs -f app

# Local
tail -f logs/jasper.log
```

### Key Log Messages

- **Startup**: `Watching preload directory: /var/lib/jasper/preload`
- **New File**: `Detected new file: <filename>`
- **Changed File**: `Detected changed file: <filename>`
- **Loading**: `Preloading static files <filename>`
- **Complete**: `Finished Preload in PT...`
- **Debug Events**: `File event detected: ENTRY_CREATE <filename>` (requires debug logging)

### Enable Debug Logging

Add to application.yml or environment:
```yaml
logging:
  level:
    jasper.component.Preload: DEBUG
```

Or via environment variable:
```bash
LOGGING_LEVEL_JASPER_COMPONENT_PRELOAD=DEBUG
```

## Troubleshooting

### WatchService Not Working
- **Symptom**: Files only detected every 5 seconds
- **Cause**: WatchService may not be supported on some filesystems (e.g., network mounts)
- **Solution**: Polling fallback is active, no action needed

### Directory Not Found
- **Symptom**: `Preload directory does not exist`
- **Solution**: Create the directory manually:
  ```bash
  mkdir -p /var/lib/jasper/preload
  ```

### Files Not Loading
- **Check**: File must have `.zip` extension (case-insensitive)
- **Check**: File must be in correct directory: `/var/lib/jasper/<origin>/preload/`
- **Check**: Storage profile must be active

## Cleanup

```bash
# Remove test files
rm -rf /var/lib/jasper/preload/*.zip

# Stop containers
docker compose down
```

## Implementation Details

### Technical Features
- **Dual Detection**: WatchService for real-time + Scheduled polling (5s) as fallback
- **Change Detection**: Uses file size to determine if file changed
- **Thread Safety**: ConcurrentHashMap for tracking loaded files
- **Resource Cleanup**: Proper @PreDestroy cleanup of WatchService
- **Error Handling**: Graceful handling of missing directories and I/O errors

### Scheduling
- Interval: 5 seconds (`@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)`)
- Can be customized by modifying the annotation in Preload.java

### File Tracking
- Files tracked by ID (filename) and size
- Reloaded only when: new file OR size changed
- Removed from tracking when file deleted
