package jasper.security.jwt;

import jasper.component.ConfigCache;
import jasper.config.Props;
import jasper.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import static jasper.domain.proj.Tag.matchesPublic;
import static jasper.security.Auth.USER_TAG_HEADER;
import static jasper.security.Auth.getHeader;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
		var principal = configs.security(origin).getDefaultUser();
		var userTagHeader = getHeader(USER_TAG_HEADER);
		if (isBlank(userTagHeader) || !userTagHeader.matches(User.REGEX)) {
			userTagHeader = "";
		}
		userTagHeader = userTagHeader.toLowerCase();
		if (isNotBlank(userTagHeader) && (props.isAllowUserTagHeader() || matchesPublic(principal, userTagHeader))) {
			principal = userTagHeader;
			logger.debug("{} User tag set by header: {}", origin, principal);
		}
		var user = configs.getUser(principal);
		logger.debug("{} Default Auth {}", origin, principal);
		return new PreAuthenticatedAuthenticationToken(principal + origin, user, getAuthorities(user, origin));
	}
}
