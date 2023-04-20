package jasper.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import jasper.config.Props;
import jasper.config.SecurityConfiguration;
import jasper.domain.User;
import jasper.security.UserDetailsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static jasper.repository.spec.QualifiedTag.qt;
import static jasper.repository.spec.QualifiedTag.selector;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractJwtTokenProviderUnitTest {

	RoleHierarchy roleHierarchy = new SecurityConfiguration().roleHierarchy();

	AbstractJwtTokenProvider getTokenProvider(String localOrigin, boolean allowUsernameClaimOrigin) {
		return getTokenProvider(getProps(localOrigin, allowUsernameClaimOrigin));
	}

	Claims getClaims(String sub) {
		var claims = new DefaultClaims();
		claims.setSubject(sub);
		return claims;
	}

	Claims getClaims(String sub, String auth) {
		var claims = new DefaultClaims(Map.of("auth", auth));
		claims.setSubject(sub);
		return claims;
	}

	Props getProps(String localOrigin, boolean allowUsernameClaimOrigin) {
		var props = new Props();
		props.setLocalOrigin(localOrigin);
		props.setAllowUsernameClaimOrigin(allowUsernameClaimOrigin);
		props.setUsernameClaim("sub");
		props.setAuthoritiesClaim("auth");
		return props;
	}

	AbstractJwtTokenProvider getTokenProvider(Props props) {
		return new AbstractJwtTokenProvider(props, null, null) {
			@Override
			public Authentication getAuthentication(String jwt) {
				return null;
			}
		};
	}

	User getUser(String userTag, String ...tags) {
		var u = new User();
		var qt = qt(userTag);
		u.setTag(qt.tag);
		u.setOrigin(qt.origin);
		u.setReadAccess(new ArrayList<>(List.of(tags)));
		u.setWriteAccess(new ArrayList<>(List.of(tags)));
		u.setTagReadAccess(new ArrayList<>(List.of(tags)));
		u.setTagWriteAccess(new ArrayList<>(List.of(tags)));
		return u;
	}

	UserDetailsProvider getUserDetailsProvider(User ...users) {
		var mock = mock(UserDetailsProvider.class);
		when(mock.findOneByQualifiedTag(anyString()))
			.thenReturn(Optional.empty());
		for (var user : users) {
			when(mock.findOneByQualifiedTag(user.getQualifiedTag()))
				.thenReturn(Optional.of(user));
		}
		return mock;
	}

	@Test
	void testGetUsername_Default() {
		var tokenProvider = getTokenProvider("", false);

		assertThat(tokenProvider.getUsername(getClaims("alice")))
			.isEqualTo("+user/alice");
	}

	@Test
	void testGetUsername_Default_Invalid() {
		var tokenProvider = getTokenProvider("", false);

		assertThat(tokenProvider.getUsername(getClaims("!!!!!!!")))
			.isNull();
	}

	@Test
	void testGetUsername_Default_Root_Invalid() {
		var tokenProvider = getTokenProvider("", false);

		assertThat(tokenProvider.getUsername(getClaims("!!!!!!!", "ROLE_ADMIN")))
			.isEqualTo("_user");
	}

	@Test
	void testGetUsername_Default_Invalid_Origin() {
		var tokenProvider = getTokenProvider("", false);

		assertThat(tokenProvider.getUsername(getClaims("!!!!!!!")))
			.isNull();
	}

	@Test
	void testGetUsername_Default_Root_Invalid_Origin() {
		var tokenProvider = getTokenProvider("", false);

		assertThat(tokenProvider.getUsername(getClaims("!!!!!!!", "ROLE_ADMIN")))
			.isEqualTo("_user");
	}

	@Test
	void testGetUsername_Default_Invalid_ClaimOrigin() {
		var tokenProvider = getTokenProvider("", true);

		assertThat(tokenProvider.getUsername(getClaims("!!!!!!!@!!!!")))
			.isNull();
	}

	@Test
	void testGetUsername_Default_Root_Invalid_ClaimOrigin() {
		var tokenProvider = getTokenProvider("", true);

		assertThat(tokenProvider.getUsername(getClaims("!!!!!!!@!!!!!", "ROLE_ADMIN")))
			.isEqualTo("_user");
	}

	@Test
	void testGetUsername_Default_InvalidOrigin() {
		var tokenProvider = getTokenProvider("", false);

		assertThat(tokenProvider.getUsername(getClaims("alice@!!!!!!!")))
			.isEqualTo("+user/alice");
	}

	@Test
	void testGetUsername_Default_Root_InvalidOrigin() {
		var tokenProvider = getTokenProvider("", false);

		assertThat(tokenProvider.getUsername(getClaims("alice@!!!!!!!", "ROLE_ADMIN")))
			.isEqualTo("+user/alice");
	}

	@Test
	void testGetUsername_Default_InvalidOrigin_ClaimOrigin() {
		var tokenProvider = getTokenProvider("", true);

		assertThat(tokenProvider.getUsername(getClaims("alice@!!!!")))
			.isEqualTo("+user/alice");
	}

	@Test
	void testGetUsername_Default_Root_InvalidOrigin_ClaimOrigin() {
		var tokenProvider = getTokenProvider("", true);

		assertThat(tokenProvider.getUsername(getClaims("alice@!!!!!", "ROLE_ADMIN")))
			.isEqualTo("+user/alice");
	}

	@Test
	void testGetUsername_Default_ClaimOrigin() {
		var tokenProvider = getTokenProvider("", false);

		assertThat(tokenProvider.getUsername(getClaims("alice@main")))
			.isEqualTo("+user/alice");
	}

	@Test
	void testGetUsername_Default_NoClaimOrigin() {
		var tokenProvider = getTokenProvider("", true);

		assertThat(tokenProvider.getUsername(getClaims("alice@main")))
			.isEqualTo("+user/alice@main");
	}

	@Test
	void testGetUsername_Origin() {
		var tokenProvider = getTokenProvider("@other", false);

		assertThat(tokenProvider.getUsername(getClaims("alice")))
			.isEqualTo("+user/alice@other");
	}

	@Test
	void testGetUsername_Origin_Invalid() {
		var tokenProvider = getTokenProvider("@main", false);

		assertThat(tokenProvider.getUsername(getClaims("!!!!!!!")))
			.isNull();
	}

	@Test
	void testGetUsername_Origin_Root_Invalid() {
		var tokenProvider = getTokenProvider("@main", false);

		assertThat(tokenProvider.getUsername(getClaims("!!!!!!!", "ROLE_ADMIN")))
			.isEqualTo("_user@main");
	}

	@Test
	void testGetUsername_Origin_Invalid_Origin() {
		var tokenProvider = getTokenProvider("@main", false);

		assertThat(tokenProvider.getUsername(getClaims("!!!!!!!")))
			.isNull();
	}

	@Test
	void testGetUsername_Origin_Invalid_ClaimOrigin() {
		var tokenProvider = getTokenProvider("@main", true);

		assertThat(tokenProvider.getUsername(getClaims("!!!!!!!@!!!!")))
			.isNull();
	}

	@Test
	void testGetUsername_Origin_Root_Invalid_ClaimOrigin() {
		var tokenProvider = getTokenProvider("@main", true);

		assertThat(tokenProvider.getUsername(getClaims("!!!!!!!@!!!!!", "ROLE_ADMIN")))
			.isEqualTo("_user@main");
	}

	@Test
	void testGetUsername_Origin_InvalidOrigin() {
		var tokenProvider = getTokenProvider("@main", false);

		assertThat(tokenProvider.getUsername(getClaims("alice@!!!!!!!")))
			.isEqualTo("+user/alice@main");
	}

	@Test
	void testGetUsername_Origin_Root_InvalidOrigin() {
		var tokenProvider = getTokenProvider("@main", false);

		assertThat(tokenProvider.getUsername(getClaims("alice@!!!!!!!", "ROLE_ADMIN")))
			.isEqualTo("+user/alice@main");
	}

	@Test
	void testGetUsername_Origin_InvalidOrigin_ClaimOrigin() {
		var tokenProvider = getTokenProvider("@main", true);

		assertThat(tokenProvider.getUsername(getClaims("alice@!!!!")))
			.isEqualTo("+user/alice@main");
	}

	@Test
	void testGetUsername_Origin_Root_InvalidOrigin_ClaimOrigin() {
		var tokenProvider = getTokenProvider("@main", true);

		assertThat(tokenProvider.getUsername(getClaims("alice@!!!!!", "ROLE_ADMIN")))
			.isEqualTo("+user/alice@main");
	}

	@Test
	void testGetUsername_Origin_NoClaimOrigin() {
		var tokenProvider = getTokenProvider("@other", false);

		assertThat(tokenProvider.getUsername(getClaims("alice@main")))
			.isEqualTo("+user/alice@other");
	}

	@Test
	void testGetUsername_Origin_ClaimOrigin() {
		var tokenProvider = getTokenProvider("@other", true);

		assertThat(tokenProvider.getUsername(getClaims("alice@main")))
			.isEqualTo("+user/alice@main");
	}

	@Test
	void testGetUsername_Blank_Default() {
		var tokenProvider = getTokenProvider("", false);

		assertThat(tokenProvider.getUsername(getClaims("")))
			.isNull();
	}

	@Test
	void testGetUsername_Blank_Default_ClaimOrigin() {
		var tokenProvider = getTokenProvider("", false);

		assertThat(tokenProvider.getUsername(getClaims("@main")))
			.isNull();
	}

	@Test
	void testGetUsername_Blank_Default_NoClaimOrigin() {
		var tokenProvider = getTokenProvider("", true);

		assertThat(tokenProvider.getUsername(getClaims("@main")))
			.isNull();
	}

	@Test
	void testGetUsername_Blank_Origin() {
		var tokenProvider = getTokenProvider("@other", false);

		assertThat(tokenProvider.getUsername(getClaims("+user")))
			.isNull();
	}

	@Test
	void testGetUsername_Blank_Origin_NoClaimOrigin() {
		var tokenProvider = getTokenProvider("@other", false);

		assertThat(tokenProvider.getUsername(getClaims("+user@main")))
			.isNull();
	}

	@Test
	void testGetUsername_Blank_Origin_ClaimOrigin() {
		var tokenProvider = getTokenProvider("@other", true);

		assertThat(tokenProvider.getUsername(getClaims("+user@main")))
			.isNull();
	}

	@Test
	void testGetUsername_BlankProtected_Default() {
		var tokenProvider = getTokenProvider("", false);

		assertThat(tokenProvider.getUsername(getClaims("+user")))
			.isNull();
	}

	@Test
	void testGetUsername_BlankProtected_Default_ClaimOrigin() {
		var tokenProvider = getTokenProvider("", false);

		assertThat(tokenProvider.getUsername(getClaims("+user@main")))
			.isNull();
	}

	@Test
	void testGetUsername_BlankProtected_Default_NoClaimOrigin() {
		var tokenProvider = getTokenProvider("", true);

		assertThat(tokenProvider.getUsername(getClaims("+user@main")))
			.isNull();
	}

	@Test
	void testGetUsername_BlankProtected_Origin() {
		var tokenProvider = getTokenProvider("@other", false);

		assertThat(tokenProvider.getUsername(getClaims("+user")))
			.isNull();
	}

	@Test
	void testGetUsername_BlankProtected_Origin_NoClaimOrigin() {
		var tokenProvider = getTokenProvider("@other", false);

		assertThat(tokenProvider.getUsername(getClaims("+user@main")))
			.isNull();
	}

	@Test
	void testGetUsername_BlankProtected_Origin_ClaimOrigin() {
		var tokenProvider = getTokenProvider("@other", true);

		assertThat(tokenProvider.getUsername(getClaims("+user@main")))
			.isNull();
	}

	@Test
	void testGetUsername_Root_Default() {
		var tokenProvider = getTokenProvider("", false);

		assertThat(tokenProvider.getUsername(getClaims("", "ROLE_ADMIN")))
			.isEqualTo("_user");
	}

	@Test
	void testGetUsername_Root_Default_ClaimOrigin() {
		var tokenProvider = getTokenProvider("", false);

		assertThat(tokenProvider.getUsername(getClaims("@main", "ROLE_ADMIN")))
			.isEqualTo("_user");
	}

	@Test
	void testGetUsername_Root_Default_NoClaimOrigin() {
		var tokenProvider = getTokenProvider("", true);

		assertThat(tokenProvider.getUsername(getClaims("@main", "ROLE_ADMIN")))
			.isEqualTo("_user@main");
	}

	@Test
	void testGetUsername_Root_Origin() {
		var tokenProvider = getTokenProvider("@other", false);

		assertThat(tokenProvider.getUsername(getClaims("", "ROLE_ADMIN")))
			.isEqualTo("_user@other");
	}

	@Test
	void testGetUsername_Root_Origin_NoClaimOrigin() {
		var tokenProvider = getTokenProvider("@other", false);

		assertThat(tokenProvider.getUsername(getClaims("@main", "ROLE_ADMIN")))
			.isEqualTo("_user@other");
	}

	@Test
	void testGetUsername_Root_Origin_ClaimOrigin() {
		var tokenProvider = getTokenProvider("@other", true);

		assertThat(tokenProvider.getUsername(getClaims("@main", "ROLE_ADMIN")))
			.isEqualTo("_user@main");
	}

	@Test
	void testGetUsername_RootProtected_Default() {
		var tokenProvider = getTokenProvider("", false);

		assertThat(tokenProvider.getUsername(getClaims("+user", "ROLE_ADMIN")))
			.isEqualTo("+user");
	}

	@Test
	void testGetUsername_RootProtected_Default_ClaimOrigin() {
		var tokenProvider = getTokenProvider("", false);

		assertThat(tokenProvider.getUsername(getClaims("+user@main", "ROLE_ADMIN")))
			.isEqualTo("+user");
	}

	@Test
	void testGetUsername_RootProtected_Default_NoClaimOrigin() {
		var tokenProvider = getTokenProvider("", true);

		assertThat(tokenProvider.getUsername(getClaims("+user@main", "ROLE_ADMIN")))
			.isEqualTo("+user@main");
	}

	@Test
	void testGetUsername_RootProtected_Origin() {
		var tokenProvider = getTokenProvider("@other", false);

		assertThat(tokenProvider.getUsername(getClaims("+user", "ROLE_ADMIN")))
			.isEqualTo("+user@other");
	}

	@Test
	void testGetUsername_RootProtected_Origin_NoClaimOrigin() {
		var tokenProvider = getTokenProvider("@other", false);

		assertThat(tokenProvider.getUsername(getClaims("+user@main", "ROLE_ADMIN")))
			.isEqualTo("+user@other");
	}

	@Test
	void testGetUsername_RootProtected_Origin_ClaimOrigin() {
		var tokenProvider = getTokenProvider("@other", true);

		assertThat(tokenProvider.getUsername(getClaims("+user@main", "ROLE_ADMIN")))
			.isEqualTo("+user@main");
	}
}
