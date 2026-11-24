package jasper.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import jasper.IntegrationTest;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Template;
import jasper.service.dto.RefReplDto;
import jasper.service.dto.UserDto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import static jasper.client.JasperClient.params;
import static jasper.security.Auth.LOCAL_ORIGIN_HEADER;
import static jasper.security.Auth.USER_TAG_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class JasperClientIT {
	static final String QUERY = "+plugin/test:!_secret|public user";
	static final Instant AFTER = Instant.parse("2024-01-02T03:04:05.123456789Z");
	static final String AUTHORIZATION = "Be" + "arer " + "test-token";
	static final String CURSOR = "2024-05-06T07:08:09.987654321Z";

	record Recorded(String method, String path, String rawQuery, Map<String, List<String>> headers, byte[] body) {
		String text() {
			return new String(body, StandardCharsets.UTF_8);
		}
	}

	static HttpServer server;
	static URI baseUri;
	static final List<Recorded> requests = Collections.synchronizedList(new ArrayList<>());
	static final Map<String, String> responses = new ConcurrentHashMap<>();

	@Autowired
	JasperClient client;

	@Autowired
	ObjectMapper objectMapper;

	@BeforeAll
	static void startServer() throws Exception {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/", exchange -> {
			var headers = new TreeMap<String, List<String>>();
			exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v));
			var path = exchange.getRequestURI().getRawPath();
			requests.add(new Recorded(
				exchange.getRequestMethod(),
				path,
				exchange.getRequestURI().getRawQuery(),
				headers,
				exchange.getRequestBody().readAllBytes()));
			if (exchange.getRequestMethod().equals("GET") && path.equals("/pub/api/v1/repl/cache")) {
				var bytes = "cached bytes".getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "text/plain");
				exchange.sendResponseHeaders(200, bytes.length);
				try (var output = exchange.getResponseBody()) {
					output.write(bytes);
				}
				return;
			}
			var body = exchange.getRequestMethod().equals("GET") ? responses.get(path) : null;
			if (body == null) {
				exchange.sendResponseHeaders(200, -1);
				exchange.close();
				return;
			}
			var bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			try (var output = exchange.getResponseBody()) {
				output.write(bytes);
			}
		});
		server.start();
		baseUri = URI.create("http://localhost:" + server.getAddress().getPort());
	}

	@AfterAll
	static void stopServer() {
		server.stop(0);
	}

	@BeforeEach
	void reset() {
		requests.clear();
		responses.clear();
	}

	Recorded only() {
		assertThat(requests).hasSize(1);
		var r = requests.getFirst();
		requests.clear();
		return r;
	}

	void assertHeaders(Recorded r, String contentType, Map<String, String> extra) {
		var expected = new TreeMap<String, String>();
		expected.put("accept", "*/*");
		expected.put("accept-encoding", "gzip,deflate");
		expected.put("connection", "Keep-Alive");
		expected.put("content-length", "" + r.body().length);
		expected.put("host", "localhost:" + server.getAddress().getPort());
		if (contentType != null) expected.put("content-type", contentType);
		expected.putAll(extra);
		assertThat(r.headers().keySet()).containsExactlyInAnyOrderElementsOf(
			concat(expected.keySet(), List.of("user-agent")));
		expected.forEach((k, v) -> assertThat(r.headers().get(k)).as(k).containsExactly(v));
		assertThat(r.headers().get("user-agent")).singleElement().asString().startsWith("Apache-HttpClient/4.5.");
	}

	static List<String> concat(Collection<String> a, List<String> b) {
		var result = new ArrayList<>(a);
		result.addAll(b);
		return result;
	}

	void assertGet(Recorded r, String path, String rawQuery) {
		assertThat(r.method()).isEqualTo("GET");
		assertThat(r.path()).isEqualTo(path);
		assertThat(r.rawQuery()).isEqualTo(rawQuery);
		assertThat(r.body()).isEmpty();
		assertHeaders(r, null, Map.of());
	}

	void assertPost(Recorded r, String path, String json) throws Exception {
		assertThat(r.method()).isEqualTo("POST");
		assertThat(r.path()).isEqualTo(path);
		assertThat(r.rawQuery()).isEqualTo("origin=%40remote");
		assertHeaders(r, "application/json", Map.of("local-origin", "@remote"));
		assertThat(objectMapper.readTree(r.body())).isEqualTo(objectMapper.readTree(json));
	}

	static Map<String, Object> pullParams() {
		// Same keys and insertion order as Replicator.pull for plugins, templates, exts and users
		return params(
			"size", 7,
			"origin", "@remote",
			"modifiedAfter", AFTER);
	}

	@Test
	void testCursors() {
		for (var type : List.of("ref", "ext", "user", "plugin", "template")) {
			responses.put("/pub/api/v1/repl/" + type + "/cursor", "\"" + CURSOR + "\"");
		}
		assertThat(client.pluginCursor(baseUri, "@remote")).isEqualTo(Instant.parse(CURSOR));
		assertGet(only(), "/pub/api/v1/repl/plugin/cursor", "origin=%40remote");
		assertThat(client.templateCursor(baseUri, "@remote")).isEqualTo(Instant.parse(CURSOR));
		assertGet(only(), "/pub/api/v1/repl/template/cursor", "origin=%40remote");
		assertThat(client.refCursor(baseUri, "@remote")).isEqualTo(Instant.parse(CURSOR));
		assertGet(only(), "/pub/api/v1/repl/ref/cursor", "origin=%40remote");
		assertThat(client.extCursor(baseUri, "@remote")).isEqualTo(Instant.parse(CURSOR));
		assertGet(only(), "/pub/api/v1/repl/ext/cursor", "origin=%40remote");
		assertThat(client.userCursor(baseUri, "@remote")).isEqualTo(Instant.parse(CURSOR));
		assertGet(only(), "/pub/api/v1/repl/user/cursor", "origin=%40remote");

		// Default origin is sent without '=', and an empty cursor (remote has no entities) decodes to null
		responses.clear();
		assertThat(client.refCursor(baseUri, "")).isNull();
		assertGet(only(), "/pub/api/v1/repl/ref/cursor", "origin");
	}

	@Test
	void testRefPull() {
		responses.put("/pub/api/v1/repl/ref", """
			[{
			  "url": "https://example.com/1",
			  "origin": "@remote",
			  "title": "One",
			  "comment": "Comment",
			  "tags": ["public", "+plugin/test"],
			  "sources": ["https://example.com/source"],
			  "alternateUrls": ["https://example.com/alt"],
			  "plugins": {"+plugin/test": {"x": 1}},
			  "metadata": {
			    "modified": "2024-01-02T03:04:05.123456789Z",
			    "responses": ["https://example.com/response"],
			    "plugins": {"plugin/comment": 2},
			    "obsolete": true,
			    "regen": true,
			    "cascade": true,
			    "ignored": true
			  },
			  "published": "2024-01-01T00:00:00.000000001Z",
			  "created": "2024-01-01T12:00:00Z",
			  "modified": "2024-01-02T03:04:05.987654321Z",
			  "vendorExtension": "ignored"
			}, {
			  "url": "https://example.com/2",
			  "origin": "@remote",
			  "metadata": {"modified": "2024-01-03T00:00:00Z"},
			  "published": "2024-01-03T00:00:00.5Z",
			  "created": "2024-01-03T00:00:00.000Z",
			  "modified": "2024-01-03T00:00:00.100Z"
			}]
			""");
		// Same keys and insertion order as Replicator.pull
		var refs = client.refPull(baseUri, params(
			"query", QUERY,
			"size", 7,
			"origin", "@remote",
			"modifiedAfter", AFTER));
		// HashMap iteration order; space encoded as '+', '+' as %2B
		assertGet(only(), "/pub/api/v1/repl/ref",
			"modifiedAfter=2024-01-02T03%3A04%3A05.123456789Z&size=7&query=%2Bplugin%2Ftest%3A%21_secret%7Cpublic+user&origin=%40remote");

		assertThat(refs).hasSize(2);
		var first = refs.get(0);
		assertThat(first.getUrl()).isEqualTo("https://example.com/1");
		assertThat(first.getOrigin()).isEqualTo("@remote");
		assertThat(first.getTitle()).isEqualTo("One");
		assertThat(first.getComment()).isEqualTo("Comment");
		assertThat(first.getTags()).containsExactly("public", "+plugin/test");
		assertThat(first.getSources()).containsExactly("https://example.com/source");
		assertThat(first.getAlternateUrls()).containsExactly("https://example.com/alt");
		assertThat(first.getPlugins().toString()).isEqualTo("{\"+plugin/test\":{\"x\":1}}");
		assertThat(first.getPublished()).isEqualTo(Instant.parse("2024-01-01T00:00:00.000000001Z"));
		assertThat(first.getCreated()).isEqualTo(Instant.parse("2024-01-01T12:00:00Z"));
		assertThat(first.getModified()).isEqualTo(Instant.parse("2024-01-02T03:04:05.987654321Z"));
		assertThat(first.getMetadata().getModified()).isEqualTo("2024-01-02T03:04:05.123456789Z");
		assertThat(first.getMetadata().getResponses()).containsExactly("https://example.com/response");
		assertThat(first.getMetadata().getPlugins()).containsExactlyEntriesOf(Map.of("plugin/comment", 2L));
		assertThat(first.getMetadata().isObsolete()).isTrue();
		assertThat(first.getMetadata().isRegen()).isTrue();
		assertThat(first.getMetadata().isCascade()).isTrue();
		assertThat(first.getMetadata().isIgnored()).isTrue();

		var second = refs.get(1);
		assertThat(second.getUrl()).isEqualTo("https://example.com/2");
		assertThat(second.getTitle()).isNull();
		assertThat(second.getTags()).isNull();
		assertThat(second.getPlugins()).isNull();
		assertThat(second.getPublished()).isEqualTo(Instant.parse("2024-01-03T00:00:00.500Z"));
		assertThat(second.getCreated()).isEqualTo(Instant.parse("2024-01-03T00:00:00Z"));
		assertThat(second.getModified()).isEqualTo(Instant.parse("2024-01-03T00:00:00.100Z"));
		assertThat(second.getMetadata().getModified()).isEqualTo("2024-01-03T00:00:00Z");
		assertThat(second.getMetadata().getResponses()).isNull();
		assertThat(second.getMetadata().isObsolete()).isFalse();
		assertThat(second.getMetadata().isRegen()).isFalse();
		assertThat(second.getMetadata().isCascade()).isFalse();
		assertThat(second.getMetadata().isIgnored()).isFalse();

		// Replicator passes pull.getQuery() which may be null: the parameter is dropped
		responses.put("/pub/api/v1/repl/ref", "[]");
		assertThat(client.refPull(baseUri, params(
			"query", null,
			"size", 7,
			"origin", "@remote",
			"modifiedAfter", AFTER))).isEmpty();
		assertGet(only(), "/pub/api/v1/repl/ref",
			"modifiedAfter=2024-01-02T03%3A04%3A05.123456789Z&size=7&origin=%40remote");
	}

	@Test
	void testExtPull() {
		responses.put("/pub/api/v1/repl/ext", """
			[{"tag": "+plugin/test", "origin": "@remote", "name": "Ext", "config": {"a": "b"}, "modified": "2024-01-02T03:04:05.123456789Z"}]
			""");
		var exts = client.extPull(baseUri, pullParams());
		assertGet(only(), "/pub/api/v1/repl/ext",
			"modifiedAfter=2024-01-02T03%3A04%3A05.123456789Z&size=7&origin=%40remote");
		assertThat(exts).hasSize(1);
		assertThat(exts.get(0).getTag()).isEqualTo("+plugin/test");
		assertThat(exts.get(0).getOrigin()).isEqualTo("@remote");
		assertThat(exts.get(0).getName()).isEqualTo("Ext");
		assertThat(exts.get(0).getConfig().toString()).isEqualTo("{\"a\":\"b\"}");
		assertThat(exts.get(0).getModified()).isEqualTo(AFTER);
	}

	@Test
	void testUserPull() {
		responses.put("/pub/api/v1/repl/user", """
			[{
			  "tag": "+user/alice", "origin": "@remote", "name": "Alice", "role": "ROLE_USER",
			  "readAccess": ["public"], "writeAccess": ["+user/alice"],
			  "tagReadAccess": ["+plugin/test"], "tagWriteAccess": ["+plugin/other"],
			  "modified": "2024-01-02T03:04:05.123456789Z",
			  "pubKey": "AQID", "authorizedKeys": "ssh-ed25519 AAAA",
			  "external": {"ids": ["alice@example.com"]}
			}]
			""");
		var users = client.userPull(baseUri, pullParams());
		assertGet(only(), "/pub/api/v1/repl/user",
			"modifiedAfter=2024-01-02T03%3A04%3A05.123456789Z&size=7&origin=%40remote");
		assertThat(users).hasSize(1);
		var user = users.get(0);
		assertThat(user.getTag()).isEqualTo("+user/alice");
		assertThat(user.getOrigin()).isEqualTo("@remote");
		assertThat(user.getName()).isEqualTo("Alice");
		assertThat(user.getRole()).isEqualTo("ROLE_USER");
		assertThat(user.getReadAccess()).containsExactly("public");
		assertThat(user.getWriteAccess()).containsExactly("+user/alice");
		assertThat(user.getTagReadAccess()).containsExactly("+plugin/test");
		assertThat(user.getTagWriteAccess()).containsExactly("+plugin/other");
		assertThat(user.getModified()).isEqualTo(AFTER);
		assertThat(user.getPubKey()).containsExactly(1, 2, 3);
		assertThat(user.getAuthorizedKeys()).isEqualTo("ssh-ed25519 AAAA");
		assertThat(user.getExternal().getIds()).containsExactly("alice@example.com");
	}

	@Test
	void testPluginAndTemplatePull() {
		responses.put("/pub/api/v1/repl/plugin", """
			[{"tag": "plugin/test", "origin": "@remote", "name": "Test", "config": {"generated": true},
			  "defaults": {"x": 1}, "schema": {"properties": {"x": {"type": "int32"}}},
			  "modified": "2024-01-02T03:04:05.123456789Z"}]
			""");
		responses.put("/pub/api/v1/repl/template", """
			[{"tag": "test", "origin": "@remote", "name": "Template", "config": {"a": 1},
			  "defaults": {"x": "y"}, "schema": {"properties": {"x": {"type": "string"}}},
			  "modified": "2024-01-02T03:04:05.000000001Z"}]
			""");
		var plugins = client.pluginPull(baseUri, pullParams());
		assertGet(only(), "/pub/api/v1/repl/plugin",
			"modifiedAfter=2024-01-02T03%3A04%3A05.123456789Z&size=7&origin=%40remote");
		assertThat(plugins).hasSize(1);
		assertThat(plugins.get(0).getTag()).isEqualTo("plugin/test");
		assertThat(plugins.get(0).getOrigin()).isEqualTo("@remote");
		assertThat(plugins.get(0).getName()).isEqualTo("Test");
		assertThat(plugins.get(0).getConfig().toString()).isEqualTo("{\"generated\":true}");
		assertThat(plugins.get(0).getDefaults().toString()).isEqualTo("{\"x\":1}");
		assertThat(plugins.get(0).getSchema().toString()).isEqualTo("{\"properties\":{\"x\":{\"type\":\"int32\"}}}");
		assertThat(plugins.get(0).getModified()).isEqualTo(AFTER);

		var templates = client.templatePull(baseUri, pullParams());
		assertGet(only(), "/pub/api/v1/repl/template",
			"modifiedAfter=2024-01-02T03%3A04%3A05.123456789Z&size=7&origin=%40remote");
		assertThat(templates).hasSize(1);
		assertThat(templates.get(0).getTag()).isEqualTo("test");
		assertThat(templates.get(0).getOrigin()).isEqualTo("@remote");
		assertThat(templates.get(0).getName()).isEqualTo("Template");
		assertThat(templates.get(0).getConfig().toString()).isEqualTo("{\"a\":1}");
		assertThat(templates.get(0).getDefaults().toString()).isEqualTo("{\"x\":\"y\"}");
		assertThat(templates.get(0).getSchema().toString()).isEqualTo("{\"properties\":{\"x\":{\"type\":\"string\"}}}");
		assertThat(templates.get(0).getModified()).isEqualTo(Instant.parse("2024-01-02T03:04:05.000000001Z"));
	}

	@Test
	void testPushes() throws Exception {
		// Same headers as Replicator.push
		Map<String, Object> headers = Map.of(LOCAL_ORIGIN_HEADER, "@remote");

		var ref = new RefReplDto();
		ref.setUrl("https://example.com/1");
		ref.setOrigin("@remote");
		ref.setTitle("One");
		ref.setTags(List.of("public", "+plugin/test"));
		ref.setSources(List.of());
		ref.setPlugins((ObjectNode) objectMapper.readTree("{\"+plugin/test\":{\"x\":1}}"));
		ref.setPublished(Instant.parse("2024-01-01T00:00:00.000000001Z"));
		ref.setCreated(Instant.parse("2024-01-01T12:00:00Z"));
		ref.setModified(AFTER);
		client.refPush(baseUri, headers, "@remote", List.of(ref));
		var r = only();
		// Instants are ISO-8601 strings with all nanos, empty lists omitted (NON_EMPTY)
		assertThat(r.text()).contains("\"modified\":\"2024-01-02T03:04:05.123456789Z\"", "\"created\":\"2024-01-01T12:00:00Z\"");
		assertPost(r, "/pub/api/v1/repl/ref", """
			[{"url": "https://example.com/1", "origin": "@remote", "title": "One",
			  "tags": ["public", "+plugin/test"], "plugins": {"+plugin/test": {"x": 1}},
			  "published": "2024-01-01T00:00:00.000000001Z", "created": "2024-01-01T12:00:00Z",
			  "modified": "2024-01-02T03:04:05.123456789Z"}]
			""");

		var ext = new Ext();
		ext.setTag("+plugin/test");
		ext.setOrigin("@remote");
		ext.setName("Ext");
		ext.setConfig(objectMapper.createObjectNode().put("a", "b"));
		ext.setModified(AFTER);
		client.extPush(baseUri, headers, "@remote", List.of(ext));
		assertPost(only(), "/pub/api/v1/repl/ext", """
			[{"tag": "+plugin/test", "origin": "@remote", "name": "Ext", "config": {"a": "b"},
			  "modified": "2024-01-02T03:04:05.123456789Z"}]
			""");

		// Entities without @JsonInclude serialize nulls
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setModified(AFTER);
		client.pluginPush(baseUri, headers, "@remote", List.of(plugin));
		assertPost(only(), "/pub/api/v1/repl/plugin", """
			[{"tag": "plugin/test", "origin": "", "name": null, "config": null, "defaults": null, "schema": null,
			  "modified": "2024-01-02T03:04:05.123456789Z"}]
			""");

		var template = new Template();
		template.setTag("test");
		template.setOrigin("@remote");
		template.setName("Template");
		template.setSchema(objectMapper.createObjectNode().put("type", "string"));
		template.setModified(AFTER);
		client.templatePush(baseUri, headers, "@remote", List.of(template));
		assertPost(only(), "/pub/api/v1/repl/template", """
			[{"tag": "test", "origin": "@remote", "name": "Template", "config": null, "defaults": null,
			  "schema": {"type": "string"}, "modified": "2024-01-02T03:04:05.123456789Z"}]
			""");

		var user = new UserDto();
		user.setTag("+user/alice");
		user.setOrigin("@remote");
		user.setRole("ROLE_USER");
		user.setReadAccess(List.of("public"));
		user.setWriteAccess(List.of());
		user.setModified(AFTER);
		user.setPubKey(new byte[]{1, 2, 3});
		client.userPush(baseUri, headers, "@remote", List.of(user));
		assertPost(only(), "/pub/api/v1/repl/user", """
			[{"tag": "+user/alice", "origin": "@remote", "role": "ROLE_USER", "readAccess": ["public"],
			  "modified": "2024-01-02T03:04:05.123456789Z", "pubKey": "AQID"}]
			""");
	}

	@Test
	void testCache() throws Exception {
		var url = "cache:a b?c=d&e+f";
		var response = client.fetch(baseUri, url, "@remote");
		assertGet(only(), "/pub/api/v1/repl/cache", "url=cache%3Aa+b%3Fc%3Dd%26e%2Bf&origin=%40remote");
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/plain");
		try (var is = response.getBody().getInputStream()) {
			assertThat(new String(is.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("cached bytes");
		}

		client.push(baseUri, Map.of(LOCAL_ORIGIN_HEADER, "@remote"), url, "@remote", new byte[]{1, 2, 3});
		var r = only();
		assertThat(r.method()).isEqualTo("PUT");
		assertThat(r.path()).isEqualTo("/pub/api/v1/repl/cache");
		assertThat(r.rawQuery()).isEqualTo("url=cache%3Aa+b%3Fc%3Dd%26e%2Bf&origin=%40remote");
		assertHeaders(r, "application/octet-stream", Map.of("local-origin", "@remote"));
		assertThat(r.body()).containsExactly(1, 2, 3);
	}

	@Test
	void testJasperHeaders() {
		var request = new MockHttpServletRequest();
		request.addHeader("Authorization", AUTHORIZATION);
		request.addHeader(USER_TAG_HEADER, "+user/alice");
		request.addHeader(LOCAL_ORIGIN_HEADER, "@remote");
		responses.put("/pub/api/v1/repl/ref", "[]");
		client.refPull(baseUri, JasperClient.jasperHeaders(new ServletWebRequest(request)), pullParams());
		var r = only();
		// Empty values from jasperHeaders are dropped by Feign, not sent as empty headers
		assertHeaders(r, null, Map.of(
			"authorization", AUTHORIZATION,
			"user-tag", "+user/alice",
			"local-origin", "@remote"));
	}
}
