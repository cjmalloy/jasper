package jasper.domain.validator;

import com.jsontypedef.jtd.InvalidSchemaException;
import com.jsontypedef.jtd.Schema;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import tools.jackson.databind.JsonNode;

import static jasper.config.JacksonConfiguration.om;

public class SchemaValidator implements ConstraintValidator<SchemaValid, JsonNode> {

	@Override
	public boolean isValid(JsonNode value, ConstraintValidatorContext context) {
		var schema = om().convertValue(value, Schema.class);
		if (value == null) return true;
		try {
			schema.verify();
			return true;
		} catch (InvalidSchemaException e) {
			return false;
		}
	}
}
