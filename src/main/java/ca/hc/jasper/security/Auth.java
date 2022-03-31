package ca.hc.jasper.security;

import static ca.hc.jasper.repository.spec.RefSpec.hasAnyTag;
import static ca.hc.jasper.repository.spec.RefSpec.hasTag;
import static ca.hc.jasper.repository.spec.TagSpec.*;

import java.util.*;
import java.util.stream.Stream;

import ca.hc.jasper.domain.Ref;
import ca.hc.jasper.domain.User;
import ca.hc.jasper.domain.proj.HasTags;
import ca.hc.jasper.domain.proj.IsTag;
import ca.hc.jasper.repository.RefRepository;
import ca.hc.jasper.repository.UserRepository;
import ca.hc.jasper.repository.spec.QualifiedTag;
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

	public boolean canReadRef(HasTags ref) {
		if (hasRole("MOD")) return true;
		if (ref.getTags() != null) {
			if (ref.getTags().contains("public")) return true;
			if (hasRole("USER")) {
				var qualifiedTags = ref.getQualifiedTags();
				if (qualifiedTags.contains(getUserTag())) return true;
				return captures(getReadAccess(), qualifiedTags);
			}
		}
		return false;
	}

	public boolean canWriteRef(Ref ref) {
		if (!canWriteRef(ref.getUrl())) return false;
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), "");
		if (!newTags(ref.getQualifiedTags(), maybeExisting.map(Ref::getQualifiedTags)).allMatch(this::canReadTag)) return false;
		return true;
	}

	public boolean canWriteRef(String url) {
		if (hasRole("ADMIN")) return true;
		var maybeExisting = refRepository.findOneByUrlAndOrigin(url, "");
		if (maybeExisting.isEmpty()) return true; // Idempotent deletes
		var existing = maybeExisting.get();
		if (existing.getTags() != null && hasRole("USER")) {
			if (existing.getTags().contains("locked")) return false;
			if (hasRole("MOD")) return true;
			var qualifiedTags = existing.getQualifiedTags();
			if (qualifiedTags.contains(getUserTag())) return true;
			return captures(getWriteAccess(), qualifiedTags);
		}
		return false;
	}

	public boolean canReadTag(String tag) {
		if (!tag.startsWith("_")) return true;
		if (hasRole("MOD")) return true;
		if (hasRole("USER")) {
			if (tag.equals(getUserTag())) return true;
			var readAccess = getReadAccess();
			if (readAccess == null) return false;
			return captures(readAccess, List.of(tag));
		}
		return false;
	}

	public boolean canWriteTag(String tag) {
		if (hasRole("MOD")) return true;
		if (hasRole("USER")) {
			if (tag.equals(getUserTag())) return true;
			var writeAccess = getWriteAccess();
			if (writeAccess == null) return false;
			return captures(writeAccess, List.of(tag));
		}
		return false;
	}

	public boolean canWriteUser(User user) {
		if (hasRole("MOD")) return true;
		if (!canWriteTag(user.getQualifiedTag())) return false;
		var maybeExisting = userRepository.findOneByQualifiedTag(user.getQualifiedTag());
		if (!newTags(user.getReadAccess(), maybeExisting.map(User::getReadAccess)).allMatch(this::canWriteTag)) return false;
		if (!newTags(user.getWriteAccess(), maybeExisting.map(User::getWriteAccess)).allMatch(this::canWriteTag)) return false;
		if (!newTags(user.getSubscriptions(), maybeExisting.map(User::getSubscriptions)).allMatch(this::canReadTag)) return false;
		return true;
	}

	public List<String> filterTags(List<String> tags) {
		if (tags == null) return null;
		if (hasRole("MOD")) return tags;
		return tags.stream().filter(this::canReadTag).toList();
	}

	public List<String> hiddenTags(List<String> tags) {
		if (hasRole("MOD")) return null;
		if (tags == null) return null;
		return tags.stream().filter(tag -> !canReadTag(tag)).toList();
	}

	public <T extends HasTags> Specification<T> refReadSpec() {
		if (hasRole("MOD")) return null;
		var spec = Specification
			.<T>where(hasTag("public"));
		if (hasRole("USER")) {
			spec = spec.or(hasTag(getUserTag()))
					   .or(hasAnyTag(getReadAccess()));
		}
		return spec;
	}

	public <T extends IsTag> Specification<T> tagReadSpec() {
		if (hasRole("MOD")) return null;
		var spec = Specification
			.<T>where(publicTag());
		if (hasRole("USER")) {
			spec = spec.or(isTag(getUserTag()))
					   .or(isAnyTag(getReadAccess()));
		}
		return spec;
	}

	private static boolean captures(List<String> selectors, List<String> target) {
		if (selectors == null) return false;
		if (selectors.isEmpty()) return false;
		if (target == null) return false;
		if (target.isEmpty()) return false;
		for (String selector : selectors) {
			var s = new QualifiedTag(selector);
			for (String t : target) {
				if (s.captures(t)) return true;
			}
		}
		return false;
	}

	private static Stream<String> newTags(List<String> changes, Optional<List<String>> existing) {
		if (changes == null) return Stream.empty();
		if (existing.isEmpty()) return changes.stream();
		return changes.stream().filter(tag -> !existing.get().contains(tag));
	}

	public String getUserTag() {
		var user = getPrincipal();
		if (user == null) return null;
		if (hasRole("PRIVATE")) {
			return "_user/" + user.getUsername();
		} else {
			return "user/" + user.getUsername();
		}
	}

	public Optional<User> getUser() {
		if (user == null) {
			user = userRepository.findOneByQualifiedTag(getUserTag());
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
		var principal = getAuthentication().getPrincipal();
		if (principal instanceof UserDetails) return (UserDetails) principal;
		return null;
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
