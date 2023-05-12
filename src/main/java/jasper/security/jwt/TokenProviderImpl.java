package jasper.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jasper.config.Props;
import jasper.management.SecurityMetersService;
import jasper.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class TokenProviderImpl extends AbstractJwtTokenProvider {

	private final Map<String, Key> keys = new HashMap<>();

	private final long tokenValidityInMilliseconds;

	private final long tokenValidityInMillisecondsForRememberMe;

	public TokenProviderImpl(Props props, UserRepository userRepository, SecurityMetersService securityMetersService) {
		super(props, userRepository, securityMetersService);
		for (var c : props.getSecurity().getClients().entrySet()) {
			var client = c.getKey().equals("default") ? "" : c.getKey();
			var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.getSecurity().getClient(getPartialOrigin()).getAuthentication().getJwt().getBase64Secret()));
			this.keys.put(client, key);
			jwtParser.put(client, Jwts.parserBuilder().setSigningKey(key).build());
		}
		this.tokenValidityInMilliseconds = 1000 * props.getSecurity().getClient(getPartialOrigin()).getAuthentication().getJwt().getTokenValidityInSeconds();
		this.tokenValidityInMillisecondsForRememberMe =
			1000 * props.getSecurity().getClient(getPartialOrigin()).getAuthentication().getJwt().getTokenValidityInSecondsForRememberMe();
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
			.setAudience(props.getSecurity().getClient(getPartialOrigin()).getAuthentication().getJwt().getClientId())
			.claim(props.getSecurity().getClient(getPartialOrigin()).getAuthoritiesClaim(), authorities)
			.signWith(keys.get(getPartialOrigin()), SignatureAlgorithm.HS512)
			.setExpiration(validity)
			.compact();
	}

	public Authentication getAuthentication(String token) {
		var claims = jwtParser.get(getPartialOrigin()).parseClaimsJws(token).getBody();
		var principal = getUsername(claims);
		var user = getUser(principal);
		return new JwtAuthentication(principal, user, claims, getAuthorities(claims, user));
	}

	@Override
	public boolean validateToken(String authToken) {
		if (!props.getSecurity().getClients().containsKey(getPartialOrigin())) return false;
		return super.validateToken(authToken);
	}
}
