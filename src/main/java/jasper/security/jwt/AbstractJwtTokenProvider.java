package jasper.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jasper.config.Props;
import jasper.domain.User;
import jasper.domain.proj.HasOrigin;
import jasper.domain.proj.Tag;
import jasper.management.SecurityMetersService;
import jasper.security.UserDetailsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jasper.repository.spec.QualifiedTag.originSelector;
import static jasper.security.Auth.USER_TAG_HEADER;
import static jasper.security.Auth.getHeader;
import static jasper.security.Auth.getOriginHeader;
import static jasper.security.AuthoritiesConstants.ADMIN;
import static jasper.security.AuthoritiesConstants.MOD;
import static jasper.security.AuthoritiesConstants.PRIVATE;
import static jasper.security.AuthoritiesConstants.SA;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public abstract class AbstractJwtTokenProvider extends AbstractTokenProvider implements TokenProvider {
	private final Logger logger = LoggerFactory.getLogger(AbstractJwtTokenProvider.class);

	private static final String INVALID_JWT_TOKEN = "Invalid JWT token.";

	private static final String[] ROOT_ROLES_ALLOWED = new String[]{ MOD, ADMIN, SA };

	Map<String, JwtParser> jwtParser = new HashMap<>();

	private final SecurityMetersService securityMetersService;

	AbstractJwtTokenProvider(Props props, UserDetailsProvider userDetailsProvider, SecurityMetersService securityMetersService) {
		super(props, userDetailsProvider);
		this.securityMetersService = securityMetersService;
	}

	Collection<? extends GrantedAuthority> getAuthorities(Claims claims, User user, String origin) {
		var auth = getPartialAuthorities(claims, origin);
		if (user != null && user.getRole() != null) {
			logger.debug("User Roles: {}", user.getRole());
			if (User.ROLES.contains(user.getRole().trim())) {
				auth.add(new SimpleGrantedAuthority(user.getRole().trim()));
			}
		} else {
			logger.debug("No User");
		}
		return auth;
	}

	List<SimpleGrantedAuthority> getPartialAuthorities(Claims claims, String origin) {
		var auth = getPartialAuthorities(origin);
		var client = props.getSecurity().getClient(origin);
		var authClaim = claims.get(client.getAuthoritiesClaim(), String.class);
		if (isNotBlank(authClaim)) {
			Arrays.stream(authClaim.split(","))
				.filter(r -> !r.isBlank())
				.map(String::trim)
				.map(SimpleGrantedAuthority::new)
				.forEach(auth::add);
		}
		return auth;
	}

	String getUsername(Claims claims, String origin) {
		var client = props.getSecurity().getClient(origin);
		if (client.isAllowUserTagHeader() && !isBlank(getHeader(USER_TAG_HEADER))) {
			return getHeader(USER_TAG_HEADER);
		}
		logger.debug("Sub: {}", client.getUsernameClaim());
		var principal = claims.get(client.getUsernameClaim(), String.class);
		logger.debug("Principal: {}", principal);
		if (props.isAllowLocalOriginHeader() && getOriginHeader() != null) {
			origin = getOriginHeader();
		} else if (!isBlank(principal) && client.isAllowUsernameClaimOrigin() && principal.contains("@")) {
			try {
				var qt = originSelector(principal);
				if (qt.origin.matches(HasOrigin.REGEX)) {
					origin = qt.origin;
					principal = qt.tag;
				}
			} catch (UnsupportedOperationException ignored) {}
		}
		if (principal.contains("@")) {
			principal = principal.substring(0, principal.indexOf("@"));
		}
		var authorities = getPartialAuthorities(claims, origin);
		if (isBlank(principal) ||
			!principal.matches(Tag.QTAG_REGEX) ||
			principal.equals("+user") ||
			principal.equals("_user")) {
			if (authorities.stream().noneMatch(a ->
				Arrays.stream(ROOT_ROLES_ALLOWED).anyMatch(r -> a.getAuthority().equals(r)))) {
				// Invalid username and can't fall back to root user
				logger.debug("Root role not allowed.");
				return null;
			}
			// The root user has access to every other user.
			// Only assign to mods or higher when username is missing.
			if (!"+user".equals(principal)) {
				// Default to private user if +user is not exactly specified
				principal = "_user";
			}
		} else if (!principal.startsWith("+user/") && !principal.startsWith("_user/")) {
			var isPrivate = authorities.stream().map(GrantedAuthority::getAuthority).anyMatch(a -> a.equals(PRIVATE));
			principal = (isPrivate ? "_user/" : "+user/") + principal;
		}
		logger.debug("Username: {}", principal + origin);
		return principal + origin;
	}

	@Override
	public boolean validateToken(String authToken, String origin) {
		if (!StringUtils.hasText(authToken)) return false;
		var client = props.getSecurity().getClient(origin);
		try {
			var claims = jwtParser.get(origin).parseClaimsJws(authToken).getBody();
			if (!client.getAuthentication().getJwt().getClientId().equals(claims.getAudience())) {
				this.securityMetersService.trackTokenInvalidAudience();
				logger.trace(INVALID_JWT_TOKEN + " Invalid Audience");
			} else {
				return true;
			}
		} catch (ExpiredJwtException e) {
			securityMetersService.trackTokenExpired();
			logger.trace(INVALID_JWT_TOKEN, e);
		} catch (UnsupportedJwtException e) {
			securityMetersService.trackTokenUnsupported();
			logger.trace(INVALID_JWT_TOKEN, e);
		} catch (MalformedJwtException e) {
			securityMetersService.trackTokenMalformed();
			logger.trace(INVALID_JWT_TOKEN, e);
		} catch (SignatureException e) {
			securityMetersService.trackTokenInvalidSignature();
			logger.trace(INVALID_JWT_TOKEN, e);
		} catch (IllegalArgumentException e) {
			logger.error("Token validation error {}", e.getMessage());
		}
		return false;
	}
}
