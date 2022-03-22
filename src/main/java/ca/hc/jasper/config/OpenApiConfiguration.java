package ca.hc.jasper.config;

import org.springdoc.core.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;

@Configuration
@Profile("api-docs")
public class OpenApiConfiguration {

    public static final String API_FIRST_PACKAGE = "ca.hc.jasper.web.api";

    @Bean
    @ConditionalOnMissingBean(name = "apiFirstGroupedOpenAPI")
    public GroupedOpenApi apiFirstGroupedOpenAPI(
        ApplicationProperties applicationProperties
    ) {
        ApplicationProperties.ApiDocs properties = applicationProperties.getApiDocs();
        return GroupedOpenApi
            .builder()
            .group("openapi")
            .packagesToScan(API_FIRST_PACKAGE)
            .pathsToMatch(properties.getDefaultIncludePattern())
            .build();
    }
}
