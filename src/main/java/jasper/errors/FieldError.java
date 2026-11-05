package jasper.errors;

/**
 * A single plugin or template validation failure. Never contains the submitted value.
 *
 * @param path       JSON Pointer into the submitted data, e.g. {@code /age}
 * @param schemaPath JSON Pointer into the schema, e.g. {@code /properties/age/type}
 * @param code       one of {@code missing}, {@code unexpected}, {@code type}, {@code enum}, {@code elements},
 *                   {@code values}, {@code discriminator}, {@code nullable} or {@code invalid}
 * @param expected   expected type or allowed values, if known
 * @param message    short readable message, e.g. {@code age: expected uint32}
 */
public record FieldError(String path, String schemaPath, String code, String expected, String message) {
}
