package jasper.security.jwt;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public interface TokenProvider {
	boolean validateToken(String jwt);
	Authentication getAuthentication(String jwt, String origin);

	default Collection<? extends GrantedAuthority> getAuthorities(Claims claims, String origin) {
		Collection<? extends GrantedAuthority> authorities;
		if (claims.containsKey(getAuthoritiesClaim() + origin)) {
			authorities = Arrays
				.stream(claims.get(getAuthoritiesClaim() + origin, String.class).split(","))
				.filter(auth -> !auth.trim().isEmpty())
				.map(SimpleGrantedAuthority::new)
				.collect(Collectors.toList());
		} else {
			authorities = List.of(new SimpleGrantedAuthority(getDefaultRole()));
		}
		return authorities;
	}

	default String getUsername(Claims claims) {
		return claims.get(getUsernameClaim(), String.class);
	}

	default String[] getOrigins(Claims claims) {
		return claims.get(getOriginsClaim(), String.class).split(",");
	}

	default String getAuthoritiesClaim() {
		return null;
	}

	default String getUsernameClaim() {
		return null;
	}

	default String getOriginsClaim() {
		return null;
	}

	default String getDefaultRole() {
		return null;
	}
}
