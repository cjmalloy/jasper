package jasper.security.jwt;

import io.jsonwebtoken.Claims;
import jasper.config.ApplicationProperties;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractJwtTokenProvider implements TokenProvider {
	ApplicationProperties applicationProperties;

	public AbstractJwtTokenProvider(ApplicationProperties applicationProperties) {
		this.applicationProperties = applicationProperties;
	}

	Collection<? extends GrantedAuthority> getAuthorities(Claims claims, String origin) {
		Collection<? extends GrantedAuthority> authorities;
		if (claims.containsKey(applicationProperties.getAuthoritiesClaim() + origin)) {
			authorities = Arrays
				.stream(claims.get(applicationProperties.getAuthoritiesClaim() + origin, String.class).split(","))
				.filter(auth -> !auth.trim().isEmpty())
				.map(SimpleGrantedAuthority::new)
				.collect(Collectors.toList());
		} else {
			authorities = List.of(new SimpleGrantedAuthority(applicationProperties.getDefaultRole()));
		}
		return authorities;
	}

	String getUsername(Claims claims) {
		return claims.get(applicationProperties.getUsernameClaim(), String.class);
	}
}
