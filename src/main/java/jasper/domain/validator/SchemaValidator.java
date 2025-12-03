package jasper.domain.validator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.jsontypedef.jtd.InvalidSchemaException;
import com.jsontypedef.jtd.Schema;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SchemaValidator implements ConstraintValidator<SchemaValid, JsonNode> {
	public static final JsonMapper mapper = new JsonMapper();

	@Override
	public boolean isValid(JsonNode value, ConstraintValidatorContext context) {
		var schema = mapper.convertValue(value, Schema.class);
		if (value == null) return true;
		try {
			schema.verify();
			return true;
		} catch (InvalidSchemaException e) {
			return false;
		}
	}
}
