package jasper.security.jwt;

import io.jsonwebtoken.Jwts;
import jasper.config.Props;
import jasper.management.SecurityMetersService;
import org.springframework.security.core.Authentication;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

public class TokenProviderImplJwks extends AbstractJwtTokenProvider implements TokenProvider {

	public TokenProviderImplJwks(
		Props props,
		SecurityMetersService securityMetersService,
		RestTemplate restTemplate
	) throws URISyntaxException {
		super(props, securityMetersService);
		String jwksUri = props.getSecurity().getAuthentication().getJwt().getJwksUri();
		jwtParser = Jwts.parserBuilder().setSigningKeyResolver(new JwkSigningKeyResolver(new URI(jwksUri), restTemplate)).build();
	}

	public Authentication getAuthentication(String token, String origin) {
		var claims = jwtParser.parseClaimsJws(token).getBody();
		return new JwtAuthentication(getUsername(claims), claims, getAuthorities(claims, origin));
	}
}
