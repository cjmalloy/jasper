package jasper.security.jwt;

import jasper.config.ApplicationProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;

public class TokenProviderImplAdmin implements TokenProvider {

	@Override
	public boolean validateToken(String jwt) {
		return true;
	}

	public Authentication getAuthentication(String token) {
		return new JwtAuthentication("admin", null, Set.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
	}

	@Override
	public ApplicationProperties getApplicationProperties() {
		return null;
	}

}
