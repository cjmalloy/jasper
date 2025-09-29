package jasper.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link ScriptExecutorFactory} class.
 */
class ScriptExecutorFactoryTest {

	private ScriptExecutorFactory factory;

	@BeforeEach
	void setup() {
		factory = new ScriptExecutorFactory();
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
}
