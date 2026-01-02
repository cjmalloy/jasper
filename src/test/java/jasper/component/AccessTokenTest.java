package jasper.component;

import jasper.client.TokenClient;
import jasper.client.dto.TokenDto;
import jasper.config.Config.SecurityConfig;
import jasper.security.Auth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccessTokenTest {

	@Mock
	Auth auth;

	@Mock
	TokenClient tokenClient;

	@Mock
	SecurityConfig securityConfig;

	AccessToken accessToken;

	@BeforeEach
	void init() {
		accessToken = new AccessToken();
		accessToken.auth = auth;
		accessToken.tokenClient = tokenClient;
	}

	@Test
	void testGetAdminToken() {
		when(auth.security()).thenReturn(securityConfig);
		when(securityConfig.getTokenEndpoint()).thenReturn("https://auth.example.com/token");
		when(securityConfig.getClientId()).thenReturn("jasper-client");
		when(securityConfig.getSecret()).thenReturn("secret123");

		var tokenDto = new TokenDto();
		tokenDto.setAccess_token("test-token-12345");
		when(tokenClient.tokenService(any(URI.class), eq("jasper-client"), eq("secret123"), eq("admin")))
			.thenReturn(tokenDto);

		var token = accessToken.getAdminToken();

		assertThat(token).isEqualTo("test-token-12345");
		verify(tokenClient, times(1)).tokenService(any(URI.class), anyString(), anyString(), eq("admin"));
	}

	@Test
	void testGetAdminTokenCached() {
		when(auth.security()).thenReturn(securityConfig);
		when(securityConfig.getTokenEndpoint()).thenReturn("https://auth.example.com/token");
		when(securityConfig.getClientId()).thenReturn("jasper-client");
		when(securityConfig.getSecret()).thenReturn("secret123");

		var tokenDto = new TokenDto();
		tokenDto.setAccess_token("cached-token");
		when(tokenClient.tokenService(any(URI.class), anyString(), anyString(), eq("admin")))
			.thenReturn(tokenDto);

		// First call
		var token1 = accessToken.getAdminToken();
		assertThat(token1).isEqualTo("cached-token");

		// Second call should use cached token
		var token2 = accessToken.getAdminToken();
		assertThat(token2).isEqualTo("cached-token");

		// TokenClient should only be called once due to caching
		verify(tokenClient, times(1)).tokenService(any(URI.class), anyString(), anyString(), eq("admin"));
	}

	@Test
	void testGetAdminTokenWithInvalidUri() {
		when(auth.security()).thenReturn(securityConfig);
		when(securityConfig.getTokenEndpoint()).thenReturn("not a valid uri []");

		assertThatThrownBy(() -> accessToken.getAdminToken())
			.isInstanceOf(RuntimeException.class)
			.hasCauseInstanceOf(java.net.URISyntaxException.class);
	}

	@Test
	void testGetAdminTokenWithDifferentCredentials() {
		when(auth.security()).thenReturn(securityConfig);
		when(securityConfig.getTokenEndpoint()).thenReturn("https://auth.example.com/token");
		when(securityConfig.getClientId()).thenReturn("different-client");
		when(securityConfig.getSecret()).thenReturn("different-secret");

		var tokenDto = new TokenDto();
		tokenDto.setAccess_token("different-token");
		when(tokenClient.tokenService(any(URI.class), eq("different-client"), eq("different-secret"), eq("admin")))
			.thenReturn(tokenDto);

		var token = accessToken.getAdminToken();

		assertThat(token).isEqualTo("different-token");
		verify(tokenClient).tokenService(
			any(URI.class), 
			eq("different-client"), 
			eq("different-secret"), 
			eq("admin")
		);
	}

	@Test
	void testGetAdminTokenWithNullClientId() {
		when(auth.security()).thenReturn(securityConfig);
		when(securityConfig.getTokenEndpoint()).thenReturn("https://auth.example.com/token");
		when(securityConfig.getClientId()).thenReturn(null);
		when(securityConfig.getSecret()).thenReturn("secret");

		var tokenDto = new TokenDto();
		tokenDto.setAccess_token("token");
		when(tokenClient.tokenService(any(URI.class), eq(null), eq("secret"), eq("admin")))
			.thenReturn(tokenDto);

		var token = accessToken.getAdminToken();

		assertThat(token).isEqualTo("token");
	}

	@Test
	void testGetAdminTokenWithNullSecret() {
		when(auth.security()).thenReturn(securityConfig);
		when(securityConfig.getTokenEndpoint()).thenReturn("https://auth.example.com/token");
		when(securityConfig.getClientId()).thenReturn("client");
		when(securityConfig.getSecret()).thenReturn(null);

		var tokenDto = new TokenDto();
		tokenDto.setAccess_token("token");
		when(tokenClient.tokenService(any(URI.class), eq("client"), eq(null), eq("admin")))
			.thenReturn(tokenDto);

		var token = accessToken.getAdminToken();

		assertThat(token).isEqualTo("token");
	}

	@Test
	void testGetAdminTokenWithHttpsEndpoint() {
		when(auth.security()).thenReturn(securityConfig);
		when(securityConfig.getTokenEndpoint()).thenReturn("https://secure.example.com/oauth/token");
		when(securityConfig.getClientId()).thenReturn("client");
		when(securityConfig.getSecret()).thenReturn("secret");

		var tokenDto = new TokenDto();
		tokenDto.setAccess_token("secure-token");
		when(tokenClient.tokenService(any(URI.class), anyString(), anyString(), eq("admin")))
			.thenReturn(tokenDto);

		var token = accessToken.getAdminToken();

		assertThat(token).isEqualTo("secure-token");
	}

	@Test
	void testGetAdminTokenWithHttpEndpoint() {
		when(auth.security()).thenReturn(securityConfig);
		when(securityConfig.getTokenEndpoint()).thenReturn("http://localhost:8080/token");
		when(securityConfig.getClientId()).thenReturn("client");
		when(securityConfig.getSecret()).thenReturn("secret");

		var tokenDto = new TokenDto();
		tokenDto.setAccess_token("local-token");
		when(tokenClient.tokenService(any(URI.class), anyString(), anyString(), eq("admin")))
			.thenReturn(tokenDto);

		var token = accessToken.getAdminToken();

		assertThat(token).isEqualTo("local-token");
	}

	@Test
	void testGetAdminTokenWithEmptyAccessToken() {
		when(auth.security()).thenReturn(securityConfig);
		when(securityConfig.getTokenEndpoint()).thenReturn("https://auth.example.com/token");
		when(securityConfig.getClientId()).thenReturn("client");
		when(securityConfig.getSecret()).thenReturn("secret");

		var tokenDto = new TokenDto();
		tokenDto.setAccess_token("");
		when(tokenClient.tokenService(any(URI.class), anyString(), anyString(), eq("admin")))
			.thenReturn(tokenDto);

		var token = accessToken.getAdminToken();

		assertThat(token).isEmpty();
	}
}
