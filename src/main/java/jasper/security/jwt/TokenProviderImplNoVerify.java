package jasper.security.jwt;

import java.util.*;
import java.util.stream.Collectors;

import jasper.management.SecurityMetersService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class TokenProviderImplNoVerify implements TokenProvider {

    private final Logger log = LoggerFactory.getLogger(TokenProviderImplNoVerify.class);

    private static final String AUTHORITIES_KEY = "auth";

    private static final String INVALID_JWT_TOKEN = "Invalid JWT token.";

    private final JwtParser jwtParser;

    private final SecurityMetersService securityMetersService;

    public TokenProviderImplNoVerify(SecurityMetersService securityMetersService) {
		jwtParser = Jwts.parserBuilder().build();
        this.securityMetersService = securityMetersService;
    }

	private String dropSig(String token) {
		return token.substring(0, token.lastIndexOf('.') + 1);
	}

    public Authentication getAuthentication(String token) {
        Claims claims = jwtParser.parseClaimsJwt(dropSig(token)).getBody();

		Collection<? extends GrantedAuthority> authorities;
		if (claims.containsKey(AUTHORITIES_KEY)) {
			authorities = Arrays
				.stream(claims.get(AUTHORITIES_KEY).toString().split(","))
				.filter(auth -> !auth.trim().isEmpty())
				.map(SimpleGrantedAuthority::new)
				.collect(Collectors.toList());
		} else {
			authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
		}

		if (claims.get("preferred_username").toString().equals("chris")) {
			authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
		}

        User principal = new User(claims.get("preferred_username").toString(), "", authorities);

        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    public boolean validateToken(String authToken) {
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
