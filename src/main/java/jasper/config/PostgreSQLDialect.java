package jasper.config;

import org.hibernate.dialect.function.StandardSQLFunction;
import org.hibernate.type.DurationType;
import tech.jhipster.domain.util.FixedPostgreSQL95Dialect;

public class PostgreSQLDialect extends FixedPostgreSQL95Dialect {
	public PostgreSQLDialect() {
		super();
		registerFunction("age", new StandardSQLFunction("age", DurationType.INSTANCE));
	}
}
