package jasper.config;

import org.hibernate.dialect.function.SQLFunctionTemplate;
import org.hibernate.dialect.function.StandardSQLFunction;
import org.hibernate.type.DurationType;
import org.hibernate.type.StandardBasicTypes;
import tech.jhipster.domain.util.FixedPostgreSQL95Dialect;

public class PostgreSQLDialect extends FixedPostgreSQL95Dialect {
	public PostgreSQLDialect() {
		super();
		registerFunction("age", new StandardSQLFunction("age", DurationType.INSTANCE));
		registerFunction("jsonb_exists", new SQLFunctionTemplate(StandardBasicTypes.BOOLEAN, "jsonb_exists(?1, ?2)"));
		registerFunction("jsonb_set", new SQLFunctionTemplate(StandardBasicTypes.STRING, "jsonb_set(?1, ?2, ?3, ?4)"));
		registerFunction("cast_to_jsonb", new SQLFunctionTemplate(StandardBasicTypes.STRING, "?1::jsonb"));
	}
}
