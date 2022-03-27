package ca.hc.jasper.component;

import java.util.*;

import ca.hc.jasper.domain.Queue;
import ca.hc.jasper.domain.*;
import ca.hc.jasper.domain.proj.IsTag;
import ca.hc.jasper.repository.RefRepository;
import ca.hc.jasper.repository.UserRepository;
import ca.hc.jasper.repository.spec.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class Auth {
	private static final Logger logger = LoggerFactory.getLogger(Auth.class);
	private static final String ROLE_PREFIX = "ROLE_";

	@Autowired
	RoleHierarchy roleHierarchy;
	@Autowired
	UserRepository userRepository;
	@Autowired
	RefRepository refRepository;

	// Cache
	private Set<String> roles;
	private Optional<User> user;

	public boolean canReadRef(Ref ref) {
		if (!ref.local()) return true;
		if (hasRole("MOD")) return true;
		if (ref.getTags() != null) {
			if (ref.getTags().contains("public")) return true;
			if (hasRole("USER")) {
				if (ref.getTags().contains(getUserTag())) return true;
				return anyMatch(getReadAccess(), ref.getTags());
			}
		}
		return false;
	}

	public boolean canWriteRef(String url) {
		if (hasRole("MOD")) return true;
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, "");
		if (maybeRef.isEmpty()) return true; // Idempotent deletes
		var ref = maybeRef.get();
		if (ref.getTags() != null && hasRole("USER")) {
			if (ref.getTags().contains(getUserTag())) return true;
			return anyMatch(getWriteAccess(), ref.getTags());
		}
		return false;
	}

	public boolean canReadTag(IsTag tag) {
		if (!tag.local()) return true;
		if (!tag.getTag().startsWith("_")) return true;
		if (hasRole("MOD")) return true;
		if (hasRole("USER")) {
			if (tag.getTag().equals(getUserTag())) return true;
			var readAccess = getReadAccess();
			if (readAccess == null) return false;
			return readAccess.contains(tag.getTag());
		}
		return false;
	}

	public boolean canWriteTag(String tag) {
		if (hasRole("MOD")) return true;
		if (hasRole("USER")) {
			if (tag.equals(getUserTag())) return true;
			var writeAccess = getWriteAccess();
			if (writeAccess == null) return false;
			return writeAccess.contains(tag);
		}
		return false;
	}

	public Specification<Ref> refReadSpec() {
		if (hasRole("MOD")) return null;
		var spec = Specification
			.where(RefSpec.hasTag("public"));
		if (hasRole("USER")) {
			spec = spec.or(RefSpec.hasTag(getUserTag()))
					   .or(RefSpec.hasAnyTag(getReadAccess()));
		}
		return spec;
	}

	public Specification<Tag> tagReadSpec() {
		if (hasRole("MOD")) return null;
		var spec = Specification
			.where(TagSpec.publicTag());
		if (hasRole("USER")) {
			spec = spec.or(TagSpec.isTag(getUserTag()))
					   .or(TagSpec.isAny(getReadAccess()));
		}
		return spec;
	}

	public Specification<Queue> queueReadSpec() {
		if (hasRole("MOD")) return null;
		var spec = Specification
			.where(QueueSpec.publicQueue());
		if (hasRole("USER")) {
			spec = spec.or(QueueSpec.isTag(getUserTag()))
					   .or(QueueSpec.isAny(getReadAccess()));
		}
		return spec;
	}

	private static boolean anyMatch(List<String> xs, List<String> ys) {
		if (xs == null) return false;
		if (xs.isEmpty()) return false;
		if (ys == null) return false;
		if (ys.isEmpty()) return false;
		for (String x : xs) {
			for (String y : ys) {
				if (x.equals(y)) return true;
			}
		}
		return false;
	}

	public String getUserTag() {
		if (hasRole("PRIVATE")) {
			return "_user/" + getPrincipal().getUsername();
		} else {
			return "user/" + getPrincipal().getUsername();
		}
	}

	public Optional<User> getUser() {
		if (user == null) {
			user = userRepository.findOneByTagAndOrigin(getUserTag(), "");
		}
		return user;
	}

	public List<String> getReadAccess() {
		return getUser().map(User::getReadAccess).orElse(null);
	}

	public List<String> getWriteAccess() {
		return getUser().map(User::getWriteAccess).orElse(null);
	}

	public boolean hasAuthority(String authority) {
		return hasAnyAuthority(authority);
	}

	public boolean hasAnyAuthority(String... authorities) {
		return hasAnyAuthorityName(null, authorities);
	}

	public boolean hasRole(String role) {
		return hasAnyRole(role);
	}

	public boolean hasAnyRole(String... roles) {
		return hasAnyAuthorityName(ROLE_PREFIX, roles);
	}

	private boolean hasAnyAuthorityName(String prefix, String... roles) {
		Set<String> roleSet = getAuthoritySet();
		for (String role : roles) {
			String defaultedRole = getRoleWithPrefix(prefix, role);
			if (roleSet.contains(defaultedRole)) {
				return true;
			}
		}
		return false;
	}

	public Authentication getAuthentication() {
		return SecurityContextHolder.getContext().getAuthentication();
	}

	public UserDetails getPrincipal() {
		return (UserDetails) getAuthentication().getPrincipal();
	}

	private Set<String> getAuthoritySet() {
		if (roles == null) {
			Collection<? extends GrantedAuthority> userAuthorities = getAuthentication().getAuthorities();
			if (roleHierarchy != null) {
				userAuthorities = roleHierarchy.getReachableGrantedAuthorities(userAuthorities);
			}
			roles = AuthorityUtils.authorityListToSet(userAuthorities);
		}
		return roles;
	}

	private static String getRoleWithPrefix(String prefix, String role) {
		if (role == null) return null;
		if (prefix == null) return role;
		if (prefix.length() == 0) return role;
		if (role.startsWith(prefix)) return role;
		return prefix + role;
	}

}
