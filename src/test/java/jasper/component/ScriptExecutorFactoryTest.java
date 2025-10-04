package jasper.component;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jasper.config.Props;
import jasper.domain.Metadata;
import jasper.plugin.Security;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link ScriptExecutorFactory} class.
 */
class ScriptExecutorFactoryTest {

    ScriptExecutorFactory factory;
    ConfigCache configCache;

    @BeforeEach
    void setup() {
        factory = new ScriptExecutorFactory();
        factory.meterRegistry = new SimpleMeterRegistry();
        
        // Mock ConfigCache to return a Security config with a script limit
        configCache = mock(ConfigCache.class);
        var security = new Security();
        security.setScriptLimit(10);
        var metadata = new Metadata();
        metadata.setConfig(security);
        
        when(configCache.security(anyString())).thenReturn(metadata);
        factory.configs = configCache;
    }

    @Test
    void shouldCreateVirtualThreadExecutor() {
        ExecutorService executor = factory.get("testTag", "testOrigin");

        assertThat(executor).isNotNull();
        verifyVirtualThreadExecutor(executor);
    }

    @Test
    void shouldReuseSameExecutorForSameTagAndOrigin() {
        ExecutorService executor1 = factory.get("testTag", "testOrigin");
        ExecutorService executor2 = factory.get("testTag", "testOrigin");

        assertThat(executor1).isSameAs(executor2);
    }

    @Test
    void shouldCreateDifferentExecutorForDifferentTagOrOrigin() {
        ExecutorService executor1 = factory.get("tag1", "origin1");
        ExecutorService executor2 = factory.get("tag2", "origin1");
        ExecutorService executor3 = factory.get("tag1", "origin2");

        assertThat(executor1).isNotSameAs(executor2);
        assertThat(executor1).isNotSameAs(executor3);
        assertThat(executor2).isNotSameAs(executor3);
    }

    /**
     * Verify that the executor uses virtual threads by submitting a task and checking thread properties.
     */
    private void verifyVirtualThreadExecutor(ExecutorService executor) {
        AtomicBoolean isVirtual = new AtomicBoolean(false);
        try {
            executor.submit(() -> {
                isVirtual.set(Thread.currentThread().isVirtual());
            }).get();
            assertThat(isVirtual.get()).isTrue();
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify virtual thread executor", e);
        }
    }
}
