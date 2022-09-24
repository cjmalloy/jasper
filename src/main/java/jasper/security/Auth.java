package jasper.security;

import jasper.domain.Ref;
import jasper.domain.User;
import jasper.domain.proj.HasTags;
import jasper.domain.proj.Tag;
import jasper.errors.FreshLoginException;
import jasper.repository.RefRepository;
import jasper.repository.UserRepository;
import jasper.repository.filter.Query;
import jasper.repository.spec.QualifiedTag;
import jasper.security.jwt.JwtAuthentication;
import org.apache.commons.collections4.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static jasper.repository.spec.RefSpec.hasAnyTag;
import static jasper.repository.spec.RefSpec.hasTag;
import static jasper.repository.spec.TagSpec.isAnyTag;
import static jasper.repository.spec.TagSpec.isTag;
import static jasper.repository.spec.TagSpec.publicTag;
import static jasper.security.AuthoritiesConstants.EDITOR;
import static jasper.security.AuthoritiesConstants.MOD;
import static jasper.security.AuthoritiesConstants.PRIVATE;
import static jasper.security.AuthoritiesConstants.ROLE_PREFIX;
import static jasper.security.AuthoritiesConstants.USER;
import static jasper.security.AuthoritiesConstants.VIEWER;

@Component
@RequestScope
public class Auth {
	private static final Logger logger = LoggerFactory.getLogger(Auth.class);

	public static final String ORIGIN_HEADER = "Origin";

	@Autowired
	RoleHierarchy roleHierarchy;
	@Autowired
	UserRepository userRepository;
	@Autowired
	RefRepository refRepository;

	// Cache
	protected Set<String> roles;
	protected Optional<User> user;
	protected String userTag;
	protected String origin;

	public boolean local(String origin) {
		return this.getOrigin().equals(origin);
	}

	public boolean freshLogin() {
		var auth = getAuthentication();
		if (auth instanceof JwtAuthentication j) {
			var iat = j.getDetails().getIssuedAt();
			if (iat != null && iat.toInstant().isBefore(Instant.now().minus(Duration.of(15, ChronoUnit.MINUTES)))) {
				return true;
			}
		}
		throw new FreshLoginException();
	}

	public boolean canReadRef(HasTags ref) {
		if (hasRole(MOD)) return true;
		if (ref.getTags() == null) return false;
		if (ref.getTags().contains("public")) return true;
		if (!hasRole(VIEWER)) return false;
		var qualifiedTags = ref.getQualifiedTags();
		return captures(getUserTag(), qualifiedTags) ||
			captures(getReadAccess(), qualifiedTags);
	}

	public boolean canReadRef(String url, String origin) {
		if (hasRole(MOD)) return true;
		var maybeExisting = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeExisting.isEmpty()) return false;
		return canReadRef(maybeExisting.get());
	}

	public boolean canWrite(String tag) {
		if (hasRole(MOD)) return true;
		if (!hasRole(USER)) return false;
		if (tag.equals(getUserTag())) return true;
		return captures(getWriteAccess(), List.of(tag));
	}

	public boolean canWriteRef(Ref ref) {
		if (!canWriteRef(ref.getUrl(), ref.getOrigin())) return false;
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		return newTags(ref.getQualifiedNonPublicTags(), maybeExisting.map(Ref::getQualifiedNonPublicTags)).allMatch(this::canAddTag);
	}

	public boolean canWriteRef(String url, String origin) {
		if (hasRole(MOD)) return true;
		if (!hasRole(USER)) return false;
		var maybeExisting = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeExisting.isEmpty()) return true;
		var existing = maybeExisting.get();
		if (existing.getTags() != null) {
			if (existing.getTags().contains("locked")) return false;
			var qualifiedTags = existing.getQualifiedNonPublicTags();
			return captures(getUserTag(), qualifiedTags) ||
				captures(getWriteAccess(), qualifiedTags);
		}
		return false;
	}

	public boolean canAddTag(String tag) {
		if (!tag.startsWith("_") && !tag.startsWith("+")) return true;
		if (hasRole(MOD)) return true;
		if (!hasRole(USER)) return false;
		if (tag.equals(getUserTag())) return true;
		return captures(getTagReadAccess(), List.of(tag));
	}

	public boolean canTagAll(List<String> tags, String url, String origin) {
		if (hasRole(MOD)) return true;
		for (var tag : tags) {
			if (!canTag(tag, url, origin)) return false;
		}
		return true;
	}

	public boolean canTag(String tag, String url, String origin) {
		if (hasRole(MOD)) return true;
		if (hasRole(EDITOR) &&
			isPublicTag(tag) &&
			!tag.equals("public") &&
			!tag.equals("locked") &&
			canReadRef(url, origin)) return true;
		return canAddTag(tag) && canWriteRef(url, origin);
	}

	public boolean isPublicTag(String tag) {
		if (tag.startsWith("_")) return false;
		if (tag.startsWith("+")) return false;
		return true;
	}

	public boolean canReadTag(String tag) {
		if (!tag.startsWith("_")) return true;
		return canAddTag(tag);
	}

	public boolean canWriteTag(String tag) {
		if (hasRole(MOD)) return true;
		if (isPublicTag(tag)) return hasRole(EDITOR);
		if (tag.equals(getUserTag()) && hasRole(VIEWER)) return true;
		if (!hasRole(USER)) return false;
		return captures(getTagWriteAccess(), List.of(tag));
	}

	public boolean canReadQuery(Query filter) {
		if (filter.getQuery() == null) return true;
		if (hasRole(MOD)) return true;
		var tagList = Arrays.stream(filter.getQuery().split("[!:|()]+"))
							.filter((t) -> t.startsWith("_"))
							.filter((t) -> !t.equals(getUserTag()))
							.toList();
		if (tagList.isEmpty()) return true;
		if (!hasRole(VIEWER)) return false;
		var tagReadAccess = getTagReadAccess();
		if (tagReadAccess == null) return false;
		return new HashSet<>(tagReadAccess).containsAll(tagList);
	}

	public boolean canWriteUser(User user) {
		if (hasRole(MOD)) return true;
		if (!local(user.getOrigin())) return false;
		if (!canWriteTag(user.getQualifiedTag())) return false;
		var maybeExisting = userRepository.findOneByQualifiedTag(user.getQualifiedTag());
		// No public tags in write access
		if (user.getWriteAccess() != null && user.getWriteAccess().stream().anyMatch(this::isPublicTag)) return false;
		// The writing user must already have write access to give read or write access to another user
		if (!newTags(user.getTagReadAccess(), maybeExisting.map(User::getTagReadAccess)).allMatch(this::canWriteTag)) return false;
		if (!newTags(user.getTagWriteAccess(), maybeExisting.map(User::getTagWriteAccess)).allMatch(this::canWriteTag)) return false;
		if (!newTags(user.getReadAccess(), maybeExisting.map(User::getReadAccess)).allMatch(this::canWrite)) return false;
		if (!newTags(user.getWriteAccess(), maybeExisting.map(User::getWriteAccess)).allMatch(this::canWrite)) return false;
		return true;
	}

	public List<String> filterTags(List<String> tags) {
		if (tags == null) return null;
		if (hasRole(MOD)) return tags;
		return tags.stream().filter(this::canReadTag).toList();
	}

	public List<String> hiddenTags(List<String> tags) {
		if (hasRole(MOD)) return null;
		if (tags == null) return null;
		return tags.stream().filter(tag -> !canReadTag(tag)).toList();
	}

	public Specification<Ref> refReadSpec() {
		if (hasRole(MOD)) return Specification.where(null);
		var spec = Specification.where(hasTag("public"));
		if (!hasRole(VIEWER)) return spec;
		return spec
			.or(hasTag(getUserTag()))
			.or(hasAnyTag(getReadAccess()));
	}

	public <T extends Tag> Specification<T> tagReadSpec() {
		if (hasRole(MOD)) return Specification.where(null);
		var spec = Specification.<T>where(publicTag());
		if (!hasRole(VIEWER)) return spec;
		return spec
			.or(isTag(getUserTag()))
			.or(isAnyTag(getReadAccess()));
	}

	private static boolean captures(List<String> selectors, List<String> target) {
		if (selectors == null) return false;
		if (selectors.isEmpty()) return false;
		if (target == null) return false;
		if (target.isEmpty()) return false;
		for (String selector : selectors) {
			if (captures(selector, target)) return true;
		}
		return false;
	}

	private static boolean captures(String selector, List<String> target) {
		if (selector == null) return false;
		if (target == null) return false;
		if (target.isEmpty()) return false;
		var s = new QualifiedTag(selector);
		for (String t : target) {
			if (s.captures(t)) return true;
		}
		return false;
	}

	private static Stream<String> newTags(List<String> changes, Optional<List<String>> existing) {
		if (changes == null) return Stream.empty();
		if (existing.isEmpty()) return changes.stream();
		return changes.stream().filter(tag -> !existing.get().contains(tag));
	}

	public String getUserTag() {
		if (userTag == null) {
			var authn = getAuthentication();
			if (authn instanceof AnonymousAuthenticationToken) return null;
			var principal = authn.getPrincipal();
			if (principal == null) return null;
			if (principal instanceof UserDetails) {
				principal = ((UserDetails) principal).getUsername();
			}
			if (hasRole(PRIVATE)) {
				userTag = "_user/" + principal;
			} else {
				userTag = "+user/" + principal;
			}
		}
		return userTag;
	}

	public Optional<User> getUser() {
		if (user == null) {
			user = userRepository.findOneByQualifiedTag(getUserTag() + getOrigin());
		}
		return user;
	}

	public String getOrigin() {
		if (origin == null) {
			if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attribs) {
				origin = attribs.getRequest().getHeader(ORIGIN_HEADER).toLowerCase();
			} else {
				origin = "";
			}
		}
		return origin;
	}

	public List<String> getReadAccess() {
		return getUser().map(User::getReadAccess).orElse(null);
	}

	public List<String> getWriteAccess() {
		return getUser().map(User::getWriteAccess).orElse(null);
	}

	public List<String> getTagReadAccess() {
		var readAccess = getReadAccess();
		var tagReadAccess = getUser().map(User::getTagReadAccess).orElse(null);
		if (readAccess != null && tagReadAccess != null) {
			return ListUtils.union(readAccess, tagReadAccess);
		}
		return readAccess == null ? tagReadAccess : readAccess;
	}

	public List<String> getTagWriteAccess() {
		var writeAccess = getWriteAccess();
		var tagWriteAccess = getUser().map(User::getTagWriteAccess).orElse(null);
		if (writeAccess != null && tagWriteAccess != null) {
			return ListUtils.union(writeAccess, tagWriteAccess);
		}
		return writeAccess == null ? tagWriteAccess : writeAccess;
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

	public AbstractAuthenticationToken getAuthentication() {
		return (AbstractAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
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
