package jasper.config;

import org.hibernate.dialect.function.StandardSQLFunction;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.type.StandardBasicTypes;

public class PostgreSQLDialect extends org.hibernate.dialect.PostgreSQLDialect {
	@Override
	public void initializeFunctionRegistry(QueryEngine queryEngine) {
		super.initializeFunctionRegistry(queryEngine);
		var functionRegistry = queryEngine.getSqmFunctionRegistry();
		var bool = queryEngine.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.BOOLEAN);
		var str = queryEngine.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.STRING);
		functionRegistry.register("age", new StandardSQLFunction("age", StandardBasicTypes.DURATION));
		functionRegistry.registerPattern("jsonb_exists", "jsonb_exists(?1, ?2)", bool);
		functionRegistry.registerPattern("jsonb_set", "jsonb_set(?1, ?2, ?3, ?4)", str);
		functionRegistry.registerPattern("cast_to_jsonb", "?1::jsonb", str);
	}

}
