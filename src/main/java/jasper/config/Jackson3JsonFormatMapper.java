package jasper.config;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import tools.jackson.databind.ObjectMapper;

public class Jackson3JsonFormatMapper implements FormatMapper {

	private final ObjectMapper objectMapper;

	public Jackson3JsonFormatMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public <T> T fromString(CharSequence charSequence, JavaType<T> javaType, WrapperOptions wrapperOptions) {
		try {
			return objectMapper.readValue(charSequence.toString(), javaType.getJavaTypeClass());
		} catch (Exception e) {
			throw new RuntimeException("Failed to deserialize JSON", e);
		}
	}

	@Override
	public <T> String toString(T value, JavaType<T> javaType, WrapperOptions wrapperOptions) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception e) {
			throw new RuntimeException("Failed to serialize to JSON", e);
		}
	}
}
