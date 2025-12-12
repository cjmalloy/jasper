package jasper.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.dialect.function.StandardSQLFunction;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.StandardBasicTypes;

public class PostgreSQLDialect extends org.hibernate.dialect.PostgreSQLDialect {
	@Override
	public void initializeFunctionRegistry(FunctionContributions functionContributions) {
		super.initializeFunctionRegistry(functionContributions);
		var functionRegistry = functionContributions.getFunctionRegistry();
		var bool = functionContributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.BOOLEAN);
		var integer = functionContributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.INTEGER);
		var doubleType = functionContributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.DOUBLE);
		var jsonb = functionContributions.getTypeConfiguration().getBasicTypeRegistry().resolve(Object.class, SqlTypes.JSON);
		functionRegistry.register("age", new StandardSQLFunction("age", StandardBasicTypes.DURATION));
		functionRegistry.registerPattern("jsonb_exists", "jsonb_exists(?1, ?2)", bool);
		functionRegistry.registerPattern("jsonb_set", "jsonb_set(?1, ?2, ?3, ?4)", jsonb);
		functionRegistry.registerPattern("cast_to_jsonb", "?1::jsonb", jsonb);
		functionRegistry.registerPattern("jsonb_concat", "jsonb_concat(?1, ?2)", jsonb);
		functionRegistry.registerPattern("cast_to_int", "(?1)::integer", integer);
		functionRegistry.registerPattern("cast_to_numeric", "(?1)::numeric", doubleType);
		functionRegistry.registerPattern("jsonb_array_length", "jsonb_array_length(?1)", integer);
		// origin_nesting: returns 0 for blank or '@', otherwise count of '.' + 1
		functionRegistry.registerPattern("origin_nesting", "CASE WHEN ?1 = '' OR ?1 = '@' THEN 0 ELSE (LENGTH(?1) - LENGTH(REPLACE(?1, '.', '')) + 1) END", integer);
		// tag_levels: returns 0 for blank tag, otherwise count of '/' + 1
		functionRegistry.registerPattern("tag_levels", "CASE WHEN ?1 = '' THEN 0 ELSE (LENGTH(?1) - LENGTH(REPLACE(?1, '/', '')) + 1) END", integer);
	}

}
