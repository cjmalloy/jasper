package jasper.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class ObjectMapperConfig {

	@Autowired
	ObjectMapper objectMapper;

	@PostConstruct
	void initStatic() {
		JacksonConfiguration.om = objectMapper;
	}
}
