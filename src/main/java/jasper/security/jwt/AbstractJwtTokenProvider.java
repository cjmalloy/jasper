package jasper.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jasper.config.Props;
import jasper.management.SecurityMetersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractJwtTokenProvider implements TokenProvider {

	private final Logger log = LoggerFactory.getLogger(AbstractJwtTokenProvider.class);

	private static final String INVALID_JWT_TOKEN = "Invalid JWT token.";

	Props props;

	JwtParser jwtParser;

	private final SecurityMetersService securityMetersService;

	public AbstractJwtTokenProvider(Props props, SecurityMetersService securityMetersService) {
		this.props = props;
		this.securityMetersService = securityMetersService;
	}

	Collection<? extends GrantedAuthority> getAuthorities(Claims claims, String origin) {
		if (origin == null) {
			String username = null;
			if (props.isAllowUsernameClaimOrigin() && (username = getUsername(claims)) != null && username.contains("@")) {
				origin = username.substring(username.indexOf("@"));
			} else {
				origin = props.getLocalOrigin();
			}
		}
		var authString = props.getDefaultRole();
		var authClaim = claims.get(props.getAuthoritiesClaim(), Object.class);
		if (authClaim instanceof String auth) {
			authString = auth;
		} else if (authClaim instanceof Map auth && auth.containsKey(origin)) {
			authString = auth.get(origin).toString();
		}
		return Arrays
			.stream(authString.split(","))
			.filter(roles -> !roles.trim().isEmpty())
			.map(SimpleGrantedAuthority::new)
			.collect(Collectors.toList());
	}

	String getUsername(Claims claims) {
		return claims.get(props.getUsernameClaim(), String.class);
	}

	public boolean validateToken(String authToken) {
		if (!StringUtils.hasText(authToken)) return false;
		try {
			var claims = jwtParser.parseClaimsJws(authToken).getBody();
			if (!props.getSecurity().getAuthentication().getJwt().getClientId().equals(claims.getAudience())) {
				this.securityMetersService.trackTokenInvalidAudience();
				log.trace(INVALID_JWT_TOKEN + " Invalid Audience");
			} else {
				return true;
			}
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
		} catch (IllegalArgumentException e) {
			log.error("Token validation error {}", e.getMessage());
		}
		return false;
	}
}
