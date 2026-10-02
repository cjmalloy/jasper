package jasper.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jasper.IntegrationTest;
import jasper.component.ProfileManagerScim;
import jasper.component.dto.ScimEmail;
import jasper.component.dto.ScimUserResource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@TestPropertySource(properties = "spring.profiles.include=scim")
class ScimClientIT {
	@Autowired
	private ScimClient scimClient;

	@Autowired
	private ProfileManagerScim profileManager;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void decodeLookupsAndEncodeCreationThroughFeign() throws Exception {
		var requestBody = new AtomicReference<String>();
		var requestQuery = new AtomicReference<String>();
		var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/Users", exchange -> {
			var query = exchange.getRequestURI().getRawQuery();
			requestQuery.set(query == null ? null : URLDecoder.decode(query, StandardCharsets.UTF_8));
			requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			var response = exchange.getRequestMethod().equals("POST") ? requestBody.get() : """
				{
				  "schemas": ["urn:ietf:params:scim:api:messages:2.0:ListResponse"],
				  "totalResults": 3, "startIndex": 2, "itemsPerPage": 1,
				  "Resources": [{
				    "id": "user-123", "userName": "x", "active": true,
				    "emails": [{"value": "x@jasper.local"}],
				    "customClaims": {"roles": "ROLE_USER"}
				  }],
				  "vendorExtension": "ignored"
				}
				""";
			var bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/scim+json");
			exchange.sendResponseHeaders(200, bytes.length);
			try (var output = exchange.getResponseBody()) {
				output.write(bytes);
			}
		});
		server.start();
		try {
			assertThat(profileManager).isNotNull();
			var baseUri = URI.create("http://localhost:" + server.getAddress().getPort());
			var lookup = scimClient.getUser(baseUri, "test-token", "x");
			assertThat(requestQuery.get()).isEqualTo("filter=userName eq \"x\"");
			assertThat(lookup.getSchemas()).containsExactly("urn:ietf:params:scim:api:messages:2.0:ListResponse");
			assertThat(lookup.getResources()).hasSize(1);
			var user = objectMapper.convertValue(lookup.getResources().get(0), ScimUserResource.class);
			assertThat(user.getUserName()).isEqualTo("x");
			assertThat(user.isActive()).isTrue();
			assertThat(user.getEmails().get(0).getValue()).isEqualTo("x@jasper.local");
			assertThat(user.getCustomClaims().get("roles").asText()).isEqualTo("ROLE_USER");

			var page = scimClient.getUsers(baseUri, "test-token", 2, 1);
			assertThat(requestQuery.get()).isEqualTo("startIndex=2&count=1");
			assertThat(page.getTotalResults()).isEqualTo(3);
			assertThat(page.getStartIndex()).isEqualTo(2);
			assertThat(page.getItemsPerPage()).isEqualTo(1);
			assertThat(page.getResources()).hasSize(1);

			var created = scimClient.createUser(baseUri, "test-token", ScimUserResource.builder()
				.userName("x")
				.emails(List.of(new ScimEmail().setValue("x@jasper.local")))
				.build());
			var json = objectMapper.readTree(requestBody.get());
			assertThat(json.get("emails")).isEqualTo(objectMapper.readTree("[{\"value\":\"x@jasper.local\"}]"));
			assertThat(json.get("schemas")).isEqualTo(objectMapper.readTree(
				"[\"urn:ietf:params:scim:schemas:core:2.0:User\"]"));
			assertThat(created.getUserName()).isEqualTo("x");
		} finally {
			server.stop(0);
		}
	}
}
