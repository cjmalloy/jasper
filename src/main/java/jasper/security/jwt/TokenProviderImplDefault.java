package jasper.security.jwt;

import jasper.config.Props;
import jasper.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

public class TokenProviderImplDefault extends AbstractTokenProvider {
	private final Logger logger = LoggerFactory.getLogger(TokenProviderImplDefault.class);

	public TokenProviderImplDefault(Props props, UserRepository userRepository) {
		super(props, userRepository);
	}

	@Override
	public boolean validateToken(String jwt, String origin) {
		return true;
	}

	@Override
	public Authentication getAuthentication(String jwt, String origin) {
		logger.debug("Origin set by default or header {}", origin);
		var principal = props.getSecurity().getClient(origin).getDefaultUser() + origin;
		var user = getUser(principal);
		return new PreAuthenticatedAuthenticationToken(principal, user, getAuthorities(user, origin));
	}
}
