package jasper.security.jwt;

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

	public UserDetailsProvider userDetailsProvider;

	AbstractTokenProvider(Props props, UserDetailsProvider userDetailsProvider) {
		this.props = props;
		this.userDetailsProvider = userDetailsProvider;
	}

	User getUser(String userTag) {
		if (userDetailsProvider == null) return null;
		return userDetailsProvider.findOneByQualifiedTag(userTag).orElse(null);
	}

	Collection<? extends GrantedAuthority> getAuthorities(User user) {
		var auth = getPartialAuthorities();
		if (user != null && User.ROLES.contains(user.getRole().trim())) {
			auth.add(new SimpleGrantedAuthority(user.getRole().trim()));
		}
		return auth;
	}

	List<SimpleGrantedAuthority> getPartialAuthorities() {
		var authString = props.getDefaultRole();
		if (props.isAllowUserRoleHeader() && isNotBlank(getHeader(USER_ROLE_HEADER))) {
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
