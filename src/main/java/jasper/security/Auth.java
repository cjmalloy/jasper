package jasper.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import jasper.config.Props;
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
import org.apache.commons.lang3.StringUtils;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.QualifiedTag.originSelector;
import static jasper.repository.spec.QualifiedTag.qtList;
import static jasper.repository.spec.QualifiedTag.selector;
import static jasper.repository.spec.RefSpec.hasAnyQualifiedTag;
import static jasper.repository.spec.TagSpec.isAnyQualifiedTag;
import static jasper.repository.spec.TagSpec.notPrivateTag;
import static jasper.security.AuthoritiesConstants.ADMIN;
import static jasper.security.AuthoritiesConstants.EDITOR;
import static jasper.security.AuthoritiesConstants.MOD;
import static jasper.security.AuthoritiesConstants.PRIVATE;
import static jasper.security.AuthoritiesConstants.ROLE_PREFIX;
import static jasper.security.AuthoritiesConstants.SA;
import static jasper.security.AuthoritiesConstants.USER;
import static jasper.security.AuthoritiesConstants.VIEWER;

@Component
@RequestScope
public class Auth {
	private static final Logger logger = LoggerFactory.getLogger(Auth.class);

	public static final String LOCAL_ORIGIN_HEADER = "Local-Origin";
	public static final String WRITE_ACCESS_HEADER = "Write-Access";
	public static final String READ_ACCESS_HEADER = "Read-Access";
	public static final String TAG_WRITE_ACCESS_HEADER = "Tag-Write-Access";
	public static final String TAG_READ_ACCESS_HEADER = "Tag-Read-Access";

	@Autowired
	Props props;
	@Autowired
	RoleHierarchy roleHierarchy;
	@Autowired
	UserRepository userRepository;
	@Autowired
	RefRepository refRepository;

	// Cache
	protected Set<String> roles;
	protected Claims claims;
	protected QualifiedTag userTag;
	protected String origin;
	protected Optional<User> user;
	protected QualifiedTag publicTag;
	protected List<QualifiedTag> readAccess;
	protected List<QualifiedTag> writeAccess;
	protected List<QualifiedTag> tagReadAccess;
	protected List<QualifiedTag> tagWriteAccess;

	public boolean local(String origin) {
		return this.getOrigin().equals(origin);
	}

	public boolean freshLogin() {
		var iat = getClaims().getIssuedAt();
		if (iat != null && iat.toInstant().isBefore(Instant.now().minus(Duration.of(15, ChronoUnit.MINUTES)))) {
			return true;
		}
		throw new FreshLoginException();
	}

	public boolean isLoggedIn() {
		return hasRole(VIEWER);
	}

	public boolean canReadRef(HasTags ref) {
		if (hasRole(SA)) return true;
		if (hasRole(MOD) && originSelector(getMultiTenantOrigin()).captures(originSelector(ref.getOrigin()))) return true;
		if (ref.getTags() == null) return false;
		var qualifiedTags = qtList(ref.getOrigin(), ref.getTags());
		if (captures(getPublicTag(), qualifiedTags)) return true;
		if (!isLoggedIn()) return false;
		return captures(getUserTag(), qualifiedTags) ||
			captures(getReadAccess(), qualifiedTags);
	}

	protected boolean canReadRef(String url, String origin) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeExisting.isEmpty()) return false;
		return canReadRef(maybeExisting.get());
	}

	public boolean canWriteRef(Ref ref) {
		if (!canWriteRef(ref.getUrl(), ref.getOrigin())) return false;
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		return newTags(ref.getTags(), maybeExisting.map(Ref::getTags)).allMatch(this::canAddTag);
	}

	public boolean canWriteRef(String url, String origin) {
		if (!local(origin)) return false;
		if (hasRole(MOD)) return true;
		if (!hasRole(USER)) return false;
		var maybeExisting = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeExisting.isEmpty()) return true;
		var existing = maybeExisting.get();
		if (existing.getTags() != null) {
			if (existing.getTags().contains("locked")) return false;
			var qualifiedTags = qtList(origin, existing.getTags());
			return captures(getUserTag(), qualifiedTags) ||
				captures(getWriteAccess(), qualifiedTags);
		}
		return false;
	}

	protected boolean canAddTag(String tag) {
		if (hasRole(MOD)) return true;
		if (!hasRole(USER)) return false;
		if (isPublicTag(tag)) return true;
		if (!isLoggedIn()) return false;
		var qt = selector(tag + getOrigin());
		if (getUserTag().captures(qt)) return true;
		return captures(getTagReadAccess(), List.of(qt));
	}

	public boolean canTagAll(List<String> tags, String url, String origin) {
		if (!local(origin)) return false;
		if (hasRole(MOD)) return true;
		if (!hasRole(USER)) return false;
		for (var tag : tags) {
			if (!canTag(tag, url, origin)) return false;
		}
		return true;
	}

	public boolean canTag(String tag, String url, String origin) {
		if (!local(origin)) return false;
		if (hasRole(MOD)) return true;
		if (hasRole(EDITOR) &&
			isPublicTag(tag) &&
			!tag.equals("public") &&
			!tag.equals("locked") &&
			canReadRef(url, origin)) return true;
		return canAddTag(tag) && canWriteRef(url, origin);
	}

	public static boolean isPublicTag(String tag) {
		if (isPrivateTag(tag)) return false;
		if (isProtectedTag(tag)) return false;
		return true;
	}

	public static boolean isPrivateTag(String tag) {
		return tag.startsWith("_");
	}

	public static boolean isProtectedTag(String tag) {
		return tag.startsWith("+");
	}

	public boolean canReadTag(String qualifiedTag) {
		if (hasRole(SA)) return true;
		var qt = selector(qualifiedTag);
		if ((local(qt.origin) || !props.isMultiTenant()) && !isPrivateTag(qualifiedTag)) return true;
		if (hasRole(MOD) && originSelector(getMultiTenantOrigin()).captures(qt)) return true;
		if (isLoggedIn() && getUserTag().captures(qt)) return true;
		return captures(getTagReadAccess(), List.of(qt));
	}

	public boolean canWriteTag(String qualifiedTag) {
		var qt = selector(qualifiedTag);
		if (!local(qt.origin)) return false;
		if (hasRole(MOD)) return true;
		if (hasRole(EDITOR) && isPublicTag(qualifiedTag)) return true;
		if (isLoggedIn() && getUserTag().captures(qt)) return true;
		if (!hasRole(USER)) return false;
		return captures(getTagWriteAccess(), List.of(qt));
	}

	public boolean isUser(String qualifiedTag) {
		return isLoggedIn() && getUserTag().captures(selector(qualifiedTag));
	}

	public boolean canReadQuery(Query filter) {
		if (filter.getQuery() == null) return true;
		if (hasRole(MOD)) return true;
		var tagList = Arrays.stream(filter.getQuery().split("[!:|()\\s]+"))
			.filter(StringUtils::isNotBlank)
			.filter(Auth::isPrivateTag)
			.map(QualifiedTag::selector)
			.filter(qt -> !isLoggedIn() || !getUserTag().captures(qt))
			.toList();
		if (tagList.isEmpty()) return true;
		if (!isLoggedIn()) return false;
		var tagReadAccess = getTagReadAccess();
		if (tagReadAccess == null) return false;
		return captures(tagReadAccess, tagList);
	}

	public boolean isSysAdmin() {
		if (props.isMultiTenant()) return hasRole(SA);
		return hasRole(ADMIN);
	}

	public boolean isSysMod() {
		if (props.isMultiTenant()) return hasRole(SA);
		return hasRole(MOD);
	}

	public boolean canWriteUser(User user) {
		if (hasRole(SA)) return true;
		if (!local(user.getOrigin())) return false;
		if (hasRole(MOD)) return true;
		if (!canWriteTag(user.getQualifiedTag())) return false;
		var maybeExisting = userRepository.findOneByQualifiedTag(user.getQualifiedTag());
		// No public tags in write access
		if (user.getWriteAccess() != null && user.getWriteAccess().stream().anyMatch(Auth::isPublicTag)) return false;
		// The writing user must already have write access to give read or write access to another user
		if (!newTags(user.getTagReadAccess(), maybeExisting.map(User::getTagReadAccess)).allMatch(this::tagWriteAccessCaptures)) return false;
		if (!newTags(user.getTagWriteAccess(), maybeExisting.map(User::getTagWriteAccess)).allMatch(this::tagWriteAccessCaptures)) return false;
		if (!newTags(user.getReadAccess(), maybeExisting.map(User::getReadAccess)).allMatch(this::writeAccessCaptures)) return false;
		if (!newTags(user.getWriteAccess(), maybeExisting.map(User::getWriteAccess)).allMatch(this::writeAccessCaptures)) return false;
		return true;
	}

	public List<String> filterTags(List<String> tags) {
		if (tags == null) return null;
		if (hasRole(MOD)) return tags;
		return tags.stream()
			.filter(tag -> canReadTag(tag + getOrigin()))
			.toList();
	}

	public List<String> hiddenTags(List<String> tags) {
		if (hasRole(MOD)) return null;
		if (tags == null) return null;
		return tags.stream()
			.filter(tag -> !canReadTag(tag + getOrigin()))
			.toList();
	}

	public Specification<Ref> refReadSpec() {
		if (hasRole(SA)) return Specification.where(null);
		var spec = Specification.<Ref>where(isOrigin(getMultiTenantOrigin()));
		if (hasRole(MOD)) return spec;
		spec = spec.and(getPublicTag().refSpec());
		if (!isLoggedIn()) return spec;
		return spec
			.or(getUserTag().refSpec())
			.or(hasAnyQualifiedTag(getReadAccess()));
	}

	public <T extends Tag> Specification<T> tagReadSpec() {
		if (hasRole(SA)) return Specification.where(null);
		var spec = Specification.<T>where(isOrigin(getMultiTenantOrigin()));
		if (hasRole(MOD)) return spec;
		spec = spec.and(notPrivateTag());
		if (!isLoggedIn()) return spec;
		return spec
			.or(getUserTag().spec())
			.or(isAnyQualifiedTag(getTagReadAccess()));
	}

	protected boolean tagWriteAccessCaptures(String tag) {
		if (hasRole(MOD)) return true;
		var qt = selector(tag + getOrigin());
		if (isLoggedIn() && getUserTag().captures(qt)) return true;
		if (!hasRole(USER)) return false;
		return captures(getTagWriteAccess(), List.of(qt));
	}

	protected boolean writeAccessCaptures(String tag) {
		if (hasRole(MOD)) return true;
		if (!hasRole(USER)) return false;
		var qt = selector(tag + getOrigin());
		if (getUserTag().captures(qt)) return true;
		return captures(getWriteAccess(), List.of(qt));
	}

	protected static boolean captures(List<QualifiedTag> selectors, List<QualifiedTag> target) {
		if (selectors == null) return false;
		if (selectors.isEmpty()) return false;
		if (target == null) return false;
		if (target.isEmpty()) return false;
		for (var selector : selectors) {
			if (captures(selector, target)) return true;
		}
		return false;
	}

	protected static boolean captures(QualifiedTag selector, List<QualifiedTag> target) {
		if (selector == null) return false;
		if (target == null) return false;
		if (target.isEmpty()) return false;
		for (var t : target) {
			if (selector.captures(t)) return true;
		}
		return false;
	}

	protected static Stream<String> newTags(List<String> changes, Optional<List<String>> existing) {
		if (changes == null) return Stream.empty();
		if (existing.isEmpty()) return changes.stream();
		return changes.stream().filter(tag -> !existing.get().contains(tag));
	}

	public QualifiedTag getUserTag() {
		if (userTag == null) {
			String principal;
			var authn = getAuthentication();
			if (authn instanceof JwtAuthentication j) {
				principal = j.getPrincipal();
			} else {
				if (authn instanceof AnonymousAuthenticationToken) return null;
				if (authn.getPrincipal() == null) return null;
				if (authn.getPrincipal() instanceof String username) {
					principal = username;
				} else if (authn.getPrincipal() instanceof UserDetails d) {
					principal = d.getUsername();
				} else {
					return null;
				}
			}
			if (hasRole(PRIVATE)) {
				userTag = selector("_user/" + principal + getOrigin());
			} else {
				userTag = selector("+user/" + principal + getOrigin());
			}
		}
		return userTag;
	}

	public Optional<User> getUser() {
		if (user == null) {
			user = userRepository.findOneByQualifiedTag(getUserTag().toString());
		}
		return user;
	}

	public String getOrigin() {
		if (origin == null) {
			if (props.isAllowLocalOriginHeader() && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attribs) {
				origin = attribs.getRequest().getHeader(LOCAL_ORIGIN_HEADER).toLowerCase();
			} else {
				origin = props.getLocalOrigin();
			}
		}
		return origin;
	}

	public QualifiedTag getPublicTag() {
		if (publicTag == null) {
			return selector("public" + getMultiTenantOrigin());
		}
		return publicTag;
	}

	protected String getMultiTenantOrigin() {
		return props.isMultiTenant() ? getOrigin() : "@*";
	}

	public List<QualifiedTag> getReadAccess() {
		if (readAccess == null) {
			readAccess = new ArrayList<>();
			if (props.getDefaultReadAccess() != null) {
				readAccess.addAll(getQualifiedTags(props.getDefaultReadAccess()));
			}
			if (props.isAllowAuthHeaders()) {
				readAccess.addAll(getHeaderQualifiedTags(READ_ACCESS_HEADER));
			}
			readAccess.addAll(getClaimQualifiedTags(props.getReadAccessClaim()));
			readAccess.addAll(qtList(getMultiTenantOrigin(), getUser()
					.map(User::getReadAccess)
					.orElse(List.of())));
		}
		return readAccess;
	}

	public List<QualifiedTag> getWriteAccess() {
		if (writeAccess == null) {
			writeAccess = new ArrayList<>();
			if (props.getDefaultWriteAccess() != null) {
				writeAccess.addAll(getQualifiedTags(props.getDefaultWriteAccess()));
			}
			if (props.isAllowAuthHeaders()) {
				writeAccess.addAll(getHeaderQualifiedTags(WRITE_ACCESS_HEADER));
			}
			writeAccess.addAll(getClaimQualifiedTags(props.getWriteAccessClaim()));
			writeAccess.addAll(qtList(getMultiTenantOrigin(), getUser()
				.map(User::getWriteAccess)
					.orElse(List.of())));
		}
		return writeAccess;
	}

	public List<QualifiedTag> getTagReadAccess() {
		if (tagReadAccess == null) {
			tagReadAccess = new ArrayList<>(getReadAccess());
			if (props.getDefaultTagReadAccess() != null) {
				tagReadAccess.addAll(getQualifiedTags(props.getDefaultTagReadAccess()));
			}
			if (props.isAllowAuthHeaders()) {
				tagReadAccess.addAll(getHeaderQualifiedTags(TAG_READ_ACCESS_HEADER));
			}
			tagReadAccess.addAll(getClaimQualifiedTags(props.getTagReadAccessClaim()));
			tagReadAccess.addAll(qtList(getMultiTenantOrigin(), getUser()
				.map(User::getTagReadAccess)
				.orElse(List.of())));
		}
		return tagReadAccess;
	}

	public List<QualifiedTag> getTagWriteAccess() {
		if (tagWriteAccess == null) {
			tagWriteAccess = new ArrayList<>(getWriteAccess());
			if (props.getDefaultTagWriteAccess() != null) {
				tagWriteAccess.addAll(getQualifiedTags(props.getDefaultTagWriteAccess()));
			}
			if (props.isAllowAuthHeaders()) {
				tagWriteAccess.addAll(getHeaderQualifiedTags(TAG_WRITE_ACCESS_HEADER));
			}
			tagWriteAccess.addAll(getClaimQualifiedTags(props.getTagWriteAccessClaim()));
			tagWriteAccess.addAll(qtList(getMultiTenantOrigin(), getUser()
				.map(User::getTagWriteAccess)
				.orElse(List.of())));
		}
		return tagWriteAccess;
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

	public Claims getClaims() {
		if (claims == null) {
			var auth = getAuthentication();
			if (auth instanceof JwtAuthentication j) {
				claims = j.getDetails();
			} else {
				claims = new DefaultClaims();
			}
		}
		return claims;
	}

	public Set<String> getAuthoritySet() {
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

	private static List<String> getHeaderTags(String headerName) {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
			return List.of(a.getRequest().getHeader(headerName).split(","));
		}
		return List.of();
	}

	private static List<QualifiedTag> getHeaderQualifiedTags(String headerName) {
		return getHeaderTags(headerName).stream().map(QualifiedTag::selector).toList();
	}

	public List<String> getClaimTags(String claim) {
		if (!getClaims().containsKey(claim)) return List.of();
		return List.of(getClaims().get(claim, String.class).split(","));
	}

	public List<QualifiedTag> getClaimQualifiedTags(String claim) {
		return getClaimTags(claim).stream().map(QualifiedTag::selector).toList();
	}

	public static List<QualifiedTag> getQualifiedTags(String[] tags) {
		return Stream.of(tags).map(QualifiedTag::selector).toList();
	}

}
