package jasper.component;

import jasper.config.Props;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Tracks the last time the server received a REST API request (excluding pub/ endpoints).
 * Used by the backfill system to wait until the server is idle.
 */
@Component
public class IdleTracker {

	@Autowired
	Props props;

	private volatile Instant lastActivity = Instant.now();

	/**
	 * Record that the server received a REST API request.
	 */
	public void clearIdle() {
		lastActivity = Instant.now();
	}

	/**
	 * Check if the server has been idle for the configured amount of time.
	 */
	public boolean isIdle() {
		if (props.getBackfillIdleSec() <= 0) return true;
		return Instant.now().isAfter(lastActivity.plusSeconds(props.getBackfillIdleSec()));
	}
}
