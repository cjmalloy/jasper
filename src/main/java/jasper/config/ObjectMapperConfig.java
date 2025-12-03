package jasper.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class ObjectMapperConfig {

	@Autowired
	JsonMapper jsonMapper;

	@PostConstruct
	void initStatic() {
		JacksonConfiguration.om = jsonMapper;
	}
}
