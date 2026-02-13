package jasper.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.StandardBasicTypes;

public class SQLiteDialect extends org.hibernate.community.dialect.SQLiteDialect {
	@Override
	public void initializeFunctionRegistry(FunctionContributions functionContributions) {
		super.initializeFunctionRegistry(functionContributions);
		var functionRegistry = functionContributions.getFunctionRegistry();
		var string = functionContributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.STRING);
		var bool = functionContributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.BOOLEAN);
		var integer = functionContributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.INTEGER);
		var doubleType = functionContributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.DOUBLE);
		var jsonb = functionContributions.getTypeConfiguration().getBasicTypeRegistry().resolve(Object.class, SqlTypes.JSON);
		// jsonb_exists: check if a JSON array or object keys contain the given value
		functionRegistry.registerPattern("jsonb_exists", "(?2 IN (SELECT je.value FROM json_each(?1) je))", bool);
		// jsonb_exists_any: check if a JSON array contains any of the values in a comma-separated list
		functionRegistry.registerPattern("jsonb_exists_any", "(EXISTS (SELECT 1 FROM json_each(?1) je WHERE INSTR(',' || ?2 || ',', ',' || je.value || ',') > 0))", bool);
		// jsonb_extract_path: extract a JSON value at a dotted path (used with simple keys)
		functionRegistry.registerPattern("jsonb_extract_path", "json_extract(?1, '$.' || ?2)", jsonb);
		functionRegistry.registerPattern("jsonb_extract_path_text", "CAST(json_extract(?1, '$.' || ?2) AS TEXT)", string);
		// jsonb_object_field: get a JSON field by key (equivalent to PostgreSQL's -> operator)
		functionRegistry.registerPattern("jsonb_object_field", "json_extract(?1, '$.' || ?2)", jsonb);
		// jsonb_object_field_text: get a JSON field as text (equivalent to PostgreSQL's ->> operator)
		functionRegistry.registerPattern("jsonb_object_field_text", "CAST(json_extract(?1, '$.' || ?2) AS TEXT)", string);
		// jsonb_set: set a JSON value at a path, converting PostgreSQL path {key} to SQLite $.key
		functionRegistry.registerPattern("jsonb_set", "json_set(?1, '$.' || REPLACE(REPLACE(?2, '{', ''), '}', ''), ?3)", jsonb);
		// cast_to_jsonb: cast text to JSON
		functionRegistry.registerPattern("cast_to_jsonb", "json(?1)", jsonb);
		// jsonb_concat: merge two JSON objects (like PostgreSQL's || operator for objects)
		functionRegistry.registerPattern("jsonb_concat", "json_patch(?1, ?2)", jsonb);
		// cast_to_int / cast_to_numeric
		functionRegistry.registerPattern("cast_to_int", "CAST((?1) AS INTEGER)", integer);
		functionRegistry.registerPattern("cast_to_numeric", "CAST((?1) AS REAL)", doubleType);
		// jsonb_array_length
		functionRegistry.registerPattern("jsonb_array_length", "json_array_length(?1)", integer);
		// jsonb_array_element_text: get element at index from a JSON array
		functionRegistry.registerPattern("jsonb_array_element_text", "json_extract(?1, '$[' || ?2 || ']')", string);
		// string_to_array: in SQLite just pass through the comma-separated string (used with jsonb_exists_any)
		functionRegistry.registerPattern("string_to_array", "?1", string);
		// origin_nesting: returns 0 for blank or '@', otherwise count of '.' + 1
		functionRegistry.registerPattern("origin_nesting", "CASE WHEN ?1 = '' OR ?1 = '@' THEN 0 ELSE (LENGTH(?1) - LENGTH(REPLACE(?1, '.', '')) + 1) END", integer);
		// tag_levels: returns 0 for blank tag, otherwise count of '/' + 1
		functionRegistry.registerPattern("tag_levels", "CASE WHEN ?1 = '' THEN 0 ELSE (LENGTH(?1) - LENGTH(REPLACE(?1, '/', '')) + 1) END", integer);
		// Vote sorting functions using SQLite's json_extract
		functionRegistry.registerPattern("vote_top", "COALESCE(CAST(json_extract(?1, '$.plugins.plugin/user/vote/up') AS INTEGER), 0) + COALESCE(CAST(json_extract(?1, '$.plugins.plugin/user/vote/down') AS INTEGER), 0)", integer);
		functionRegistry.registerPattern("vote_score", "COALESCE(CAST(json_extract(?1, '$.plugins.plugin/user/vote/up') AS INTEGER), 0) - COALESCE(CAST(json_extract(?1, '$.plugins.plugin/user/vote/down') AS INTEGER), 0)", doubleType);
		// vote_decay: simplified decay formula for SQLite (uses julianday for time difference)
		functionRegistry.registerPattern("vote_decay", "(3 + COALESCE(CAST(json_extract(?1, '$.plugins.plugin/user/vote/up') AS INTEGER), 0) - COALESCE(CAST(json_extract(?1, '$.plugins.plugin/user/vote/down') AS INTEGER), 0)) * 1.0 / (1 + (julianday('now') - julianday(?2)) * 6)", doubleType);
		// Collation function for binary sorting (SQLite uses BINARY collation)
		functionRegistry.registerPattern("collate_c", "(?1) COLLATE BINARY", string);
		// Full-text search: SQLite doesn't have tsvector/tsquery, use LIKE-based fallback
		functionRegistry.registerPattern("websearch_to_tsquery", "?1", string);
		functionRegistry.registerPattern("ts_match_vq", "(COALESCE(?1, '') LIKE '%' || ?2 || '%')", bool);
		functionRegistry.registerPattern("ts_rank_cd", "0", doubleType);
	}
}
