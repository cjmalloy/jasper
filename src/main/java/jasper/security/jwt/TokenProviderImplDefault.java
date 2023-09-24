package jasper.security.jwt;

import jasper.config.Props;
import jasper.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

public class TokenProviderImplDefault extends AbstractTokenProvider {

	public TokenProviderImplDefault(Props props, UserRepository userRepository) {
		super(props, userRepository);
	}

	@Override
	public boolean validateToken(String jwt, String origin) {
		return true;
	}

	@Override
	public Authentication getAuthentication(String jwt, String origin) {
		var principal = props.getSecurity().getClient(origin).getDefaultUser();
		var user = getUser(principal + origin);
		return new PreAuthenticatedAuthenticationToken(principal, user, getAuthorities(user, origin));
	}
}
