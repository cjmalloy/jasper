package jasper.security.jwt;

import io.jsonwebtoken.Jwts;
import jasper.config.Props;
import jasper.management.SecurityMetersService;
import jasper.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

import static jasper.domain.proj.HasOrigin.formatOrigin;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class TokenProviderImplJwks extends AbstractJwtTokenProvider implements TokenProvider {
	private final Logger logger = LoggerFactory.getLogger(TokenProviderImplJwks.class);

	public TokenProviderImplJwks(
		Props props,
		UserRepository userRepository,
		SecurityMetersService securityMetersService,
		RestTemplate restTemplate
	) throws URISyntaxException {
		super(props, userRepository, securityMetersService);
		for (var c : props.getSecurity().clientList()) {
			var client = c.getKey();
			String jwksUri = c.getValue().getAuthentication().getJwt().getJwksUri();
			if (isNotBlank(jwksUri)) jwtParser.put(client, Jwts.parser().setSigningKeyResolver(new JwkSigningKeyResolver(new URI(jwksUri), restTemplate)).build());
		}
	}

	public Authentication getAuthentication(String token, String origin) {
		var claims = jwtParser.get(origin).parseClaimsJws(token).getPayload();
		var principal = getUsername(claims, origin);
		var user = getUser(principal);
		return new JwtAuthentication(principal, user, claims, getAuthorities(claims, user, origin));
	}

	@Override
	public boolean validateToken(String authToken, String origin) {
		if (!props.getSecurity().hasClient(origin)) {
			logger.error("No client for provider {}", formatOrigin(origin));
			return false;
		}
		return super.validateToken(authToken, origin);
	}
}
