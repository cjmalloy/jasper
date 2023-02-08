package jasper.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jasper.config.Props;
import jasper.management.SecurityMetersService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.security.Key;
import java.util.Date;
import java.util.stream.Collectors;

public class TokenProviderImpl extends AbstractJwtTokenProvider implements TokenProvider {

	private final Key key;

	private final long tokenValidityInMilliseconds;

	private final long tokenValidityInMillisecondsForRememberMe;

	public TokenProviderImpl(Props props, SecurityMetersService securityMetersService) {
		super(props, securityMetersService);
		key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.getSecurity().getAuthentication().getJwt().getBase64Secret()));
		jwtParser = Jwts.parserBuilder().setSigningKey(key).build();
		this.tokenValidityInMilliseconds = 1000 * props.getSecurity().getAuthentication().getJwt().getTokenValidityInSeconds();
		this.tokenValidityInMillisecondsForRememberMe =
			1000 * props.getSecurity().getAuthentication().getJwt().getTokenValidityInSecondsForRememberMe();
		this.props = props;
	}

	public String createToken(Authentication authentication, boolean rememberMe) {
		String authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(","));

		long now = (new Date()).getTime();
		Date validity;
		if (rememberMe) {
			validity = new Date(now + this.tokenValidityInMillisecondsForRememberMe);
		} else {
			validity = new Date(now + this.tokenValidityInMilliseconds);
		}

		return Jwts
			.builder()
			.setSubject(authentication.getName())
			.setAudience(props.getSecurity().getAuthentication().getJwt().getClientId())
			.claim(props.getAuthoritiesClaim(), authorities)
			.signWith(key, SignatureAlgorithm.HS512)
			.setExpiration(validity)
			.compact();
	}

	public Authentication getAuthentication(String token) {
		var claims = jwtParser.parseClaimsJws(token).getBody();
		return new JwtAuthentication(getUsername(claims), claims, getAuthorities(claims));
	}
}
