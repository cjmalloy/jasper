package jasper.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.JasperApplication;
import jasper.domain.Plugin;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.service.ProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Streamed proxy responses are written on an async thread. Every response,
 * including range, 416 and 404 responses, must still carry the full set of
 * security headers.
 */
@WithMockUser(value = "+user/tester", roles = "USER")
@SpringBootTest(classes = JasperApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"file-cache", "storage", "test"})
class ProxyControllerHeadersIT {

	@LocalServerPort
	int port;

	@Autowired
	ProxyService proxyService;

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@BeforeEach
	void setup() throws Exception {
		refRepository.deleteAll();
		pluginRepository.deleteAll();
		var cachePlugin = new Plugin();
		cachePlugin.setTag("_plugin/cache");
		cachePlugin.setSchema((ObjectNode) new ObjectMapper().readTree("""
		{
		    "optionalProperties": {
		      "id": { "type": "string" },
		      "mimeType": { "type": "string" },
		      "contentLength": { "type": "uint32" },
		      "ban": { "type": "boolean" },
		      "noStore": { "type": "boolean" },
		      "thumbnail": { "type": "boolean" }
		    }
		}"""));
		pluginRepository.save(cachePlugin);
	}

	@Test
	@Timeout(60)
	void testConcurrentFetchesHaveSecurityHeaders() throws Exception {
		var urls = new ArrayList<String>();
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
		try {
			for (var i = 0; i < 50; i++) {
				var html = "<html><body><script>parent.postMessage(" + i + ", '*')</script>" + "x".repeat(i * 37) + "</body></html>";
				urls.add(proxyService.save("", "tab.html", new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)), "text/html").getUrl());
			}
		} finally {
			RequestContextHolder.resetRequestAttributes();
		}
		var client = HttpClient.newHttpClient();
		var tasks = new ArrayList<Callable<String>>();
		for (var round = 0; round < 10; round++) {
			for (var i = 0; i < urls.size(); i++) {
				var url = urls.get(i);
				var range = switch ((round + i) % 5) {
					case 1 -> "bytes=0-9";
					case 2 -> "bytes=5-";
					case 3 -> "bytes=999999-";
					default -> null;
				};
				var missing = (round + i) % 5 == 4;
				var target = missing ? "cache:missing-" + round + "-" + i : url;
				tasks.add(() -> {
					var req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/proxy/tab.html?origin=&url=" + URLEncoder.encode(target, StandardCharsets.UTF_8)))
						.header("Sec-Fetch-Dest", "document");
					if (range != null) req.header("Range", range);
					var res = client.send(req.build(), HttpResponse.BodyHandlers.ofByteArray());
					var expectedStatus = missing ? 404 : range == null ? 200 : range.startsWith("bytes=999999") ? 416 : 206;
					return check(res, expectedStatus, target, range);
				});
			}
		}
		List<String> failures = new ArrayList<>();
		try (var pool = Executors.newFixedThreadPool(8)) {
			for (var f : pool.invokeAll(tasks)) {
				var failure = f.get();
				if (failure != null) failures.add(failure);
			}
		}
		assertThat(failures).isEmpty();
	}

	private String check(HttpResponse<byte[]> res, int expectedStatus, String url, String range) {
		var h = res.headers();
		var problems = new ArrayList<String>();
		if (res.statusCode() != expectedStatus) problems.add("status " + res.statusCode());
		if (!h.firstValue("Content-Security-Policy").orElse("").contains("sandbox")) problems.add("CSP");
		if (h.firstValue("X-Frame-Options").isEmpty()) problems.add("X-Frame-Options");
		if (h.firstValue("Referrer-Policy").isEmpty()) problems.add("Referrer-Policy");
		if (h.firstValue("Permissions-Policy").isEmpty()) problems.add("Permissions-Policy");
		if (!h.firstValue("X-Content-Type-Options").orElse("").equals("nosniff")) problems.add("X-Content-Type-Options");
		if (expectedStatus == 200 && !h.firstValue("Accept-Ranges").orElse("").equals("bytes")) problems.add("Accept-Ranges");
		if (expectedStatus == 206 && h.firstValue("Content-Range").isEmpty()) problems.add("Content-Range");
		return problems.isEmpty() ? null : url + " " + range + ": " + problems;
	}
}
