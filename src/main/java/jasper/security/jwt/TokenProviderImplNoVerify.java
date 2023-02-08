package jasper.security.jwt;

import io.jsonwebtoken.Jwts;
import jasper.config.Props;
import jasper.management.SecurityMetersService;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;

public class TokenProviderImplNoVerify extends AbstractJwtTokenProvider implements TokenProvider {

	public TokenProviderImplNoVerify(Props props, SecurityMetersService securityMetersService) {
		super(props, securityMetersService);
		jwtParser = Jwts.parserBuilder().build();
		this.props = props;
	}

	private String dropSig(String token) {
		return token.substring(0, token.lastIndexOf('.') + 1);
	}

	public Authentication getAuthentication(String token) {
		var claims = jwtParser.parseClaimsJwt(dropSig(token)).getBody();
		return new JwtAuthentication(getUsername(claims), claims, getAuthorities(claims));
	}

	@Override
	public boolean validateToken(String authToken) {
		if (!StringUtils.hasText(authToken)) return false;
		return super.validateToken(dropSig(authToken));
	}
}
