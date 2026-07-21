package jasper.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.IntegrationTest;
import jasper.component.ConfigCache;
import jasper.domain.User;
import jasper.repository.UserRepository;
import jasper.security.jwt.Claims;
import jasper.security.jwt.JWTFilter;
import jasper.security.jwt.JwtAuthentication;
import jasper.security.jwt.TokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link UserController}.
 * Tests cross-origin write prevention with Local-Origin header.
 */
@WithMockUser(value = "+user/tester@a", roles = {"ADMIN"})
@AutoConfigureMockMvc
@IntegrationTest
class UserControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private TokenProvider tokenProvider;

	@Autowired
	private ConfigCache configCache;

	@BeforeEach
	void setup() {
		userRepository.deleteAll();
		configCache.clearUserCache();
	}

	@Test
	void testCreateUserWithDifferentOriginThanLocalOriginHeaderShouldFail() throws Exception {
		var user = new User();
		user.setTag("+user/test");
		user.setOrigin("@b");
		user.setRole("ROLE_USER");

		mockMvc
			.perform(post("/api/v1/user")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(user))
				.header("Local-Origin", "@a")
				.with(csrf().asHeader()))
			.andExpect(status().isForbidden());

		assertThat(userRepository.count()).isZero();
	}

	@Test
	void testCreateUserWithParentOriginFromSubOriginShouldFail() throws Exception {
		var user = new User();
		user.setTag("+user/test");
		user.setOrigin("@a");
		user.setRole("ROLE_USER");

		mockMvc
			.perform(post("/api/v1/user")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(user))
				.header("Local-Origin", "@a.b")
				.with(csrf().asHeader()))
			.andExpect(status().isForbidden());

		assertThat(userRepository.count()).isZero();
	}

	@Test
	void testUpdateUserWithDifferentOriginThanLocalOriginHeaderShouldFail() throws Exception {
		var user = new User();
		user.setTag("+user/test");
		user.setOrigin("@b");
		user.setRole("ROLE_USER");
		userRepository.save(user);

		user.setRole("ROLE_EDITOR");

		mockMvc
			.perform(put("/api/v1/user")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(user))
				.header("Local-Origin", "@a")
				.with(csrf().asHeader()))
			.andExpect(status().isForbidden());

		var existing = userRepository.findOneByQualifiedTag("+user/test@b");
		assertThat(existing).isPresent();
		assertThat(existing.get().getRole()).isEqualTo("ROLE_USER");
	}

	@Test
	void testUpdateAuthorizedKeysWithJwtUserRole() throws Exception {
		var user = new User();
		user.setTag("+user/target");
		user.setOrigin("@a");
		user.setRole("ROLE_USER");
		userRepository.save(user);

		user.setAuthorizedKeys("ssh-ed25519 test-key");
		var authenticatedUser = new User();
		authenticatedUser.setTag("+user/tester");
		authenticatedUser.setOrigin("@a");
		authenticatedUser.setTagWriteAccess(new ArrayList<>(List.of("+user/target")));
		var authentication = new JwtAuthentication("+user/tester@a", authenticatedUser, Claims.EMPTY,
			AuthorityUtils.createAuthorityList("ROLE_USER"));
		var token = "test-token";
		when(tokenProvider.validateToken(token, "@a")).thenReturn(true);
		when(tokenProvider.getAuthentication(token, "@a")).thenReturn(authentication);

		mockMvc
			.perform(put("/api/v1/user")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(user))
				.header(JWTFilter.AUTHORIZATION_HEADER, "Bearer " + token)
				.header("Local-Origin", "@a")
				.with(csrf().asHeader()))
			.andExpect(status().isOk());

		var existing = userRepository.findOneByQualifiedTag("+user/target@a");
		assertThat(existing).isPresent();
		assertThat(existing.get().getAuthorizedKeys()).isEqualTo("ssh-ed25519 test-key");
	}
}
