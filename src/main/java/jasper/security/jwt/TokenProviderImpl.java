package jasper.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jasper.config.Props;
import jasper.management.SecurityMetersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;

import java.security.Key;
import java.util.Date;
import java.util.stream.Collectors;

public class TokenProviderImpl extends AbstractJwtTokenProvider implements TokenProvider {

	private final Logger log = LoggerFactory.getLogger(TokenProviderImpl.class);

	private static final String INVALID_JWT_TOKEN = "Invalid JWT token.";

	private final Key key;

	private final JwtParser jwtParser;

	private final long tokenValidityInMilliseconds;

	private final long tokenValidityInMillisecondsForRememberMe;

	private final SecurityMetersService securityMetersService;

	public TokenProviderImpl(Props props, SecurityMetersService securityMetersService) {
		super(props);
		byte[] keyBytes;
		String secret = props.getSecurity().getAuthentication().getJwt().getBase64Secret();
		log.debug("Using a Base64-encoded JWT secret key");
		keyBytes = Decoders.BASE64.decode(secret);
		key = Keys.hmacShaKeyFor(keyBytes);
		jwtParser = Jwts.parserBuilder().setSigningKey(key).build();
		this.tokenValidityInMilliseconds = 1000 * props.getSecurity().getAuthentication().getJwt().getTokenValidityInSeconds();
		this.tokenValidityInMillisecondsForRememberMe =
			1000 * props.getSecurity().getAuthentication().getJwt().getTokenValidityInSecondsForRememberMe();
		this.props = props;
		this.securityMetersService = securityMetersService;
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
			.claim(props.getAuthoritiesClaim(), authorities)
			.signWith(key, getAlg())
			.setExpiration(validity)
			.compact();
	}

	private SignatureAlgorithm getAlg() {
		switch (props.getSecurity().getAuthentication().getJwt().getAlg()) {
			case "HS512": return SignatureAlgorithm.HS512;
			case "RS256": return SignatureAlgorithm.RS256;
			case "RS512": return SignatureAlgorithm.RS512;
		}
		log.error("Unsupported signature algorithm {}. Using default HS512.", props.getSecurity().getAuthentication().getJwt().getAlg());
		return SignatureAlgorithm.HS512;
	}

	public Authentication getAuthentication(String token, String origin) {
		Claims claims = jwtParser.parseClaimsJws(token).getBody();
		return new JwtAuthentication(getUsername(claims), claims, getAuthorities(claims, origin));
	}

	public boolean validateToken(String authToken) {
		if (!StringUtils.hasText(authToken)) return false;
		try {
			jwtParser.parseClaimsJws(authToken);

			return true;
		} catch (ExpiredJwtException e) {
			this.securityMetersService.trackTokenExpired();

			log.trace(INVALID_JWT_TOKEN, e);
		} catch (UnsupportedJwtException e) {
			this.securityMetersService.trackTokenUnsupported();

			log.trace(INVALID_JWT_TOKEN, e);
		} catch (MalformedJwtException e) {
			this.securityMetersService.trackTokenMalformed();

			log.trace(INVALID_JWT_TOKEN, e);
		} catch (SignatureException e) {
			this.securityMetersService.trackTokenInvalidSignature();

			log.trace(INVALID_JWT_TOKEN, e);
		} catch (IllegalArgumentException e) { // TODO: should we let it bubble (no catch), to avoid defensive programming and follow the fail-fast principle?
			log.error("Token validation error {}", e.getMessage());
		}

		return false;
	}
}
