package jasper.security.jwt;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;

public class TokenProviderImplAdmin implements TokenProvider {

	@Override
	public boolean validateToken(String jwt) {
		return true;
	}

	public Authentication getAuthentication(String token, String origin) {
		var authorities = Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
		return new JwtAuthentication("admin", null, authorities);
	}

}
