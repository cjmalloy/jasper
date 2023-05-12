package jasper.security.jwt;

import io.jsonwebtoken.Jwts;
import jasper.config.Props;
import jasper.management.SecurityMetersService;
import jasper.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

public class TokenProviderImplJwks extends AbstractJwtTokenProvider implements TokenProvider {

	public TokenProviderImplJwks(
		Props props,
		UserRepository userRepository,
		SecurityMetersService securityMetersService,
		RestTemplate restTemplate
	) throws URISyntaxException {
		super(props, userRepository, securityMetersService);
		for (var c : props.getSecurity().getClients().entrySet()) {
			var client = c.getKey().equals("default") ? "" : c.getKey();
			String jwksUri = c.getValue().getAuthentication().getJwt().getJwksUri();
			jwtParser.put(client, Jwts.parserBuilder().setSigningKeyResolver(new JwkSigningKeyResolver(new URI(jwksUri), restTemplate)).build());
		}
	}

	public Authentication getAuthentication(String token) {
		var claims = jwtParser.get(getPartialOrigin()).parseClaimsJws(token).getBody();
		var principal = getUsername(claims);
		var user = getUser(principal);
		return new JwtAuthentication(principal, user, claims, getAuthorities(claims, user));
	}
}
