package jasper.config;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SQLitePropertiesBindingTest {

	@ParameterizedTest
	@ValueSource(strings = {
		"src/main/resources/config/application-sqlite.yml",
		"src/test/resources/config/application-sqlite.yml"
	})
	void leakDetectionThresholdBindsFromYaml(String location) throws IOException {
		var resource = new FileSystemResource(Path.of(location));
		var environment = new StandardEnvironment();

		for (var propertySource : new YamlPropertySourceLoader().load(resource.getFilename(), resource).reversed()) {
			environment.getPropertySources().addFirst(propertySource);
		}

		var properties = Binder.get(environment)
			.bind("spring.datasource.hikari", Bindable.of(HikariConfig.class))
			.orElseThrow(() -> new AssertionError("Expected SQLite Hikari properties to bind"));

		assertThat(properties.getLeakDetectionThreshold()).isEqualTo(10_000L);
	}
}
