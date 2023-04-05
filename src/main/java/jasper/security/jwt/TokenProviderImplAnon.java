package jasper.security.jwt;

import jasper.config.Props;
import jasper.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

public class TokenProviderImplAnon extends AbstractTokenProvider {

	public TokenProviderImplAnon(Props props, UserRepository userRepository) {
		super(props, userRepository);
	}

	@Override
	public boolean validateToken(String jwt) {
		return true;
	}

	@Override
	public Authentication getAuthentication(String jwt) {
		return new AnonymousAuthenticationToken("key", "anonymousUser", getPartialAuthorities());
	}
}
