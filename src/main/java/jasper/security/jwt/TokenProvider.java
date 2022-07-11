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
	Authentication getAuthentication(String jwt);

	default Collection<? extends GrantedAuthority> getAuthorities(Claims claims) {
		Collection<? extends GrantedAuthority> authorities;
		if (claims.containsKey(getAuthoritiesClaim())) {
			authorities = Arrays
				.stream(claims.get(getAuthoritiesClaim()).toString().split(","))
				.filter(auth -> !auth.trim().isEmpty())
				.map(SimpleGrantedAuthority::new)
				.collect(Collectors.toList());
		} else {
			authorities = List.of(new SimpleGrantedAuthority(getDefaultRole()));
		}
		return authorities;
	}

	default String getUsername(Claims claims) {
		return claims.get(getUsernameClaim()).toString();
	}

	default String getAuthoritiesClaim() {
		return null;
	}

	default String getUsernameClaim() {
		return null;
	}

	default String getDefaultRole() {
		return null;
	}
}
