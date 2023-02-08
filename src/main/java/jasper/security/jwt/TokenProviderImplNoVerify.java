package jasper.security.jwt;

import io.jsonwebtoken.Jwts;
import jasper.config.Props;
import jasper.management.SecurityMetersService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;

import static jasper.security.AuthoritiesConstants.PRIVATE;

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
		var authorites = getAuthorities(claims);
		var isPrivate = authorites.stream().map(GrantedAuthority::getAuthority).anyMatch(a -> a.equals(PRIVATE));
		return new JwtAuthentication(getUsername(claims, isPrivate), claims, authorites);
	}

	@Override
	public boolean validateToken(String authToken) {
		if (!StringUtils.hasText(authToken)) return false;
		return super.validateToken(dropSig(authToken));
	}
}
