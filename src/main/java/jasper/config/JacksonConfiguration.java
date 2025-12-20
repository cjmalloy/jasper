package jasper.config;

import com.jsontypedef.jtd.Validator;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.zalando.problem.violations.ConstraintViolationProblemModule;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.datatype.hibernate7.Hibernate7Module;

import static tools.jackson.core.json.JsonReadFeature.*;
import static tools.jackson.databind.DeserializationFeature.*;

@Configuration
public class JacksonConfiguration {
	static JsonMapper om = null;

	public static JsonMapper om() {
		assert om != null;
		return om;
	}

	@PostConstruct
	void initStatic() {
		JacksonConfiguration.om = jsonMapper();
	}

	public static String dump(Object any) {
        try {
            return om().writeValueAsString(any);
        } catch (JacksonException e) {
			e.printStackTrace();
            return "{error}" + any.getClass().getName();
        }
    }

	@Bean
	@Primary
	public JsonMapper jsonMapper() {
		return JsonMapper.builder()
			.enable(ALLOW_UNESCAPED_CONTROL_CHARS, ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
			.enable(ALLOW_TRAILING_COMMA)
			.enable(ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
			.disable(FAIL_ON_NULL_FOR_PRIMITIVES, FAIL_ON_UNKNOWN_PROPERTIES)
			.build();
	}

	@Bean
	public YAMLMapper yamlMapper() {
		return YAMLMapper.builder()
			.build();
	}

    /*
     * Support for Hibernate types in Jackson.
     */
    @Bean
    public Hibernate7Module hibernate7Module() {
        return new Hibernate7Module();
    }

    /*
     * Module for serialization/deserialization of ConstraintViolationProblem.
     */
    @Bean
    public ConstraintViolationProblemModule constraintViolationProblemModule() {
        return new ConstraintViolationProblemModule();
    }

	@Bean
	public Validator validator() {
		var validator = new Validator();
		validator.setMaxDepth(32);
		validator.setMaxErrors(5);
		return validator;
	}
}
