package jasper.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jasper.config.Props;
import jasper.management.SecurityMetersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

public class TokenProviderImplJwks extends AbstractJwtTokenProvider implements TokenProvider {
	private static final Logger logger = LoggerFactory.getLogger(TokenProviderImpl.class);

	private final Logger log = LoggerFactory.getLogger(TokenProviderImplJwks.class);

	private static final String INVALID_JWT_TOKEN = "Invalid JWT token.";

	private final JwtParser jwtParser;

	private final SecurityMetersService securityMetersService;
	private final Props props;

	public TokenProviderImplJwks(
		Props props,
		SecurityMetersService securityMetersService,
		RestTemplate restTemplate
	) throws URISyntaxException {
		super(props);
		String jwksUri = props.getSecurity().getAuthentication().getJwt().getJwksUri();
		jwtParser = Jwts.parserBuilder().setSigningKeyResolver(new JwkSigningKeyResolver(new URI(jwksUri), restTemplate)).build();
		this.props = props;
		this.securityMetersService = securityMetersService;
	}

	public Authentication getAuthentication(String token, String origin) {
		Claims claims = jwtParser.parseClaimsJws(token).getBody();
		logger.debug("JWT Claims:  {}", claims.toString());
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
