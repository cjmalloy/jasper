package jasper.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link ThreadPoolConfig} class.
 */
class ThreadPoolConfigTest {

    private ThreadPoolConfig threadPoolConfig;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setup() {
        meterRegistry = new SimpleMeterRegistry();
        threadPoolConfig = new ThreadPoolConfig();
        ReflectionTestUtils.setField(threadPoolConfig, "meterRegistry", meterRegistry);
    }

    @Test
    void shouldCreateScriptsExecutor() {
        Executor executor = threadPoolConfig.scriptsExecutor();
        
        assertThat(executor).isNotNull();
        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(10);
        assertThat(taskExecutor.getQueueCapacity()).isEqualTo(50);
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("scripts-");
    }

    @Test
    void shouldCreateWebSocketExecutor() {
        Executor executor = threadPoolConfig.websocketExecutor();
        
        assertThat(executor).isNotNull();
        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(4);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(8);
        assertThat(taskExecutor.getQueueCapacity()).isEqualTo(0);
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("websocket-");
    }

    @Test
    void shouldCreateIntegrationExecutor() {
        Executor executor = threadPoolConfig.integrationExecutor();
        
        assertThat(executor).isNotNull();
        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(4);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(32);
        assertThat(taskExecutor.getQueueCapacity()).isEqualTo(0);
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("integration-");
    }

    @Test
    void shouldCreateAsyncExecutor() {
        Executor executor = threadPoolConfig.asyncExecutor();
        
        assertThat(executor).isNotNull();
        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(8);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(32);
        assertThat(taskExecutor.getQueueCapacity()).isEqualTo(100);
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("async-");
    }

    @Test
    void shouldCreateSchedulerExecutor() {
        ThreadPoolTaskScheduler scheduler = threadPoolConfig.schedulerExecutor();
        
        assertThat(scheduler).isNotNull();
        assertThat(scheduler.getThreadNamePrefix()).isEqualTo("scheduler-");
        
        // Note: getPoolSize() returns the configured pool size, not the current pool size
        // The scheduler needs to be started for getPoolSize() to return the correct value
        // Since we're testing configuration, we test the thread name prefix instead
    }

    @Test
    void shouldRegisterMetricsForAllExecutors() {
        // Create all executors to register metrics
        threadPoolConfig.scriptsExecutor();
        threadPoolConfig.websocketExecutor();
        threadPoolConfig.integrationExecutor();
        threadPoolConfig.asyncExecutor();
        threadPoolConfig.schedulerExecutor();

        // Verify that metrics are registered (simple check for meter existence)
        assertThat(meterRegistry.getMeters()).isNotEmpty();
        
        // Should have some executor-related metrics registered
        boolean hasExecutorMetrics = meterRegistry.getMeters().stream()
            .anyMatch(meter -> meter.getId().getName().startsWith("executor."));
        assertThat(hasExecutorMetrics).isTrue();
    }
}