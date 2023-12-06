package jasper.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jasper.config.Props;
import jasper.management.SecurityMetersService;
import jasper.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static jasper.domain.proj.HasOrigin.formatOrigin;

public class TokenProviderImpl extends AbstractJwtTokenProvider {
	private final Logger logger = LoggerFactory.getLogger(TokenProviderImpl.class);

	private final Map<String, Key> keys = new HashMap<>();

	private final long tokenValidityInMilliseconds;

	private final long tokenValidityInMillisecondsForRememberMe;

	public TokenProviderImpl(Props props, UserRepository userRepository, SecurityMetersService securityMetersService) {
		super(props, userRepository, securityMetersService);
		for (var c : props.getSecurity().clientList()) {
			var client = c.getKey();
			var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(c.getValue().getAuthentication().getJwt().getBase64Secret()));
			keys.put(client, key);
			jwtParser.put(client, Jwts.parserBuilder().setSigningKey(key).build());
		}
		tokenValidityInMilliseconds = 1000 * props.getSecurity().getClient(props.getLocalOrigin()).getAuthentication().getJwt().getTokenValidityInSeconds();
		tokenValidityInMillisecondsForRememberMe = 1000 * props.getSecurity().getClient(props.getLocalOrigin()).getAuthentication().getJwt().getTokenValidityInSecondsForRememberMe();
	}

	public String createToken(Authentication authentication, boolean rememberMe) {
		String authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(","));

		long now = (new Date()).getTime();
		Date validity;
		if (rememberMe) {
			validity = new Date(now + tokenValidityInMillisecondsForRememberMe);
		} else {
			validity = new Date(now + tokenValidityInMilliseconds);
		}

		return Jwts
			.builder()
			.setSubject(authentication.getName())
			.setAudience(props.getSecurity().getClient("").getAuthentication().getJwt().getClientId())
			.claim(props.getSecurity().getClient("").getAuthoritiesClaim(), authorities)
			.signWith(keys.get(""), SignatureAlgorithm.HS512)
			.setExpiration(validity)
			.compact();
	}

	public Authentication getAuthentication(String token, String origin) {
		var claims = jwtParser.get(origin).parseClaimsJws(token).getBody();
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
