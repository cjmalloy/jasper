package jasper.security.jwt;

import io.jsonwebtoken.Claims;
import jasper.config.Props;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractJwtTokenProvider implements TokenProvider {
	Props props;

	public AbstractJwtTokenProvider(Props props) {
		this.props = props;
	}

	Collection<? extends GrantedAuthority> getAuthorities(Claims claims, String origin) {
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
}
