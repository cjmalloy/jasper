package jasper.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import jasper.component.ConfigCache;
import jasper.service.dto.BulkheadDto;
import jasper.service.dto.TemplateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;

import static jasper.component.Messages.originHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link BulkheadConfiguration} class.
 */
@ExtendWith(MockitoExtension.class)
class BulkheadConfigurationTest {

    @Mock
    ConfigCache configs;

    @Mock
    ConfigCache.RootConfig rootConfig;

    @Mock
    BulkheadRegistry registry;

    @Mock
    MessageChannel bulkheadTxChannel;

    BulkheadConfiguration bulkheadConfiguration;

    @BeforeEach
    void setup() {
        bulkheadConfiguration = new BulkheadConfiguration();
        bulkheadConfiguration.configs = configs;
        bulkheadConfiguration.registry = registry;
        bulkheadConfiguration.bulkheadTxChannel = bulkheadTxChannel;

        when(configs.root()).thenReturn(rootConfig);
        when(rootConfig.getMaxConcurrentRequests()).thenReturn(100);
        when(rootConfig.getMaxConcurrentScripts()).thenReturn(10);
        when(rootConfig.getMaxConcurrentReplication()).thenReturn(5);
        when(rootConfig.getMaxConcurrentFetch()).thenReturn(20);
    }

    @Test
    void shouldCreateHttpBulkhead() {
        when(registry.bulkhead(eq("http"), any(BulkheadConfig.class)))
            .thenReturn(Bulkhead.of("http", BulkheadConfig.custom().maxConcurrentCalls(100).build()));

        Bulkhead bulkhead = bulkheadConfiguration.httpBulkhead();

        assertThat(bulkhead).isNotNull();
        assertThat(bulkhead.getName()).isEqualTo("http");
        verify(registry).bulkhead(eq("http"), any(BulkheadConfig.class));
    }

    @Test
    void shouldPublishBulkheadUpdateOnTemplateChange() {
        // Create mocked bulkheads
        Bulkhead httpBulkhead = Bulkhead.of("http", BulkheadConfig.custom().maxConcurrentCalls(100).build());
        Bulkhead scriptBulkhead = Bulkhead.of("script", BulkheadConfig.custom().maxConcurrentCalls(10).build());
        Bulkhead replBulkhead = Bulkhead.of("repl", BulkheadConfig.custom().maxConcurrentCalls(5).build());
        Bulkhead fetchBulkhead = Bulkhead.of("fetch", BulkheadConfig.custom().maxConcurrentCalls(20).build());

        when(registry.bulkhead(eq("http"), any(BulkheadConfig.class))).thenReturn(httpBulkhead);
        when(registry.bulkhead(eq("script"), any(BulkheadConfig.class))).thenReturn(scriptBulkhead);
        when(registry.bulkhead(eq("repl"), any(BulkheadConfig.class))).thenReturn(replBulkhead);
        when(registry.bulkhead(eq("fetch"), any(BulkheadConfig.class))).thenReturn(fetchBulkhead);

        // Initialize bulkheads
        bulkheadConfiguration.httpBulkhead();
        bulkheadConfiguration.scriptBulkhead();
        bulkheadConfiguration.replBulkhead();
        bulkheadConfiguration.fetchBulkhead();

        when(bulkheadTxChannel.send(any())).thenReturn(true);

        // Create template update message
        TemplateDto template = new TemplateDto();
        template.setTag("_config/server");
        Message<TemplateDto> message = MessageBuilder.createMessage(template, originHeaders(""));

        // Handle template update
        bulkheadConfiguration.handleTemplateUpdate(message);

        // Verify bulkhead updates were published
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(bulkheadTxChannel, times(4)).send(messageCaptor.capture());

        // Verify the published messages contain BulkheadDto
        var publishedMessages = messageCaptor.getAllValues();
        assertThat(publishedMessages).hasSize(4);
        assertThat(publishedMessages.get(0).getPayload()).isInstanceOf(BulkheadDto.class);
    }

    @Test
    void shouldLogBulkheadUpdateFromOtherNode() {
        BulkheadDto dto = BulkheadDto.builder()
            .name("http")
            .maxConcurrentCalls(200)
            .build();

        Message<BulkheadDto> message = MessageBuilder.createMessage(dto, originHeaders("remote-origin"));

        // This should just log, not throw
        bulkheadConfiguration.handleBulkheadUpdate(message);
    }

    @Test
    void shouldUpdateBulkheadConfig() {
        Bulkhead bulkhead = Bulkhead.of("test", BulkheadConfig.custom().maxConcurrentCalls(10).build());

        BulkheadConfiguration.updateBulkheadConfig(bulkhead, 20);

        assertThat(bulkhead.getBulkheadConfig().getMaxConcurrentCalls()).isEqualTo(20);
    }
}
