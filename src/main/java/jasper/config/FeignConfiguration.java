package jasper.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClientsConfiguration;
import org.springframework.context.annotation.*;

@Configuration
@EnableFeignClients(basePackages = "jasper")
@Import(FeignClientsConfiguration.class)
public class FeignConfiguration {

    /**
     * Set the Feign specific log level to log client REST requests.
     */
    @Bean
    feign.Logger.Level feignLoggerLevel() {
        return feign.Logger.Level.BASIC;
    }
}
