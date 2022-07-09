package jasper.security.jwt;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Set;

public class TokenProviderImplAdmin implements TokenProvider {

	@Override
	public boolean validateToken(String jwt) {
		return true;
	}

	public Authentication getAuthentication(String token) {
		var authorities = Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
		var principal = new User("admin", "", authorities);
		return new UsernamePasswordAuthenticationToken(principal, token, authorities);
	}

}
