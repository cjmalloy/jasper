package jasper.config;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Custom JSON format mapper for Hibernate that uses Jackson 3 (tools.jackson.*)
 * instead of Jackson 2 (com.fasterxml.jackson.*).
 */
public class Jackson3JsonFormatMapper implements FormatMapper {

	private final JsonMapper jsonMapper;

	public Jackson3JsonFormatMapper(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public <T> T fromString(CharSequence charSequence, JavaType<T> javaType, WrapperOptions wrapperOptions) {
		if (charSequence == null) {
			return null;
		}
		try {
			return jsonMapper.readValue(charSequence.toString(), 
				jsonMapper.getTypeFactory().constructType(javaType.getJavaType()));
		} catch (JacksonException e) {
			throw new IllegalArgumentException("Could not deserialize string to java type: " + javaType.getJavaType(), e);
		}
	}

	@Override
	public <T> String toString(T value, JavaType<T> javaType, WrapperOptions wrapperOptions) {
		if (value == null) {
			return null;
		}
		try {
			return jsonMapper.writeValueAsString(value);
		} catch (JacksonException e) {
			throw new IllegalArgumentException("Could not serialize object of java type: " + javaType.getJavaType(), e);
		}
	}
}
