package jasper.security.jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;

public class TokenProviderImplAdmin implements TokenProvider {
	private static final Logger logger = LoggerFactory.getLogger(TokenProviderImplAdmin.class);

	@Override
	public boolean validateToken(String jwt) {
		return true;
	}

	public Authentication getAuthentication(String token, String origin) {
		logger.debug("Ignoring JWT, all users admin.");
		var authorities = Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
		return new JwtAuthentication("admin", null, authorities);
	}

}
