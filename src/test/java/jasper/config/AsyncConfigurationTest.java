package jasper.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties;
import org.springframework.boot.autoconfigure.task.TaskSchedulingProperties;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link AsyncConfiguration} class.
 */
class AsyncConfigurationTest {

    AsyncConfiguration asyncConfiguration;
    TaskExecutionProperties taskExecutionProperties;
    TaskSchedulingProperties taskSchedulingProperties;

    @BeforeEach
    void setup() {
        asyncConfiguration = new AsyncConfiguration();
        taskExecutionProperties = new TaskExecutionProperties();
        taskExecutionProperties.setThreadNamePrefix("async-");
        taskExecutionProperties.getPool().setMaxSize(32);
        taskExecutionProperties.getPool().setQueueCapacity(100);
        asyncConfiguration.taskExecutionProperties = taskExecutionProperties;
        taskSchedulingProperties = new TaskSchedulingProperties();
        taskSchedulingProperties.setThreadNamePrefix("scheduler-");
        asyncConfiguration.taskSchedulingProperties = taskSchedulingProperties;
    }

    @Test
    void shouldCreateGetScriptsExecutor() {
        Executor executor = asyncConfiguration.getScriptsExecutor();

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
        Executor executor = asyncConfiguration.getWebsocketExecutor();

        assertThat(executor).isNotNull();
        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(4);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(8);
        assertThat(taskExecutor.getQueueCapacity()).isEqualTo(0);
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("websocket-");
    }

    @Test
    void shouldCreateAsyncExecutor() {
        Executor executor = asyncConfiguration.getAsyncExecutor();

        assertThat(executor).isNotNull();
        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(8);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(32);
        assertThat(taskExecutor.getQueueCapacity()).isEqualTo(100);
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("async-");
    }

    @Test
    void shouldCreateGetSchedulerExecutor() {
        ThreadPoolTaskScheduler scheduler = asyncConfiguration.getSchedulerExecutor();

        assertThat(scheduler).isNotNull();
        assertThat(scheduler.getThreadNamePrefix()).isEqualTo("scheduler-");

        // Note: getPoolSize() returns the configured pool size, not the current pool size
        // The scheduler needs to be started for getPoolSize() to return the correct value
        // Since we're testing configuration, we test the thread name prefix instead
    }
}
