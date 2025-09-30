package jasper.component;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link ScriptExecutorFactory} class.
 */
class ScriptExecutorFactoryTest {

	private ScriptExecutorFactory factory;

	@BeforeEach
	void setup() {
		factory = new ScriptExecutorFactory();
		factory.meterRegistry = new SimpleMeterRegistry();
	}

	@Test
	void shouldCreateDeltaExecutor() {
		ExecutorService executor = factory.get("delta", "testOrigin");

		assertThat(executor).isNotNull();
	}

	@Test
	void shouldCreateCronExecutor() {
		ExecutorService executor = factory.get("cron", "testOrigin");

		assertThat(executor).isNotNull();
	}

	@Test
	void shouldCreatePythonExecutor() {
		ExecutorService executor = factory.get("python", "testOrigin");

		assertThat(executor).isNotNull();
	}

	@Test
	void shouldCreateJavaScriptExecutor() {
		ExecutorService executor = factory.get("javascript", "testOrigin");

		assertThat(executor).isNotNull();
	}

	@Test
	void shouldCreateDefaultExecutorForUnknownType() {
		ExecutorService executor = factory.get("unknown", "testOrigin");

		assertThat(executor).isNotNull();
	}

	@Test
	void shouldReuseExecutorForSameTypeAndOrigin() {
		ExecutorService executor1 = factory.get("delta", "testOrigin");
		ExecutorService executor2 = factory.get("delta", "testOrigin");

		assertThat(executor1).isSameAs(executor2);
	}

	@Test
	void shouldCreateSeparateExecutorsForDifferentOrigins() {
		ExecutorService executor1 = factory.get("delta", "origin1");
		ExecutorService executor2 = factory.get("delta", "origin2");

		assertThat(executor1).isNotSameAs(executor2);
	}

	@Test
	void shouldCreateSeparateExecutorsForDifferentTypes() {
		ExecutorService deltaExecutor = factory.get("delta", "testOrigin");
		ExecutorService cronExecutor = factory.get("cron", "testOrigin");

		assertThat(deltaExecutor).isNotSameAs(cronExecutor);
	}
}
