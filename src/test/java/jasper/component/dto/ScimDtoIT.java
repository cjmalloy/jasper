package jasper.component.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins SCIM DTO decoding with the app mapper: ScimClient (Feign, Spring message converters)
 * and ProfileManagerScim (convertValue) both use the primary ObjectMapper.
 */
@IntegrationTest
public class ScimDtoIT {

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void listResponseAcceptsMixedCaseAndUnknownKeys() throws Exception {
		var response = objectMapper.readValue("""
			{
			  "SCHEMAS": ["urn:ietf:params:scim:api:messages:2.0:ListResponse"],
			  "TotalResults": 3,
			  "startindex": 2,
			  "ITEMSPERPAGE": 1,
			  "resources": [{"id": "a", "userName": "x"}],
			  "unknown": {"a": 1}
			}
			""", new TypeReference<ScimListResponse<Map<String, Object>>>() {});

		assertThat(response.getSchemas()).containsExactly("urn:ietf:params:scim:api:messages:2.0:ListResponse");
		assertThat(response.getTotalResults()).isEqualTo(3);
		assertThat(response.getStartIndex()).isEqualTo(2);
		assertThat(response.getItemsPerPage()).isEqualTo(1);
		assertThat(response.getResources()).containsExactly(Map.of("id", "a", "userName", "x"));
		assertThat(objectMapper.writeValueAsString(response)).isEqualTo(
			"{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:ListResponse\"],\"totalResults\":3,\"startIndex\":2,\"itemsPerPage\":1,\"Resources\":[{\"id\":\"a\",\"userName\":\"x\"}]}");
	}

	@Test
	void listResponseAbsentFields() throws Exception {
		var response = objectMapper.readValue("{\"RESOURCES\": [{}]}", new TypeReference<ScimListResponse<Map<String, Object>>>() {});
		assertThat(response.getSchemas()).isNull();
		assertThat(response.getTotalResults()).isZero();
		assertThat(response.getStartIndex()).isNull();
		assertThat(response.getItemsPerPage()).isNull();
		assertThat(response.getResources()).containsExactly(Map.of());
		// No NON_NULL on ScimListResponse: nulls are written
		assertThat(objectMapper.writeValueAsString(response)).isEqualTo(
			"{\"schemas\":null,\"totalResults\":0,\"startIndex\":null,\"itemsPerPage\":null,\"Resources\":[{}]}");
	}

	@Test
	void emailIsCaseSensitiveAndIgnoresUnknownKeys() throws Exception {
		var email = objectMapper.readValue("""
			{"Value": "x@jasper.local", "TYPE": "work", "display": "d", "primary": false, "unknown": 1}
			""", ScimEmail.class);

		// ScimEmail has no ACCEPT_CASE_INSENSITIVE_PROPERTIES: mixed-case keys are dropped
		assertThat(email.getValue()).isNull();
		assertThat(email.getType()).isNull();
		assertThat(email.getDisplay()).isEqualTo("d");
		assertThat(email.getPrimary()).isFalse();
		assertThat(objectMapper.writeValueAsString(email)).isEqualTo("{\"display\":\"d\",\"primary\":false}");
	}

	@Test
	void userResourceFromListResponseLikeProfileManagerScim() throws Exception {
		var response = objectMapper.readValue("""
			{"Resources": [{
			  "id": "user-1",
			  "UserName": "ignored-case",
			  "userName": "x",
			  "unknown": true,
			  "emails": [{"value": "x@jasper.local", "VALUE": "dropped", "extra": 1}],
			  "customClaims": {"roles": "ROLE_USER"}
			}]}
			""", new TypeReference<ScimListResponse<Map<String, Object>>>() {});

		var user = objectMapper.convertValue(response.getResources().get(0), ScimUserResource.class);
		assertThat(user.getId()).isEqualTo("user-1");
		assertThat(user.getUserName()).isEqualTo("x");
		// Absent fields keep their initializer
		assertThat(user.isActive()).isTrue();
		assertThat(user.getSchemas()).containsExactly("urn:ietf:params:scim:schemas:core:2.0:User");
		assertThat(user.getPassword()).isNull();
		assertThat(user.getEmails()).hasSize(1);
		assertThat(user.getEmails().get(0).getValue()).isEqualTo("x@jasper.local");
		assertThat(user.getCustomClaims().get("roles").textValue()).isEqualTo("ROLE_USER");
	}

	@Test
	void userResourceSerialization() throws Exception {
		var user = ScimUserResource.builder()
			.userName("x")
			.emails(List.of(new ScimEmail().setValue("x@jasper.local")))
			.build();
		// No NON_NULL on ScimUserResource: nulls are written, in declaration order
		assertThat(objectMapper.writeValueAsString(user)).isEqualTo(
			"{\"schemas\":[\"urn:ietf:params:scim:schemas:core:2.0:User\"],\"id\":null,\"active\":true,\"userName\":\"x\",\"password\":null,\"customClaims\":null,\"emails\":[{\"value\":\"x@jasper.local\"}]}");
	}
}
