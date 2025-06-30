package jasper.security.jwt;

import io.jsonwebtoken.Claims;
import jasper.component.ConfigCache;
import jasper.config.Props;
import jasper.domain.User;
import jasper.errors.UserTagInUseException;
import jasper.service.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static jasper.security.Auth.USER_ROLE_HEADER;
import static jasper.security.Auth.getHeader;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public abstract class AbstractTokenProvider implements TokenProvider {
	private final Logger logger = LoggerFactory.getLogger(AbstractTokenProvider.class);

	public Props props;

	public ConfigCache configs;

	AbstractTokenProvider(Props props, ConfigCache configs) {
		this.props = props;
		this.configs = configs;
	}

	UserDto getUser(String userTag, Claims claims, String origin) {
		var user = configs.getUser(userTag);
		var security = configs.security(origin);
		if (security.isExternalId()) {
			var email = claims.get(security.getUsernameClaim(), String.class);
			if (user == null) {
				return configs.createUser(userTag, origin, email);
			} else if (configs.getUserByExternalId(origin, email).isEmpty() && user.hasExternalId()) {
				// There is no explicit mapping for `email`, but `user` has an explicit mapping,
				// Therefore, the current `userTag` cannot be implicitly mapped to `user`
				logger.warn("{} External ID {} already mapped to user {}", origin, email, userTag);
				throw new UserTagInUseException();
			} else if (!user.hasExternalId(email)) {
				// After a user is implicitly mapped for the first time, save the external ID to make it explicit
				configs.setExternalId(userTag, origin, email);
			}
		}
		return user;
	}

	Collection<? extends GrantedAuthority> getAuthorities(UserDto user, String origin) {
		var auth = getPartialAuthorities(origin);
		if (user != null && user.getRole() != null) {
			logger.debug("User Roles: {}", user.getRole());
			if (User.ROLES.contains(user.getRole().trim())) {
				auth.add(new SimpleGrantedAuthority(user.getRole().trim()));
			}
		} else {
			logger.debug("No User");
		}
		return auth;
	}

	List<SimpleGrantedAuthority> getPartialAuthorities(String origin) {
		var roles = props.getDefaultRole() + ',' + configs.security(origin).getDefaultRole();
		if (props.isAllowUserRoleHeader() && isNotBlank(getHeader(USER_ROLE_HEADER))) {
			logger.debug("Header Roles: {}", getHeader(USER_ROLE_HEADER));
			roles += ", " + getHeader(USER_ROLE_HEADER);
		}
		return Arrays
			.stream(roles.split(","))
			.filter(r -> !r.isBlank())
			.map(String::trim)
			.map(SimpleGrantedAuthority::new)
			.collect(Collectors.toList());
	}
}
