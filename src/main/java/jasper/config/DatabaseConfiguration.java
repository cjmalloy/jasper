package jasper.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.json.JsonMapper;

import jakarta.annotation.PostConstruct;

@Configuration
@EnableJpaRepositories({ "jasper.repository" })
@EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
@EnableTransactionManagement
public class DatabaseConfiguration {
	
	@Autowired
	private JpaProperties jpaProperties;
	
	@Autowired
	private JsonMapper jsonMapper;
	
	@PostConstruct
	public void configureHibernate() {
		// Configure Hibernate to use Jackson 3 for JSON serialization instead of Jackson 2
		jpaProperties.getProperties().put(
			"hibernate.type.json_format_mapper",
			new Jackson3JsonFormatMapper(jsonMapper)
		);
	}
}
