package jasper.security.jwt;

import jasper.component.ConfigCache;
import jasper.config.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import static jasper.security.Auth.USER_TAG_HEADER;
import static jasper.security.Auth.getHeader;
import static org.apache.commons.lang3.StringUtils.isBlank;

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
		var principal = configs.security(origin).getDefaultUser() + origin;
		if (props.isAllowUserTagHeader() && !isBlank(getHeader(USER_TAG_HEADER))) {
			principal = getHeader(USER_TAG_HEADER);
			logger.debug("User tag set by header: {} ({})", principal, origin);
		}
		var user = getUser(principal);
		logger.debug("Default Auth {} ({})", principal, origin);
		return new PreAuthenticatedAuthenticationToken(principal, user, getAuthorities(user, origin));
	}
}
