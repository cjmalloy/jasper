package jasper.repository.filter;

import jasper.domain.Ref;
import jasper.domain.proj.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.ArrayList;

import static jasper.config.JacksonConfiguration.om;
import static jasper.repository.spec.QualifiedTag.atom;

public class TagQuery {
	private static final Logger logger = LoggerFactory.getLogger(TagQuery.class);

	private ArrayNode ast;

	public TagQuery(String query) {
		parse(query.replaceAll("\\s", ""));
	}

	public Specification<Ref> refSpec() {
		return _refSpec(ast);
	}

	private Specification<Ref> _refSpec(JsonNode ast) {
		var result = Specification.<Ref>unrestricted();
		if (!ast.isArray()) return result;
		var or = true;
		var ands = new ArrayList<Specification<Ref>>();
		for (var i = 0; i < ast.size(); i++) {
			var n = ast.get(i);
			if (n.isString() && ":".equals(n.stringValue())) {
				or = false;
			} else if (n.isString() && "|".equals(n.stringValue())) {
				or = true;
			} else {
				var value = n.isArray() ? _refSpec(n) : atom(n.stringValue()).refSpec();
				if (or && !ands.isEmpty()) {
					result = result.or(ands.stream().reduce(Specification::and).get());
					ands.clear();
				}
				ands.add(value);
			}
		}
		if (!ands.isEmpty()) {
			result = result.or(ands.stream().reduce(Specification::and).get());
		}
		return result;
	}

	public <T extends Tag> Specification<T> spec() {
		return _spec(ast);
	}

	private  <T extends Tag> Specification<T> _spec(JsonNode ast) {
		var result = Specification.<T>unrestricted();
		if (!ast.isArray()) return result;
		var or = true;
		var ands = new ArrayList<Specification<T>>();
		for (var i = 0; i < ast.size(); i++) {
			var n = ast.get(i);
			if (n.isString() && ":".equals(n.stringValue())) {
				or = false;
			} else if (n.isString() && "|".equals(n.stringValue())) {
				or = true;
			} else {
				Specification<T> value = n.isArray() ? _spec(n) : atom(n.stringValue()).spec();
				if (or && !ands.isEmpty()) {
					result = result.or(ands.stream().reduce(Specification::and).get());
					ands.clear();
				}
				ands.add(value);
			}
		}
		if (!ands.isEmpty()) {
			result = result.or(ands.stream().reduce(Specification::and).get());
		}
		return result;
	}

	private void parse(String query) {
		logger.trace(query);
		// TODO: compare performance with https://en.wikipedia.org/wiki/Shunting-yard_algorithm
		var array = ("[\"" + query + "\"]")
			.replaceAll("[|:()]", "\",\"$0\",\"")
			.replaceAll(",?\"\\(\",?", "[")
			.replaceAll(",?\"\\)\",?", "]")
			.replaceAll("\"\"", "");
		try {
			logger.trace(array);
			ast = (ArrayNode) om().readTree(array);
		} catch (JacksonException e) {
			throw new UnsupportedOperationException(e);
		}
	}

}
