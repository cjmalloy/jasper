package jasper.security.jwt;

import jasper.component.ConfigCache;
import jasper.config.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

public class TokenProviderImplDefault extends AbstractTokenProvider {
	private final Logger logger = LoggerFactory.getLogger(TokenProviderImplDefault.class);

	public TokenProviderImplDefault(Props props, ConfigCache configs) {
		super(props, configs);
	}

	@Override
	public boolean validateToken(String jwt, String origin) {
		return true;
	}

	@Override
	public Authentication getAuthentication(String jwt, String origin) {
		logger.debug("Origin set by default or header {}", origin);
		var principal = configs.security(origin).getDefaultUser() + origin;
		var user = getUser(principal);
		return new PreAuthenticatedAuthenticationToken(principal, user, getAuthorities(user, origin));
	}
}
