package jasper.domain.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsontypedef.jtd.InvalidSchemaException;
import com.jsontypedef.jtd.Schema;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SchemaValidator implements ConstraintValidator<SchemaValid, JsonNode> {
	public static final ObjectMapper mapper = new ObjectMapper();

	@Override
	public boolean isValid(JsonNode value, ConstraintValidatorContext context) {
		if (value == null) return true;
		try {
			mapper.convertValue(value, Schema.class).verify();
			return true;
		} catch (IllegalArgumentException | InvalidSchemaException e) {
			// IllegalArgumentException: not shaped like a JTD schema (unknown type, wrong field types)
			return false;
		}
	}
}
