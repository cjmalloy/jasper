package jasper.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class LiquibasePropertiesTest {

    @Test
    void mainApplicationConfigDisablesLiquibaseAnalytics() {
        assertThat(loadYaml("src/main/resources/config/application.yml"))
            .containsEntry("spring.liquibase.analytics-enabled", false);
    }

    @Test
    void testApplicationConfigDisablesLiquibaseAnalytics() {
        assertThat(loadYaml("src/test/resources/config/application.yml"))
            .containsEntry("spring.liquibase.analytics-enabled", false);
    }

    private Properties loadYaml(String path) {
        var factory = new YamlPropertiesFactoryBean();
        factory.setResources(new FileSystemResource(Path.of(path)));
        return factory.getObject();
    }
}
