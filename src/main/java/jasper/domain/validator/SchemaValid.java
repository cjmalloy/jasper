package jasper.domain.validator;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;
import javax.validation.Constraint;
import javax.validation.Payload;

@Target(FIELD)
@Retention(RUNTIME)
@Constraint(validatedBy = SchemaValidator.class)
@Documented
public @interface SchemaValid {

	String message() default "{schema.invalid}";

	Class<?>[] groups() default { };

	Class<? extends Payload>[] payload() default { };
}
