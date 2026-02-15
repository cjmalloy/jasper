package jasper.component;

import jasper.config.Props;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IdleTrackerTest {

	IdleTracker idleTracker = new IdleTracker();
	Props props = new Props();

	@BeforeEach
	void setUp() {
		idleTracker.props = props;
	}

	@Test
	void testIdleWhenDisabled() {
		props.setBackfillIdleSec(0);
		assertThat(idleTracker.isIdle()).isTrue();
	}

	@Test
	void testIdleWhenDisabledAfterActivity() {
		props.setBackfillIdleSec(0);
		idleTracker.clearIdle();
		assertThat(idleTracker.isIdle()).isTrue();
	}

	@Test
	void testNotIdleAfterActivity() {
		props.setBackfillIdleSec(60);
		idleTracker.clearIdle();
		assertThat(idleTracker.isIdle()).isFalse();
	}

	@Test
	void testIdleAfterWaiting() throws InterruptedException {
		props.setBackfillIdleSec(1);
		idleTracker.clearIdle();
		assertThat(idleTracker.isIdle()).isFalse();
		Thread.sleep(1100);
		assertThat(idleTracker.isIdle()).isTrue();
	}

	@Test
	void testNotIdleWithLargeIdleTime() {
		props.setBackfillIdleSec(3600);
		assertThat(idleTracker.isIdle()).isFalse();
	}
}
