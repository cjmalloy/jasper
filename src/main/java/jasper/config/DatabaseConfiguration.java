package jasper.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.json.JsonMapper;

import static org.hibernate.cfg.MappingSettings.JSON_FORMAT_MAPPER;

@Configuration
@EnableJpaRepositories({ "jasper.repository" })
@EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
@EnableTransactionManagement
public class DatabaseConfiguration {

	@Autowired
	JsonMapper mapper;

	@Bean
	public HibernatePropertiesCustomizer hibernateCustomizer() {
		return (properties) -> properties.put(
			JSON_FORMAT_MAPPER, new Jackson3JsonFormatMapper(mapper)
		);
	}
}
