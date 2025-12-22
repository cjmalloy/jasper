package jasper.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jsontypedef.jtd.Validator;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.zalando.problem.violations.ConstraintViolationProblem;
import org.zalando.problem.violations.ConstraintViolationProblemModule;
import org.zalando.problem.violations.Violation;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.datatype.hibernate7.Hibernate7Module;

import java.util.List;

import static tools.jackson.core.StreamReadFeature.*;
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
			.enable(ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
			.enable(ALLOW_UNESCAPED_CONTROL_CHARS, ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, ALLOW_TRAILING_COMMA)
			.disable(FAIL_ON_NULL_FOR_PRIMITIVES, FAIL_ON_UNKNOWN_PROPERTIES)
			.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
			.addMixIn(ConstraintViolationProblem.class, ConstraintViolationProblemMixIn.class)
			.addMixIn(Violation.class, ViolationMixIn.class)
			.addModule(hibernate7Module())
			.build();
	}

	public interface ConstraintViolationProblemMixIn {
		@JsonProperty("violations")
		List<Violation> getViolations();
	}

	public interface ViolationMixIn {
		@JsonProperty("field")
		String getField();
		@JsonProperty("message")
		String getMessage();
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

	/**
	 * Jackson 2 ObjectMapper for json-patch library compatibility.
	 * The json-patch library uses Jackson 2, so we need a separate ObjectMapper
	 * to bridge between Jackson 3 (used by the application) and Jackson 2.
	 */
	@Bean
	public com.fasterxml.jackson.databind.ObjectMapper jackson2ObjectMapper() {
		return new com.fasterxml.jackson.databind.ObjectMapper();
	}

	@Bean
	public Validator validator() {
		var validator = new Validator();
		validator.setMaxDepth(32);
		validator.setMaxErrors(5);
		return validator;
	}
}
