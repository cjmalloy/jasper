package jasper.web.rest;

import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.domain.proj.HasOrigin;
import jasper.domain.proj.Tag;
import jasper.repository.filter.Query;
import jasper.repository.spec.SortSpec.PluginValueFilter;
import jasper.repository.spec.SortSpec.TagValueFilter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static jasper.domain.Ref.URL_LEN;
import static jasper.domain.proj.HasOrigin.ORIGIN_LEN;
import static jasper.domain.proj.Tag.QTAG_LEN;
import static jasper.domain.proj.Tag.TAG_LEN;
import static jasper.repository.filter.Query.QUERY_LEN;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the hand written OpenAPI spec in sync with the REST controllers.
 * Profile specific controllers are included, so the spec documents every optional endpoint.
 */
class ApiSpecTest {
	static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete");

	static Map<String, Object> spec;
	/**
	 * Operations in the controllers: "METHOD path" to query parameter names, or null for any.
	 */
	static Map<String, Set<String>> code = new TreeMap<>();
	/**
	 * Success status of operations in the controllers: "METHOD path" to status code.
	 * Operations returning a ResponseEntity set the status at runtime and are not included.
	 */
	static Map<String, String> status = new TreeMap<>();

	@BeforeAll
	static void init() throws IOException, ClassNotFoundException {
		try (var in = ApiSpecTest.class.getResourceAsStream("/swagger/api.yml")) {
			spec = new Yaml().load(in);
		}
		// Include controllers from every profile
		var scanner = new ClassPathScanningCandidateComponentProvider(false, new StandardEnvironment() {
			@Override
			public boolean acceptsProfiles(Profiles profiles) {
				return true;
			}
		});
		scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
		var names = new DefaultParameterNameDiscoverer();
		for (var bean : scanner.findCandidateComponents("jasper.web.rest")) {
			var type = ClassUtils.forName(bean.getBeanClassName(), ApiSpecTest.class.getClassLoader());
			if (!type.getPackageName().equals("jasper.web.rest")) continue;
			var base = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
			var basePaths = base == null || base.path().length == 0 ? new String[]{ "" } : base.path();
			for (var method : type.getDeclaredMethods()) {
				var mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
				if (mapping == null) continue;
				Set<String> params = new TreeSet<>();
				for (var i = 0; i < method.getParameterCount(); i++) {
					var p = new MethodParameter(method, i);
					p.initParameterNameDiscovery(names);
					if (Pageable.class.equals(p.getParameterType())) {
						params.addAll(List.of("page", "size", "sort"));
					}
					var rp = p.getParameterAnnotation(RequestParam.class);
					if (rp == null) continue;
					if (Map.class.isAssignableFrom(p.getParameterType())) {
						params = null;
						break;
					}
					params.add(!rp.name().isEmpty() ? rp.name() : p.getParameterName());
				}
				var subPaths = mapping.path().length == 0 ? new String[]{ "" } : mapping.path();
				for (var b : basePaths) {
					for (var s : subPaths) {
						var path = ("/" + b + "/" + s).replaceAll("/+", "/").replaceAll("/$", "")
							.replaceAll("\\{(\\w+):[^}]*}", "{$1}");
						for (var m : mapping.method()) {
							var key = m.name() + " " + path;
							if (!ResponseEntity.class.isAssignableFrom(method.getReturnType())) {
								var rs = AnnotatedElementUtils.findMergedAnnotation(method, ResponseStatus.class);
								status.put(key, String.valueOf(rs == null ? 200 : rs.code().value()));
							}
							if (code.containsKey(key) && code.get(key) != null && params != null) {
								// Same operation with a different consumes / produces
								code.get(key).addAll(params);
							} else {
								code.put(key, params);
							}
						}
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	static Map<String, Map<String, Object>> specOperations() {
		var result = new TreeMap<String, Map<String, Object>>();
		var paths = (Map<String, Map<String, Object>>) spec.get("paths");
		paths.forEach((path, ops) -> ops.forEach((method, op) -> {
			if (METHODS.contains(method)) result.put(method.toUpperCase() + " " + path, (Map<String, Object>) op);
		}));
		return result;
	}

	@SuppressWarnings("unchecked")
	static List<Map<String, Object>> parameters(Map<String, Object> op, String in) {
		var params = (List<Map<String, Object>>) op.getOrDefault("parameters", List.of());
		return params.stream().filter(p -> in.equals(p.get("in"))).toList();
	}

	@Test
	void testOperations() {
		assertThat(code).isNotEmpty();
		assertThat(specOperations().keySet())
			.containsExactlyInAnyOrderElementsOf(code.keySet());
	}

	@Test
	void testQueryParameters() {
		var ops = specOperations();
		var mismatched = new ArrayList<String>();
		code.forEach((op, params) -> {
			if (params == null || !ops.containsKey(op)) return;
			var documented = new TreeSet<String>();
			for (var p : parameters(ops.get(op), "query")) documented.add((String) p.get("name"));
			if (!documented.equals(params)) mismatched.add(op + ": spec " + documented + " code " + params);
		});
		assertThat(mismatched).isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void testSuccessStatus() {
		assertThat(status).isNotEmpty();
		var ops = specOperations();
		var mismatched = new ArrayList<String>();
		status.forEach((op, code) -> {
			if (!ops.containsKey(op)) return;
			var documented = new TreeSet<String>();
			for (var c : ((Map<Object, Object>) ops.get(op).getOrDefault("responses", Map.of())).keySet()) {
				if (String.valueOf(c).startsWith("2")) documented.add(String.valueOf(c));
			}
			if (!documented.equals(Set.of(code))) mismatched.add(op + ": spec " + documented + " code " + code);
		});
		assertThat(mismatched).isEmpty();
	}

	@Test
	void testPathParameters() {
		var mismatched = new ArrayList<String>();
		specOperations().forEach((op, o) -> {
			var expected = new TreeSet<String>();
			var m = Pattern.compile("\\{(\\w+)}").matcher(op);
			while (m.find()) expected.add(m.group(1));
			var documented = new TreeSet<String>();
			for (var p : parameters(o, "path")) documented.add((String) p.get("name"));
			if (!documented.equals(expected)) mismatched.add(op + ": spec " + documented + " path " + expected);
		});
		assertThat(mismatched).isEmpty();
	}

	@Test
	void testPatternsAnchored() {
		var unanchored = new ArrayList<String>();
		findPatterns(spec, "", unanchored);
		assertThat(unanchored).isEmpty();
	}

	static void findPatterns(Object node, String path, List<String> unanchored) {
		if (node instanceof Map<?, ?> map) {
			map.forEach((k, v) -> {
				if ("pattern".equals(k) && v instanceof String p && (!p.startsWith("^") || !p.endsWith("$"))) {
					unanchored.add(path + ": " + p);
				}
				findPatterns(v, path + "/" + k, unanchored);
			});
		} else if (node instanceof List<?> list) {
			for (var i = 0; i < list.size(); i++) findPatterns(list.get(i), path + "/" + i, unanchored);
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	void testSchemasMatchCode() {
		var schemas = (Map<String, Map<String, Object>>) ((Map<String, Object>) spec.get("components")).get("schemas");
		var expected = new TreeMap<String, Object[]>(Map.ofEntries(
			Map.entry("url", new Object[]{ Ref.REGEX, URL_LEN }),
			Map.entry("scheme", new Object[]{ Ref.SCHEME_REGEX, URL_LEN }),
			Map.entry("origin", new Object[]{ HasOrigin.REGEX, ORIGIN_LEN }),
			Map.entry("tag", new Object[]{ Tag.REGEX, TAG_LEN }),
			Map.entry("tagPatch", new Object[]{ Tag.ADD_REMOVE_REGEX, TAG_LEN + 1 }),
			Map.entry("qtag", new Object[]{ Tag.QTAG_REGEX, QTAG_LEN }),
			Map.entry("query", new Object[]{ Query.REGEX, QUERY_LEN }),
			Map.entry("tag-template", new Object[]{ Template.REGEX, TAG_LEN }),
			Map.entry("qtag-template", new Object[]{ Template.QTAG_REGEX, QTAG_LEN }),
			Map.entry("tag-plugin", new Object[]{ Plugin.REGEX, TAG_LEN }),
			Map.entry("qtag-plugin", new Object[]{ Plugin.QTAG_REGEX, QTAG_LEN }),
			Map.entry("tag-user", new Object[]{ User.REGEX, TAG_LEN }),
			Map.entry("qtag-user", new Object[]{ User.QTAG_REGEX, QTAG_LEN }),
			Map.entry("tagValueFilter", new Object[]{ TagValueFilter.REGEX, TagValueFilter.LEN }),
			Map.entry("pluginValueFilter", new Object[]{ PluginValueFilter.REGEX, PluginValueFilter.LEN })
		));
		expected.forEach((name, e) -> {
			assertThat(schemas).containsKey(name);
			var regex = ((String) e[0]).replaceFirst("^\\^", "");
			assertThat(schemas.get(name).get("pattern")).as(name).isEqualTo("^" + regex + "$");
			assertThat(schemas.get(name).get("maxLength")).as(name).isEqualTo(e[1]);
		});
	}

	@Test
	void testMinLength() {
		var invalid = new ArrayList<String>();
		findMinLengthZero(spec, "", invalid);
		assertThat(invalid).isEmpty();
	}

	static void findMinLengthZero(Object node, String path, List<String> invalid) {
		if (node instanceof Map<?, ?> map) {
			map.forEach((k, v) -> {
				if ("minLength".equals(k) && Integer.valueOf(0).equals(v)) invalid.add(path);
				findMinLengthZero(v, path + "/" + k, invalid);
			});
		} else if (node instanceof List<?> list) {
			for (var i = 0; i < list.size(); i++) findMinLengthZero(list.get(i), path + "/" + i, invalid);
		}
	}
}
