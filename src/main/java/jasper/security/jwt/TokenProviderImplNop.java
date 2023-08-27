package jasper.security.jwt;

import jasper.config.Props;
import jasper.repository.UserRepository;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

public class TokenProviderImplNop extends AbstractTokenProvider {

	public TokenProviderImplNop(Props props, UserRepository userRepository) {
		super(props, userRepository);
	}

	@Override
	public boolean validateToken(String jwt) {
		return false;
	}

	@Override
	public Authentication getAuthentication(String jwt) {
		throw new NotImplementedException();
	}
}
