package jasper.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.MessageChannel;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for bulkhead message channels in IntegrationConfig.
 */
@SpringBootTest
@ActiveProfiles("test")
class IntegrationConfigTest {

    @Autowired(required = false)
    MessageChannel bulkheadTxChannel;

    @Autowired(required = false)
    MessageChannel bulkheadRxChannel;

    @Test
    void shouldCreateBulkheadChannels() {
        assertThat(bulkheadTxChannel).isNotNull();
        assertThat(bulkheadRxChannel).isNotNull();
    }
}
