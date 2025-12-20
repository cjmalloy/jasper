package jasper.config;

import com.jsontypedef.jtd.Validator;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.zalando.problem.violations.ConstraintViolationProblemModule;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.datatype.hibernate7.Hibernate7Module;

import static tools.jackson.core.json.JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER;
import static tools.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS;

@Configuration
public class JacksonConfiguration {
	static JsonMapper om = null;

	public static JsonMapper om() {
		assert om != null;
		return om;
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
	JsonMapper jsonMapper(JsonMapper.Builder builder) {
		return builder.build();
	}

	@Bean("yamlMapper")
	public YAMLMapper yamlMapper() {
		return YAMLMapper.builder()
			.build();
	}

	@Bean
	public JsonMapperBuilderCustomizer jsonCustomizer() {
		return builder -> builder
			.configure(ALLOW_UNESCAPED_CONTROL_CHARS, true)
			.configure(ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true)
			.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
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
