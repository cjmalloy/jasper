package jasper.component.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.config.JacksonConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScimDtoTest {
	private final ObjectMapper objectMapper;

	ScimDtoTest() {
		var builder = Jackson2ObjectMapperBuilder.json();
		new JacksonConfiguration().jsonCustomizer().customize(builder);
		objectMapper = builder.build();
		objectMapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
	}

	@Test
	void deserializeListResponseAndConvertUser() throws Exception {
		var response = objectMapper.readValue("""
			{
			  "schemas": ["urn:ietf:params:scim:api:messages:2.0:ListResponse"],
			  "totalResults": 42,
			  "startIndex": 1,
			  "itemsPerPage": 10,
			  "Resources": [{
			    "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
			    "id": "user-123",
			    "userName": "x",
			    "active": false,
			    "emails": [{"value": "x@jasper.local", "vendorExtension": "ignored"}],
			    "customClaims": {"roles": "ROLE_USER,ROLE_PRIVATE"}
			  }],
			  "vendorExtension": "ignored"
			}
			""", new TypeReference<ScimListResponse<Map<String, Object>>>() {});

		assertThat(response.getSchemas()).containsExactly("urn:ietf:params:scim:api:messages:2.0:ListResponse");
		assertThat(response.getTotalResults()).isEqualTo(42);
		assertThat(response.getStartIndex()).isEqualTo(1);
		assertThat(response.getItemsPerPage()).isEqualTo(10);
		assertThat(response.getResources()).hasSize(1);

		var user = objectMapper.convertValue(response.getResources().get(0), ScimUserResource.class);
		assertThat(user.getId()).isEqualTo("user-123");
		assertThat(user.getUserName()).isEqualTo("x");
		assertThat(user.isActive()).isFalse();
		assertThat(user.getEmails()).hasSize(1);
		assertThat(user.getEmails().get(0).getValue()).isEqualTo("x@jasper.local");
		assertThat(user.getCustomClaims().get("roles").asText()).isEqualTo("ROLE_USER,ROLE_PRIVATE");

		var json = objectMapper.valueToTree(response);
		assertThat(json.has("Resources")).isTrue();
		assertThat(json.has("resources")).isFalse();
		assertThat(json.get("Resources").get(0).get("userName").asText()).isEqualTo("x");
	}

	@Test
	void serializeCreatedUserOmitsNullEmailFields() throws Exception {
		var user = ScimUserResource.builder()
			.userName("x")
			.password("test-password")
			.customClaims(objectMapper.createObjectNode().put("roles", "ROLE_USER"))
			.emails(List.of(new ScimEmail().setValue("x@jasper.local")))
			.build();

		var json = objectMapper.readTree(objectMapper.writeValueAsString(user));
		assertThat(json.get("emails")).isEqualTo(objectMapper.readTree("""
			[{"value": "x@jasper.local"}]
			"""));
		assertThat(json.get("schemas")).isEqualTo(objectMapper.readTree("""
			["urn:ietf:params:scim:schemas:core:2.0:User"]
			"""));
		assertThat(json.get("userName").asText()).isEqualTo("x");
		assertThat(json.get("active").asBoolean()).isTrue();
		assertThat(json.get("customClaims").get("roles").asText()).isEqualTo("ROLE_USER");
	}

	@Test
	void emailPreservesAllFieldsIncludingFalsePrimary() throws Exception {
		var email = objectMapper.readValue("""
			{"value": "x@jasper.local", "type": "work", "display": "Work email", "primary": false, "extra": true}
			""", ScimEmail.class);

		assertThat(email.getValue()).isEqualTo("x@jasper.local");
		assertThat(email.getType()).isEqualTo("work");
		assertThat(email.getDisplay()).isEqualTo("Work email");
		assertThat(email.getPrimary()).isFalse();
		assertThat(objectMapper.readTree(objectMapper.writeValueAsString(email))).isEqualTo(objectMapper.readTree("""
			{"value": "x@jasper.local", "type": "work", "display": "Work email", "primary": false}
			"""));
	}

	@Test
	void emptyListResponseAllowsAbsentPagination() throws Exception {
		var response = objectMapper.readValue("""
			{"schemas": ["urn:ietf:params:scim:api:messages:2.0:ListResponse"], "totalResults": 0, "Resources": []}
			""", new TypeReference<ScimListResponse<Map<String, Object>>>() {});

		assertThat(response.getTotalResults()).isZero();
		assertThat(response.getResources()).isEmpty();
		assertThat(response.getStartIndex()).isNull();
		assertThat(response.getItemsPerPage()).isNull();
	}
}
