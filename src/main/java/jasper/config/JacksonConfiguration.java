package jasper.config;

import com.jsontypedef.jtd.Validator;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.zalando.problem.violations.ConstraintViolationProblemModule;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.datatype.hibernate7.Hibernate7Module;

import java.util.List;

import static tools.jackson.core.StreamReadFeature.*;
import static tools.jackson.core.json.JsonReadFeature.*;
import static tools.jackson.databind.DeserializationFeature.*;

@Configuration
public class JacksonConfiguration implements WebMvcConfigurer {
	static JsonMapper om = null;
	
	private final JsonMapper jsonMapper;
	
	public JacksonConfiguration(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public static JsonMapper om() {
		assert om != null;
		return om;
	}

	@PostConstruct
	void initStatic() {
		JacksonConfiguration.om = jsonMapper;
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
	@Profile("!test")
	public JsonMapper jsonMapper() {
		return JsonMapper.builder()
			.enable(ALLOW_UNESCAPED_CONTROL_CHARS, ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
			.enable(ALLOW_TRAILING_COMMA)
			.enable(ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
			.disable(FAIL_ON_NULL_FOR_PRIMITIVES, FAIL_ON_UNKNOWN_PROPERTIES)
			.addMixIn(ProblemDetail.class, ProblemDetailMixin.class)
			.build();
	}

	@Bean
	@Primary
	@Profile("test")
	public JsonMapper testJonMapper() {
		return JsonMapper.builder()
			.enable(INCLUDE_SOURCE_IN_LOCATION)
			.enable(ALLOW_UNESCAPED_CONTROL_CHARS, ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
			.enable(ALLOW_TRAILING_COMMA)
			.enable(ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
			.disable(FAIL_ON_NULL_FOR_PRIMITIVES, FAIL_ON_UNKNOWN_PROPERTIES)
			.addMixIn(ProblemDetail.class, ProblemDetailMixin.class)
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
	
	@Override
	public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
		// Replace all Jackson 2 converters with Jackson 3 converter using our configured JsonMapper
		converters.removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);
		converters.add(0, new MappingJackson2HttpMessageConverter(
			com.fasterxml.jackson.databind.ObjectMapper.builder()
				.enable(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNESCAPED_CONTROL_CHARS)
				.enable(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
				.enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature())
				.enable(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
				.disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
				.disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.addMixIn(ProblemDetail.class, ProblemDetailJackson2Mixin.class)
				.build()
		));
	}
}
