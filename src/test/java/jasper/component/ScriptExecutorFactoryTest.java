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
        Executor executor = factory.getDeltaExecutor("testOrigin");
        
        assertThat(executor).isNotNull();
        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(8);
        assertThat(taskExecutor.getQueueCapacity()).isEqualTo(20);
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("script-delta-testOrigin-");
    }

    @Test
    void shouldCreateCronExecutor() {
        Executor executor = factory.getCronExecutor("testOrigin");
        
        assertThat(executor).isNotNull();
        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(1);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(4);
        assertThat(taskExecutor.getQueueCapacity()).isEqualTo(10);
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("script-cron-testOrigin-");
    }

    @Test
    void shouldCreatePythonExecutor() {
        Executor executor = factory.getLanguageExecutor("python", "testOrigin");
        
        assertThat(executor).isNotNull();
        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(1);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(6);
        assertThat(taskExecutor.getQueueCapacity()).isEqualTo(15);
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("script-python-testOrigin-");
    }

    @Test
    void shouldCreateJavaScriptExecutor() {
        Executor executor = factory.getLanguageExecutor("javascript", "testOrigin");
        
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
        Executor executor = factory.getExecutorForScript("unknown", "testOrigin");
        
        assertThat(executor).isNotNull();
        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(1);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(4);
        assertThat(taskExecutor.getQueueCapacity()).isEqualTo(10);
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("script-unknown-testOrigin-");
    }

    @Test
    void shouldReuseExecutorForSameTypeAndOrigin() {
        Executor executor1 = factory.getDeltaExecutor("testOrigin");
        Executor executor2 = factory.getDeltaExecutor("testOrigin");
        
        assertThat(executor1).isSameAs(executor2);
    }

    @Test
    void shouldCreateSeparateExecutorsForDifferentOrigins() {
        Executor executor1 = factory.getDeltaExecutor("origin1");
        Executor executor2 = factory.getDeltaExecutor("origin2");
        
        assertThat(executor1).isNotSameAs(executor2);
    }

    @Test
    void shouldCreateSeparateExecutorsForDifferentTypes() {
        Executor deltaExecutor = factory.getDeltaExecutor("testOrigin");
        Executor cronExecutor = factory.getCronExecutor("testOrigin");
        
        assertThat(deltaExecutor).isNotSameAs(cronExecutor);
    }

    @Test
    void shouldHandleNullOrigin() {
        Executor executor = factory.getExecutorForScript("delta", null);
        
        assertThat(executor).isNotNull();
        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("script-delta-");
    }

    @Test
    void shouldProvideExecutorStats() {
        // Create some executors
        factory.getDeltaExecutor("origin1");
        factory.getCronExecutor("origin1");
        factory.getLanguageExecutor("python", "origin2");
        
        Map<String, ScriptExecutorFactory.ExecutorStats> stats = factory.getExecutorStats();
        
        assertThat(stats).hasSize(3);
        assertThat(stats).containsKeys("delta:origin1", "cron:origin1", "python:origin2");
        
        ScriptExecutorFactory.ExecutorStats deltaStats = stats.get("delta:origin1");
        assertThat(deltaStats.corePoolSize()).isEqualTo(2);
        assertThat(deltaStats.maxPoolSize()).isEqualTo(8);
        assertThat(deltaStats.activeCount()).isEqualTo(0);
        assertThat(deltaStats.poolSize()).isEqualTo(0);
        assertThat(deltaStats.queueSize()).isEqualTo(0);
    }

    @Test
    void shouldRegisterMetricsForDynamicExecutors() {
        factory.getDeltaExecutor("testOrigin");
        factory.getCronExecutor("testOrigin");
        
        // Should have executor metrics registered
        assertThat(meterRegistry.getMeters()).isNotEmpty();
        
        // Should have some executor-related metrics registered
        boolean hasExecutorMetrics = meterRegistry.getMeters().stream()
            .anyMatch(meter -> meter.getId().getName().startsWith("executor."));
        assertThat(hasExecutorMetrics).isTrue();
    }
}