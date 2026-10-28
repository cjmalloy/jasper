package jasper.repository.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jasper.domain.Ref;
import jasper.domain.proj.Tag;
import jasper.errors.InvalidQueryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

import static jasper.repository.spec.QualifiedTag.atom;

public class TagQuery {
	private static final Logger logger = LoggerFactory.getLogger(TagQuery.class);
	private static final ObjectMapper objectMapper = new ObjectMapper();
	static final int MAX_DEPTH = 64;

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
		var not = false;
		var ands = new ArrayList<Specification<Ref>>();
		for (var i = 0; i < ast.size(); i++) {
			var n = ast.get(i);
			if (":".equals(n.textValue())) {
				or = false;
			} else if ("|".equals(n.textValue())) {
				or = true;
			} else if ("!".equals(n.textValue())) {
				not = !not;
			} else {
				var value = n.isArray() ? _refSpec(n) : atom(n.textValue()).refSpec();
				if (not) value = Specification.not(value);
				not = false;
				if (or && ands.size() > 0) {
					result = result.or(ands.stream().reduce(Specification::and).get());
					ands.clear();
				}
				ands.add(value);
			}
		}
		if (ands.size() > 0) {
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
		var not = false;
		var ands = new ArrayList<Specification<T>>();
		for (var i = 0; i < ast.size(); i++) {
			var n = ast.get(i);
			if (":".equals(n.textValue())) {
				or = false;
			} else if ("|".equals(n.textValue())) {
				or = true;
			} else if ("!".equals(n.textValue())) {
				not = !not;
			} else {
				Specification<T> value = n.isArray() ? _spec(n) : atom(n.textValue()).spec();
				if (not) value = Specification.not(value);
				not = false;
				if (or && ands.size() > 0) {
					result = result.or(ands.stream().reduce(Specification::and).get());
					ands.clear();
				}
				ands.add(value);
			}
		}
		if (ands.size() > 0) {
			result = result.or(ands.stream().reduce(Specification::and).get());
		}
		return result;
	}

	ArrayNode ast() {
		return ast;
	}

	/**
	 * Parse a query into a nested JSON array AST. Groups become nested arrays
	 * and may be nested up to {@link #MAX_DEPTH} levels. A negated group is represented by a
	 * <code>"!"</code> token directly before the group array. Negations on single
	 * terms are kept as part of the term (<code>!tag</code>).
	 */
	private void parse(String query) {
		logger.trace(query);
		var tokens = tokenize(query);
		var pos = new int[]{ 0 };
		ast = objectMapper.createArrayNode();
		if (tokens.isEmpty()) return;
		parseExpr(query, tokens, pos, ast, 0);
		if (pos[0] < tokens.size()) throw new InvalidQueryException(query, "unexpected \"" + tokens.get(pos[0]) + "\"");
		logger.trace("{}", ast);
	}

	private static List<String> tokenize(String query) {
		var tokens = new ArrayList<String>();
		var term = new StringBuilder();
		for (var i = 0; i < query.length(); i++) {
			var c = query.charAt(i);
			if (c == '|' || c == ':' || c == '(' || c == ')') {
				if (!term.isEmpty()) tokens.add(term.toString());
				term.setLength(0);
				tokens.add(String.valueOf(c));
			} else if (c == '!' && !term.isEmpty()) {
				throw new InvalidQueryException(query, "unexpected \"!\" in \"" + term + "\"");
			} else if (c == '!' && i + 1 < query.length() && (query.charAt(i + 1) == '(' || query.charAt(i + 1) == '!')) {
				tokens.add("!");
			} else {
				term.append(c);
			}
		}
		if (!term.isEmpty()) tokens.add(term.toString());
		return tokens;
	}

	private static void parseExpr(String query, List<String> tokens, int[] pos, ArrayNode out, int depth) {
		parseTerm(query, tokens, pos, out, depth);
		while (pos[0] < tokens.size() && isOperator(tokens.get(pos[0]))) {
			out.add(tokens.get(pos[0]++));
			parseTerm(query, tokens, pos, out, depth);
		}
	}

	private static void parseTerm(String query, List<String> tokens, int[] pos, ArrayNode out, int depth) {
		while (pos[0] < tokens.size() && "!".equals(tokens.get(pos[0]))) {
			out.add(tokens.get(pos[0]++));
		}
		if (pos[0] >= tokens.size()) throw new InvalidQueryException(query, "unexpected end of query");
		var token = tokens.get(pos[0]++);
		if ("(".equals(token)) {
			if (depth >= MAX_DEPTH) throw new InvalidQueryException(query, "groups nested deeper than " + MAX_DEPTH);
			if (pos[0] < tokens.size() && ")".equals(tokens.get(pos[0]))) throw new InvalidQueryException(query, "empty group");
			var group = out.addArray();
			parseExpr(query, tokens, pos, group, depth + 1);
			if (pos[0] >= tokens.size() || !")".equals(tokens.get(pos[0]++))) throw new InvalidQueryException(query, "missing \")\"");
		} else if (isOperator(token) || ")".equals(token) || "!".equals(token)) {
			throw new InvalidQueryException(query, "unexpected \"" + token + "\"");
		} else {
			out.add(token);
		}
	}

	private static boolean isOperator(String token) {
		return ":".equals(token) || "|".equals(token);
	}

}
