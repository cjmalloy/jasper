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

public class TokenProviderImplNoVerify extends AbstractJwtTokenProvider implements TokenProvider {

	private final Logger log = LoggerFactory.getLogger(TokenProviderImplNoVerify.class);

	private static final String INVALID_JWT_TOKEN = "Invalid JWT token.";

	private final JwtParser jwtParser;

	private final SecurityMetersService securityMetersService;
	private final Props props;

	public TokenProviderImplNoVerify(Props props, SecurityMetersService securityMetersService) {
		super(props);
		jwtParser = Jwts.parserBuilder().build();
		this.props = props;
		this.securityMetersService = securityMetersService;
	}

	private String dropSig(String token) {
		return token.substring(0, token.lastIndexOf('.') + 1);
	}

	public Authentication getAuthentication(String token, String origin) {
		Claims claims = jwtParser.parseClaimsJwt(dropSig(token)).getBody();
		return new JwtAuthentication(getUsername(claims), claims, getAuthorities(claims, origin));
	}

	public boolean validateToken(String authToken) {
		if (!StringUtils.hasText(authToken)) return false;
		try {
			jwtParser.parseClaimsJwt(dropSig(authToken));

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
