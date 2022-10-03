package jasper.security.jwt;

import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.Assert;

import java.util.Collection;

public class JwtAuthentication extends AbstractAuthenticationToken {

	private final Claims claims;
	private final String principal;

	public JwtAuthentication(String principal) {
		super(null);
		this.principal = principal;
		this.claims = null;
		setAuthenticated(false);
	}

	public JwtAuthentication(String principal, Claims claims, Collection<? extends GrantedAuthority> authorities) {
		super(authorities);
		this.principal = principal;
		this.claims = claims;
		super.setAuthenticated(true); // must use super, as we override
	}

	@Override
	public String getPrincipal() {
		return this.principal;
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
	public Claims getDetails() {
		return claims;
	}
}
