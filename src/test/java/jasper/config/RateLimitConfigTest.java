package jasper.config;

import jasper.component.ConfigCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringJUnitConfig
@ActiveProfiles({"test", "limit"})
class RateLimitConfigTest {

    @Autowired
    ConfigCache configCache;

    @Autowired(required = false)
    RateLimitConfig rateLimitConfig;

    @Test
    void testRateLimitConfigIsLoadedWhenLimitProfileActive() {
        assertThat(rateLimitConfig).isNotNull();
        assertThat(rateLimitConfig.rateLimitInterceptor()).isNotNull();
    }

    @Test
    void testServerConfigContainsRateLimitSettings() {
        var serverConfig = configCache.root();
        assertThat(serverConfig.getMaxConcurrentRequestsPerOrigin()).isEqualTo(100);
        assertThat(serverConfig.getMaxConcurrentScriptsPerOrigin()).isEqualTo(10);
        assertThat(serverConfig.getMaxConcurrentCronScriptsPerOrigin()).isEqualTo(5);
        assertThat(serverConfig.getMaxConcurrentReplicationPerOrigin()).isEqualTo(3);
        assertThat(serverConfig.getMaxConcurrentRssScrapePerOrigin()).isEqualTo(5);
    }
}