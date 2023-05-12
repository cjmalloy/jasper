package jasper.security.jwt;

import org.springframework.security.core.Authentication;

public interface TokenProvider {
	boolean validateToken(String jwt);
	Authentication getAuthentication(String jwt);
	String getPartialOrigin();
}
