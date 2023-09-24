package jasper.security.jwt;

import jasper.config.Props;
import jasper.repository.UserRepository;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.security.core.Authentication;

public class TokenProviderImplNop extends AbstractTokenProvider {

	public TokenProviderImplNop(Props props, UserRepository userRepository) {
		super(props, userRepository);
	}

	@Override
	public boolean validateToken(String jwt, String origin) {
		return false;
	}

	@Override
	public Authentication getAuthentication(String jwt, String origin) {
		throw new NotImplementedException();
	}
}
