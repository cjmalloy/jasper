package jasper.security;

import jasper.component.ConfigCache;
import jasper.config.Props;
import jasper.repository.RefRepository;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

import static jasper.domain.User.ROLES;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Component
public class AuthFactory {
	private final Props props;
	private final RoleHierarchy roleHierarchy;
	private final ConfigCache configs;
	private final RefRepository refRepository;

	public AuthFactory(Props props, RoleHierarchy roleHierarchy, ConfigCache configs, RefRepository refRepository) {
		this.props = props;
		this.roleHierarchy = roleHierarchy;
		this.configs = configs;
		this.refRepository = refRepository;
	}

	public Auth create() {
		return new Auth(props, roleHierarchy, configs, refRepository);
	}

	/**
	 * Create an {@link Auth} for a user outside of a request, such as a
	 * background task acting on behalf of the user that signed a Ref.
	 * Only the default roles and the role stored on the user are granted.
	 * @param userTag the local user tag, or blank for the default user
	 * @param origin the origin of the user
	 */
	public Auth create(String userTag, String origin) {
		if (isBlank(userTag)) userTag = configs.security(origin).getDefaultUser();
		var principal = userTag + origin;
		var user = isBlank(userTag) ? null : configs.getUser(principal);
		var authorities = new ArrayList<GrantedAuthority>();
		authorities.add(new SimpleGrantedAuthority(props.getDefaultRole()));
		authorities.add(new SimpleGrantedAuthority(configs.security(origin).getDefaultRole()));
		if (user != null && user.getRole() != null && ROLES.contains(user.getRole().trim())) {
			authorities.add(new SimpleGrantedAuthority(user.getRole().trim()));
		}
		var auth = create();
		auth.clear(new PreAuthenticatedAuthenticationToken(principal, user, authorities));
		return auth;
	}
}
