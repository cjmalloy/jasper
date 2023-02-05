package jasper.security.jwt;

import jasper.config.Props;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class TokenProviderImplDefault implements TokenProvider {

	Props props;

	public TokenProviderImplDefault(Props props) {
		this.props = props;
	}

	@Override
	public boolean validateToken(String jwt) {
		return true;
	}

	@Override
	public Authentication getAuthentication(String jwt, String origin) {
		return new AnonymousAuthenticationToken("key", "anonymousUser", List.of(new SimpleGrantedAuthority(props.getDefaultRole())));
	}
}
