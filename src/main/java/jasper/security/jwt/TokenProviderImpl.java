package jasper.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jasper.component.ConfigCache;
import jasper.config.Props;
import jasper.domain.User;
import jasper.domain.proj.Tag;
import jasper.errors.UserTagInUseException;
import jasper.management.SecurityMetersService;
import jasper.service.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static jasper.domain.proj.HasOrigin.formatOrigin;
import static jasper.security.Auth.USER_TAG_HEADER;
import static jasper.security.Auth.getHeader;
import static jasper.security.Auth.getOriginHeader;
import static jasper.security.AuthoritiesConstants.ADMIN;
import static jasper.security.AuthoritiesConstants.MOD;
import static jasper.security.AuthoritiesConstants.PRIVATE;
import static jasper.util.Logging.getMessage;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.util.StringUtils.hasText;

public class TokenProviderImpl extends AbstractTokenProvider implements TokenProvider {
	private final Logger logger = LoggerFactory.getLogger(TokenProviderImpl.class);

	private static final String INVALID_JWT_TOKEN = "Invalid JWT token.";

	private static final String[] ROOT_ROLES_ALLOWED = new String[]{ MOD, ADMIN };

	Map<String, JwtParser> jwtParsers = new HashMap<>();

	private final SecurityMetersService securityMetersService;
	private final RestTemplate restTemplate;

	public TokenProviderImpl(Props props, ConfigCache caches, SecurityMetersService securityMetersService, RestTemplate restTemplate) {
		super(props, caches);
		this.securityMetersService = securityMetersService;
		this.restTemplate = restTemplate;
	}

	public String createToken(Authentication authentication, int validityInSeconds) {
		var security = configs.security("");
		var authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(","));
		var now = (new Date()).getTime();
		var validity = new Date(now + 1000L * validityInSeconds);
		return Jwts
			.builder()
			.subject(authentication.getName())
				.audience()
				.add(security.getClientId())
			.and()
			.claim(security.getAuthoritiesClaim(), authorities)
			.claim(security.getVerifiedEmailClaim(), true)
			.signWith(Keys.hmacShaKeyFor(security.getSecretBytes()), Jwts.SIG.HS512)
			.expiration(validity)
			.compact();
	}

	public Authentication getAuthentication(String token, String origin) {
		var claims = getParser(origin).parseSignedClaims(token).getPayload();
		var principal = getUsername(claims, origin);
		UserDto user;
		try {
			user = getUser(principal, claims, origin);
		} catch (UserTagInUseException e) {
			principal = principal + "." + Math.floor(Math.random() * 1000);
			user = getUser(principal, claims, origin);
		}
		logger.debug("{} Token Auth {}", origin, principal);
		return new JwtAuthentication(principal, user, claims, getAuthorities(claims, user, origin));
	}

	JwtParser getParser(String origin) {
		var security = configs.security(origin);
		if (!jwtParsers.containsKey(origin)) {
			switch (security.getMode()) {
				case "jwt":
					var key = Keys.hmacShaKeyFor(security.getSecretBytes());
					jwtParsers.put(origin, Jwts.parser().verifyWith(key).build());
					break;
				case "jwks":
                    try {
                        jwtParsers.put(origin, Jwts.parser().setSigningKeyResolver(new JwkSigningKeyResolver(new URI(security.getJwksUri()), restTemplate)).build());
                    } catch (URISyntaxException e) {
						logger.error("Cannot parse JWKS URI {}", security.getJwksUri());
                        throw new RuntimeException(e);
                    }
                    break;
				case "nop":

			}
		}
		return jwtParsers.get(origin);
	}

	Collection<? extends GrantedAuthority> getAuthorities(Claims claims, UserDto user, String origin) {
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
		var authClaim = claims.get(configs.security(origin).getAuthoritiesClaim(), String.class);
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
		if (props.isAllowLocalOriginHeader() && isNotBlank(getOriginHeader())) {
			origin = getOriginHeader();
			logger.debug("{} Origin set by header", origin);
		}
		var principal = "";
		if (props.isAllowUserTagHeader() && !isBlank(getHeader(USER_TAG_HEADER))) {
			principal = getHeader(USER_TAG_HEADER);
			logger.debug("{} User tag set by header: {}", principal);
		} else {
			var security = configs.security(origin);
			principal = claims.get(security.getUsernameClaim(), String.class);
			logger.debug("{} User tag set by JWT claim {}: {})", origin, security.getUsernameClaim(), principal, origin);
			if (security.isExternalId()) {
				var user = configs.getUserByExternalId(origin, principal);
				if (user.isPresent()) {
					logger.debug("{} Username: {} (external ID: {})", origin, user.get(), principal);
					return user.get() + origin;
				}
			}
		}
		logger.debug("Principal: {}", principal);
		if (principal != null && principal.contains("@")) {
			var emailDomain = principal.substring(principal.indexOf("@") + 1);
			principal = principal.substring(0, principal.indexOf("@"));
			var security = configs.security(origin);
			if (security.isEmailDomainInUsername() && !emailDomain.equals(security.getRootEmailDomain())) {
				principal = emailDomain + "/" + principal;
			}
		}
		var authorities = getPartialAuthorities(claims, origin);
		if (isBlank(principal) ||
			!principal.matches(Tag.QTAG_REGEX) ||
			principal.equals("+user") ||
			principal.equals("_user")) {
			logger.debug("{} Invalid principal {}.", origin, principal);
			if (authorities.stream().noneMatch(a ->
				Arrays.stream(ROOT_ROLES_ALLOWED).anyMatch(r -> a.getAuthority().equals(r)))) {
				// Invalid username and can't fall back to root user
				logger.debug("{} Root role not allowed.", origin);
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
		logger.debug("{} Username: {}", origin, principal + origin);
		return principal + origin;
	}

	@Override
	public boolean validateToken(String authToken, String origin) {
		if (!hasText(authToken)) return false;
		var security = configs.security(origin);
		if (isBlank(security.getMode())) {
			logger.error("No client for origin {} in security settings", formatOrigin(origin));
			return false;
		}
		try {
			var parser = getParser(origin);
			if (parser == null) {
				logger.error("No client for origin {} in security settings", formatOrigin(origin));
				return false;
			}
			var claims = parser.parseSignedClaims(authToken).getPayload();
			if (isBlank(security.getClientId()) &&
				claims.getAudience() != null &&
				(!claims.getAudience().contains("") || !claims.getAudience().isEmpty())) {
				securityMetersService.trackTokenInvalidAudience();
				logger.trace(INVALID_JWT_TOKEN + " Invalid Audience");
			} else if (isNotBlank(security.getClientId()) &&
				(claims.getAudience() == null || !claims.getAudience().contains(security.getClientId()) || claims.getAudience().size() != 1)) {
				// TODO: add method to whitelist extra audiences
				securityMetersService.trackTokenInvalidAudience();
				logger.trace(INVALID_JWT_TOKEN + " Invalid Audience");
			} else if (isNotBlank(security.getVerifiedEmailClaim()) && claims.getOrDefault(security.getVerifiedEmailClaim(), Boolean.FALSE).equals(false)) {
				securityMetersService.trackUnverifiedEmail();
				logger.trace(INVALID_JWT_TOKEN + " Email is not verified");
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
			logger.error("Token validation error {}", getMessage(e));
		}
        return false;
	}
}
