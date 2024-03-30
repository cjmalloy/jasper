package jasper.security.jwt;

import jasper.component.ConfigCache;
import jasper.config.Config.*;
import jasper.config.Props;
import jasper.domain.User;
import jasper.security.UserDetailsProvider;
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

	public UserDetailsProvider userDetailsProvider;

	AbstractTokenProvider(Props props, UserDetailsProvider userDetailsProvider, ConfigCache configs) {
		this.props = props;
		this.userDetailsProvider = userDetailsProvider;
		this.configs = configs;
	}

	ServerConfig root() {
		return configs.getTemplate("_config/server", "", ServerConfig.class);
	}

	SecurityConfig security(String origin) {
		return configs.getTemplate("_config/security", origin, SecurityConfig.class);
	}

	User getUser(String userTag) {
		if (userDetailsProvider == null) return null;
		return userDetailsProvider.findOneByQualifiedTag(userTag).orElse(null);
	}

	Collection<? extends GrantedAuthority> getAuthorities(User user, String origin) {
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
		var client = props.getSecurity().getClient(origin);
		var authString = client == null ? "ROLE_ANONYMOUS" : security(origin).getDefaultRole();
		if (client.isAllowUserRoleHeader() && isNotBlank(getHeader(USER_ROLE_HEADER))) {
			logger.debug("Header Roles: {}", getHeader(USER_ROLE_HEADER));
			authString += ", " + getHeader(USER_ROLE_HEADER);
		}
		return Arrays
			.stream(authString.split(","))
			.filter(r -> !r.isBlank())
			.map(String::trim)
			.map(SimpleGrantedAuthority::new)
			.collect(Collectors.toList());
	}
}
