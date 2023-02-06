package jasper.security.jwt;

import io.jsonwebtoken.Jwts;
import jasper.config.Props;
import jasper.management.SecurityMetersService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

import static jasper.security.AuthoritiesConstants.PRIVATE;

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
		var authorites = getAuthorities(claims, origin);
		var isPrivate = authorites.stream().map(GrantedAuthority::getAuthority).anyMatch(a -> a.equals(PRIVATE));
		return new JwtAuthentication(getUsername(claims, isPrivate), claims, authorites);
	}
}
