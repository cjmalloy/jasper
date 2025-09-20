package jasper.component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link ScriptExecutorFactory} class.
 */
class ScriptExecutorFactoryTest {

	private ScriptExecutorFactory factory;
	private MeterRegistry meterRegistry;

	@BeforeEach
	void setup() {
		meterRegistry = new SimpleMeterRegistry();
		factory = new ScriptExecutorFactory();
		ReflectionTestUtils.setField(factory, "meterRegistry", meterRegistry);
	}

	@Test
	void shouldCreateDeltaExecutor() {
		Executor executor = factory.get("delta", "testOrigin");

		assertThat(executor).isNotNull();
		assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);

		ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
		assertThat(taskExecutor.getCorePoolSize()).isEqualTo(2);
		assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(10);
		assertThat(taskExecutor.getQueueCapacity()).isEqualTo(25);
		assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("script-delta-testOrigin-");
	}

	@Test
	void shouldCreateCronExecutor() {
		Executor executor = factory.get("cron", "testOrigin");

		assertThat(executor).isNotNull();
		assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);

		ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
		assertThat(taskExecutor.getCorePoolSize()).isEqualTo(2);
		assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(10);
		assertThat(taskExecutor.getQueueCapacity()).isEqualTo(25);
		assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("script-cron-testOrigin-");
	}

	@Test
	void shouldCreatePythonExecutor() {
		Executor executor = factory.get("python", "testOrigin");

		assertThat(executor).isNotNull();
		assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);

		ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
		assertThat(taskExecutor.getCorePoolSize()).isEqualTo(2);
		assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(10);
		assertThat(taskExecutor.getQueueCapacity()).isEqualTo(25);
		assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("script-python-testOrigin-");
	}

	@Test
	void shouldCreateJavaScriptExecutor() {
		Executor executor = factory.get("javascript", "testOrigin");

		assertThat(executor).isNotNull();
		assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);

		ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
		assertThat(taskExecutor.getCorePoolSize()).isEqualTo(2);
		assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(10);
		assertThat(taskExecutor.getQueueCapacity()).isEqualTo(25);
		assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("script-javascript-testOrigin-");
	}

	@Test
	void shouldCreateDefaultExecutorForUnknownType() {
		Executor executor = factory.get("unknown", "testOrigin");

		assertThat(executor).isNotNull();
		assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);

		ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
		assertThat(taskExecutor.getCorePoolSize()).isEqualTo(2);
		assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(10);
		assertThat(taskExecutor.getQueueCapacity()).isEqualTo(25);
		assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("script-unknown-testOrigin-");
	}

	@Test
	void shouldReuseExecutorForSameTypeAndOrigin() {
		Executor executor1 = factory.get("delta", "testOrigin");
		Executor executor2 = factory.get("delta", "testOrigin");

		assertThat(executor1).isSameAs(executor2);
	}

	@Test
	void shouldCreateSeparateExecutorsForDifferentOrigins() {
		Executor executor1 = factory.get("delta", "origin1");
		Executor executor2 = factory.get("delta", "origin2");

		assertThat(executor1).isNotSameAs(executor2);
	}

	@Test
	void shouldCreateSeparateExecutorsForDifferentTypes() {
		Executor deltaExecutor = factory.get("delta", "testOrigin");
		Executor cronExecutor = factory.get("cron", "testOrigin");

		assertThat(deltaExecutor).isNotSameAs(cronExecutor);
	}

	@Test
	void shouldHandleNullOrigin() {
		Executor executor = factory.get("delta", null);

		assertThat(executor).isNotNull();
		assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);

		ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
		assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("script-delta-");
	}

	@Test
	void shouldProvideExecutorStats() {
		// Create some executors
		factory.get("delta", "origin1");
		factory.get("cron", "origin1");
		factory.get("python", "origin2");

		Map<String, ScriptExecutorFactory.ExecutorStats> stats = factory.getExecutorStats();

		assertThat(stats).hasSize(3);
		assertThat(stats).containsKeys("delta:origin1", "cron:origin1", "python:origin2");

		ScriptExecutorFactory.ExecutorStats deltaStats = stats.get("delta:origin1");
		assertThat(deltaStats.corePoolSize()).isEqualTo(2);
		assertThat(deltaStats.maxPoolSize()).isEqualTo(10);
		assertThat(deltaStats.activeCount()).isEqualTo(0);
		assertThat(deltaStats.poolSize()).isEqualTo(0);
		assertThat(deltaStats.queueSize()).isEqualTo(0);
	}

	@Test
	void shouldRegisterMetricsForDynamicExecutors() {
		factory.get("delta", "testOrigin");
		factory.get("cron", "testOrigin");

		// Should have executor metrics registered
		assertThat(meterRegistry.getMeters()).isNotEmpty();

		// Should have some executor-related metrics registered
		boolean hasExecutorMetrics = meterRegistry.getMeters().stream()
			.anyMatch(meter -> meter.getId().getName().startsWith("executor."));
		assertThat(hasExecutorMetrics).isTrue();
	}
}
