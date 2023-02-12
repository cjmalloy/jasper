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
import java.util.stream.Collectors;

import static jasper.repository.spec.QualifiedTag.selector;
import static jasper.security.AuthoritiesConstants.ADMIN;
import static jasper.security.AuthoritiesConstants.MOD;
import static jasper.security.AuthoritiesConstants.PRIVATE;
import static jasper.security.AuthoritiesConstants.SA;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public abstract class AbstractJwtTokenProvider implements TokenProvider {

	private final Logger log = LoggerFactory.getLogger(AbstractJwtTokenProvider.class);

	private static final String INVALID_JWT_TOKEN = "Invalid JWT token.";

	private static final String[] ROOT_ROLES_ALLOWED = new String[]{ MOD, ADMIN, SA };

	Props props;

	JwtParser jwtParser;

	private final SecurityMetersService securityMetersService;

	public AbstractJwtTokenProvider(Props props, SecurityMetersService securityMetersService) {
		this.props = props;
		this.securityMetersService = securityMetersService;
	}

	Collection<? extends GrantedAuthority> getAuthorities(Claims claims) {
		var authString = props.getDefaultRole();
		var authClaim = claims.get(props.getAuthoritiesClaim(), String.class);
		if (isNotBlank(authClaim)) {
			if (isNotBlank(authString)) {
				authString += "," + authClaim;
			} else {
				authString = authClaim;
			}
		}
		return Arrays
			.stream(authString.split(","))
			.filter(roles -> !roles.trim().isEmpty())
			.map(SimpleGrantedAuthority::new)
			.collect(Collectors.toList());
	}

	String getUsername(Claims claims) {
		var principal = claims.get(props.getUsernameClaim(), String.class);
		var authorities = getAuthorities(claims);
		var isPrivate = authorities.stream().map(GrantedAuthority::getAuthority).anyMatch(a -> a.equals(PRIVATE));
		if (isBlank(principal) ||
			principal.equals("+user") ||
			principal.equals("_user") ||
			!principal.matches("^[+_a-z0-9].*")) {
			if (authorities.stream().noneMatch(a ->
				Arrays.stream(ROOT_ROLES_ALLOWED).anyMatch(r -> a.getAuthority().equals(r)))) {
				// Invalid username and can't fall back to root user
				return null;
			}
			// The root user has access to every other user.
			// Only assign to mods or higher when username is missing.
			principal = "user" + (isBlank(principal)  ? "" : selector(principal).origin);
		} else {
			principal = principal.replaceAll("[^a-z.@/]+", ".");
			if (principal.startsWith("+user") || principal.startsWith("_user")) return principal;
			principal = "user/" + principal;
		}
		return isPrivate ? "_" : "+" + principal;
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
			securityMetersService.trackTokenExpired();
			log.trace(INVALID_JWT_TOKEN, e);
		} catch (UnsupportedJwtException e) {
			securityMetersService.trackTokenUnsupported();
			log.trace(INVALID_JWT_TOKEN, e);
		} catch (MalformedJwtException e) {
			securityMetersService.trackTokenMalformed();
			log.trace(INVALID_JWT_TOKEN, e);
		} catch (SignatureException e) {
			securityMetersService.trackTokenInvalidSignature();
			log.trace(INVALID_JWT_TOKEN, e);
		} catch (IllegalArgumentException e) {
			log.error("Token validation error {}", e.getMessage());
		}
		return false;
	}
}
