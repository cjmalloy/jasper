package jasper.security.jwt;

import io.jsonwebtoken.Claims;
import jasper.service.dto.UserDto;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.Assert;

import java.util.Collection;

public class JwtAuthentication extends AbstractAuthenticationToken {

	private final UserDto user;
	private final Claims claims;
	private final String principal;

	public JwtAuthentication(String principal) {
		super(null);
		this.principal = principal;
		this.user = null;
		this.claims = null;
		setAuthenticated(false);
	}

	public JwtAuthentication(String principal, UserDto user, Claims claims, Collection<? extends GrantedAuthority> authorities) {
		super(authorities);
		this.principal = principal;
		this.user = user;
		this.claims = claims;
		super.setAuthenticated(true); // must use super, as we override
	}

	@Override
	public String getPrincipal() {
		return principal;
	}

	@Override
	public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
		Assert.isTrue(!isAuthenticated,
			"Cannot set this token to trusted - use constructor which takes a GrantedAuthority list instead");
		super.setAuthenticated(false);
	}

	@Override
	public Object getCredentials() {
		return null;
	}

	@Override
	public UserDto getDetails() {
		return user;
	}

	public Claims getClaims() {
		return claims;
	}
}
