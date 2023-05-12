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
		super.jwtParser.put("", Jwts.parserBuilder().build());
	}

	private String dropSig(String token) {
		return token.substring(0, token.lastIndexOf('.') + 1);
	}

	public Authentication getAuthentication(String token) {
		var claims = jwtParser.get("").parseClaimsJwt(dropSig(token)).getBody();
		var principal = getUsername(claims);
		var user = getUser(principal);
		return new JwtAuthentication(principal, user, claims, getAuthorities(claims, user));
	}

	@Override
	public boolean validateToken(String authToken) {
		if (!StringUtils.hasText(authToken)) return false;
		return super.validateToken(dropSig(authToken));
	}
}
