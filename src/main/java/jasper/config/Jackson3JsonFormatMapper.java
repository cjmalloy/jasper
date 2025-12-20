package jasper.config;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class Jackson3JsonFormatMapper implements FormatMapper {

	private final ObjectMapper objectMapper;

	public Jackson3JsonFormatMapper() {
		this.objectMapper = JsonMapper.builder().build();
	}

	public Jackson3JsonFormatMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public <T> T fromString(CharSequence charSequence, JavaType<T> javaType, WrapperOptions wrapperOptions) {
		return objectMapper.readValue(charSequence.toString(), javaType.getJavaTypeClass());
	}

	@Override
	public <T> String toString(T value, JavaType<T> javaType, WrapperOptions wrapperOptions) {
		return objectMapper.writeValueAsString(value);
	}
}
