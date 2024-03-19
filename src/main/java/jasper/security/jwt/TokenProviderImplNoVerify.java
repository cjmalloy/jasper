package jasper.security.jwt;

import io.jsonwebtoken.Jwts;
import jasper.config.Props;
import jasper.management.SecurityMetersService;
import jasper.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;

public class TokenProviderImplNoVerify extends AbstractJwtTokenProvider {

	public TokenProviderImplNoVerify(Props props, UserRepository userRepository, SecurityMetersService securityMetersService) {
		super(props, userRepository, securityMetersService);
		super.jwtParser.put("", Jwts.parser().build());
	}

	private String dropSig(String token) {
		return token.substring(0, token.lastIndexOf('.') + 1);
	}

	public Authentication getAuthentication(String token, String origin) {
		var claims = jwtParser.get("").parseUnsecuredClaims(dropSig(token)).getPayload();
		var principal = getUsername(claims, origin);
		var user = getUser(principal);
		return new JwtAuthentication(principal, user, claims, getAuthorities(claims, user, origin));
	}

	@Override
	public boolean validateToken(String authToken, String origin) {
		if (!StringUtils.hasText(authToken)) return false;
		return super.validateToken(dropSig(authToken), origin);
	}
}
