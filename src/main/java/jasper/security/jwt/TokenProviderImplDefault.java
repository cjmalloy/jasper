package jasper.security.jwt;

import org.springframework.security.core.Authentication;

public class TokenProviderImplDefault implements TokenProvider {

	@Override
	public boolean validateToken(String jwt) {
		return false;
	}

	@Override
	public Authentication getAuthentication(String jwt, String origin) {
		return null;
	}
}
