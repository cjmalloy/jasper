# Preload Component File Watching Implementation

## Overview

The Preload component has been enhanced to automatically watch the preload folder for changes and load any new or modified zip files without requiring application restart.

## Architecture

### Dual Detection Strategy

The implementation uses a two-pronged approach for maximum reliability:

1. **Java NIO WatchService** (Primary)
   - Real-time file system event monitoring
   - Detects `ENTRY_CREATE` and `ENTRY_MODIFY` events
   - Near-instant detection (< 1 second)
   - May not work on all filesystems (e.g., network mounts)

2. **Scheduled Polling** (Fallback)
   - Runs every 5 seconds via Spring's `@Scheduled`
   - Scans directory for all `.zip` files
   - Ensures no files are missed if WatchService fails
   - Works on all filesystem types

### Change Detection Algorithm

Files are tracked using a `ConcurrentHashMap<String, Long>` mapping:
- **Key**: File ID (filename)
- **Value**: File size in bytes

A file is reloaded when:
1. It's a new file (not in the map)
2. Its size has changed (different from stored value)

This approach:
- Avoids unnecessary reloads of unchanged files
- Is simple and efficient
- Works well for the preload use case where files are rarely modified in place

## Implementation Details

### Component Lifecycle

```java
@PostConstruct
public void init() {
    // 1. Load all existing zip files
    // 2. Record their sizes in loadedFiles map
    // 3. Setup WatchService on preload directory
}

@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
public void checkForChanges() {
    // 1. Poll WatchService for events
    // 2. Process any detected changes
    // 3. Fallback: scan directory for changes
}

@PreDestroy
public void cleanup() {
    // Close WatchService to release resources
}
```

### File Processing Flow

```
File Event/Scan
    ↓
Is .zip file?
    ↓ Yes
Does file exist?
    ↓ Yes
Get current size
    ↓
Compare with stored size
    ↓
Size different OR new file?
    ↓ Yes
Load file with loadStatic()
    ↓
Update stored size in map
```

### Thread Safety

- `ConcurrentHashMap` for file tracking (thread-safe)
- `WatchService.poll()` is thread-safe
- `@Scheduled` methods run in Spring's task executor thread pool
- No synchronization needed due to:
  - Single scheduled method accessing WatchService
  - Thread-safe data structures
  - Idempotent operations

## Configuration

### Enable Preload Profile

The component is activated via Spring profile:

```yaml
spring:
  profiles:
    active: preload,storage
```

Or via environment:
```bash
SPRING_PROFILES_ACTIVE=preload,storage
```

### Adjust Polling Interval

Modify the `@Scheduled` annotation in `Preload.java`:

```java
@Scheduled(fixedDelay = 10, timeUnit = TimeUnit.SECONDS)  // Change to 10 seconds
```

### Storage Location

Configured in `application.yml`:

```yaml
jasper:
  storage: /var/lib/jasper
```

Preload directory: `{storage}/{origin-tenant}/preload/`

## Logging

### Log Levels

- **INFO**: File detection, loading start/finish
- **DEBUG**: Individual file system events
- **ERROR**: Setup failures, loading errors

### Key Messages

| Level | Message | Meaning |
|-------|---------|---------|
| INFO | `Watching preload directory: {path}` | WatchService setup successful |
| INFO | `Detected new file: {filename}` | New zip file found |
| INFO | `Detected changed file: {filename}` | Existing file modified |
| INFO | `Preloading static files {filename}` | Starting to load file |
| INFO | `Finished Preload in {duration}` | Loading completed |
| DEBUG | `File event detected: {event} {filename}` | WatchService event |
| ERROR | `Failed to setup file watcher` | WatchService initialization failed |
| ERROR | `Error preloading {filename}` | File loading failed |

### Enable Debug Logging

```yaml
logging:
  level:
    jasper.component.Preload: DEBUG
```

## Error Handling

### Graceful Degradation

| Scenario | Behavior |
|----------|----------|
| WatchService unavailable | Falls back to polling only |
| Preload directory missing | Logs info, continues without watching |
| Invalid zip file | Logs error, continues watching |
| Storage not configured | Component disabled, logs error |

### Recovery

- Failed WatchService setup doesn't crash the app
- Invalid files are skipped but don't stop monitoring
- Polling ensures eventual consistency even if events are missed

## Performance Considerations

### Memory

- **Overhead**: ~16 bytes per tracked file (String key + Long value)
- **Typical**: < 1KB for most deployments (< 100 files)

### CPU

- **WatchService**: Negligible (event-driven)
- **Polling**: O(n) scan every 5 seconds where n = number of files
- **Impact**: Minimal unless thousands of files

### I/O

- **Startup**: Loads all existing zip files
- **Runtime**: Only loads new/changed files
- **Optimization**: Size comparison avoids unnecessary file reads

## Comparison with Alternatives

### Why not use Spring's FileSystemWatcher?

Spring Boot DevTools includes a FileSystemWatcher, but:
- It's designed for development mode
- Not available in production profiles
- Java NIO WatchService is standard and more appropriate

### Why not use Apache Commons VFS?

- Adds unnecessary dependency
- Java NIO is built-in and sufficient
- Simpler to maintain

### Why not use only WatchService?

- WatchService may fail on some filesystems
- Polling provides guaranteed eventual consistency
- Hybrid approach maximizes reliability

## Migration Notes

### From Previous Version

No configuration changes needed. The component:
- Still loads all files on startup (backward compatible)
- Adds automatic reloading as a bonus feature
- No breaking changes to existing behavior

### For Multi-Tenant Deployments

Each origin has its own preload directory:
- `/var/lib/jasper/{origin-tenant}/preload/`
- Files are loaded with the correct origin context
- Logging includes origin prefix for debugging

## Future Enhancements

Potential improvements (not currently implemented):

1. **Configurable polling interval** via application properties
2. **File deletion detection** with automatic cleanup
3. **MD5/SHA256 checksums** instead of size for change detection
4. **Metrics** for file load times and success rates
5. **Admin API** to trigger manual reload
6. **File validation** before loading (zip structure check)

## References

- [Java NIO WatchService Tutorial](https://docs.oracle.com/javase/tutorial/essential/io/notification.html)
- [Spring @Scheduled Documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#scheduling)
- [ConcurrentHashMap JavaDoc](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentHashMap.html)
