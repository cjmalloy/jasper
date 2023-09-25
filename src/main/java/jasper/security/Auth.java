package jasper.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import jasper.config.Props;
import jasper.config.Props.Security.Client;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.QualifiedTag.originSelector;
import static jasper.repository.spec.QualifiedTag.qt;
import static jasper.repository.spec.QualifiedTag.qtList;
import static jasper.repository.spec.QualifiedTag.selector;
import static jasper.repository.spec.QualifiedTag.selectors;
import static jasper.repository.spec.RefSpec.hasAnyQualifiedTag;
import static jasper.repository.spec.TagSpec.isAnyQualifiedTag;
import static jasper.repository.spec.TagSpec.notPrivateTag;
import static jasper.security.AuthoritiesConstants.ADMIN;
import static jasper.security.AuthoritiesConstants.EDITOR;
import static jasper.security.AuthoritiesConstants.MOD;
import static jasper.security.AuthoritiesConstants.ROLE_PREFIX;
import static jasper.security.AuthoritiesConstants.SA;
import static jasper.security.AuthoritiesConstants.USER;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.jpa.domain.Specification.where;

/**
 * This single class is where all authorization decisions are made.
 * Authorization decisions are made based on six criteria:
 * 1. The user tag
 * 2. The local origin (always the same as the user tag origin)
 * 3. The user role (SYSADMIN, ADMIN, MOD, EDITOR, USER, VIEWER, ANONYMOUS)
 * 4. The user access tags
 * 5. Is multi-tenant enabled?
 * 6. Is the JWT token fresh? (less than 15 minutes old)
 *
 * These criteria may be sourced in three cascading steps:
 * 1. Application properties (set by command line, environment variables, or default value)
 * 2. JWT token claims
 * 3. Request headers
 *
 * The local origin is set by headers with the highest precedence, then JWT and
 * finally application properties.
 * Roles and access tags merge to be the more elevated role.
 *
 * The application properties that can be configured are:
 * 1. localOrigin (""): set the local origin
 * 2. multiTenant (false): enable or disable multi-tenant
 * 3. defaultRole ("ROLE_ANONYMOUS"): set the default role
 * 4. defaultReadAccess ([]): set the default read access tags
 * 5. defaultWriteAccess ([]): set the default write access tags
 * 6. defaultTagReadAccess ([]): set the default tag read access tags
 * 7. defaultTagWriteAccess ([]): set the default tag write access tags
 * 8. usernameClaim ("sub"): the JWT claim to use as a username
 * 9. allowUsernameClaimOrigin (false): allow the JWT username claim to set the local origin
 * 10. authoritiesClaim ("auth"): the JWT claim to use as a role
 * 11. readAccessClaim ("readAccess"): the JWT claim to use as read access tags
 * 12. readAccessClaim ("writeAccess"): the JWT claim to use as write access tags
 * 13. tagReadAccessClaim ("tagReadAccess"): the JWT claim to use as tag read access tags
 * 14. tagWriteAccessClaim ("tagWriteAccess"): the JWT claim to use as tag write access tags
 * 15. allowLocalOriginHeader (false): enable setting the local origin in the header
 * 16. allowAuthHeaders (false): enable setting the user access tags in the header
 *
 * The following headers are checked if enabled:
 * 1. User-Tag
 * 2. Local-Origin
 * 3. User-Role
 * 4. Write-Access
 * 5. Read-Access
 * 6. Tag-Write-Access
 * 7. Tag-Read-Access
 *
 * If no username is not set and the role is at least MOD it will default to +user.
 */
@Component
@RequestScope
public class Auth {
	private static final Logger logger = LoggerFactory.getLogger(Auth.class);

	public static final String USER_TAG_HEADER = "User-Tag";
	public static final String USER_ROLE_HEADER = "User-Role";
	public static final String LOCAL_ORIGIN_HEADER = "Local-Origin";
	public static final String WRITE_ACCESS_HEADER = "Write-Access";
	public static final String READ_ACCESS_HEADER = "Read-Access";
	public static final String TAG_WRITE_ACCESS_HEADER = "Tag-Write-Access";
	public static final String TAG_READ_ACCESS_HEADER = "Tag-Read-Access";

	Props props;
	RoleHierarchy roleHierarchy;
	UserRepository userRepository;
	RefRepository refRepository;

	// Cache
	protected Authentication authentication;
	protected Set<String> roles;
	protected Claims claims;
	protected String principal;
	protected QualifiedTag userTag;
	protected String origin;
	protected Client client;
	protected Optional<User> user;
	protected List<QualifiedTag> publicTags;
	protected List<QualifiedTag> readAccess;
	protected List<QualifiedTag> writeAccess;
	protected List<QualifiedTag> tagReadAccess;
	protected List<QualifiedTag> tagWriteAccess;

	public Auth(Props props, RoleHierarchy roleHierarchy, UserRepository userRepository, RefRepository refRepository) {
		this.props = props;
		this.roleHierarchy = roleHierarchy;
		this.userRepository = userRepository;
		this.refRepository = refRepository;
	}

	public void clear(Authentication authentication) {
		this.authentication = authentication;
		roles = null;
		claims = null;
		principal = null;
		userTag = null;
		client = null;
		user = null;
		publicTags = null;
		readAccess = null;
		writeAccess = null;
		tagReadAccess = null;
		tagWriteAccess = null;
		if (getPrincipal().startsWith("@")) {
			origin = getPrincipal();
		} else {
			origin = qt(getPrincipal()).origin;
		}
	}

	@PostConstruct
	public void log() {
		logger.debug("AUTH{} User: {} {} (hasUser: {})",
			getOrigin(), getPrincipal(), getAuthoritySet(), getUser().isPresent());
	}

	/**
	 * Is this origin local. Nulls and empty strings are both considered to
	 * be the default origin.
	 */
	public boolean local(String origin) {
		if (!origin.startsWith("@")) origin = qt(origin).origin;
		return getOrigin().equals(origin);
	}

	/**
	 * Is this origin a sub-origin.
	 */
	public boolean subOrigin(String origin) {
		if (isBlank(origin)) return false;
		if (isBlank(getOrigin())) return true;
		return origin.startsWith(getOrigin()+".");
	}

	/**
	 * Is this origin readable.
	 * In single tenant mode all origins are readable.
	 * In multi tenant mode only local tenants or whitelisted tenants are readable.
	 */
	public boolean tenantAccess(String origin) {
		if (!props.isMultiTenant()) return true;
		if (local(origin)) return true;
		if (getClient().getTenantAccess() == null) return false;
		for (var t : getClient().getTenantAccess()) {
			if (t.equals(origin)) return true;
		}
		return false;
	}

	/**
	 * Has the user logged in within 15 minutes?
	 */
	public boolean freshLogin() {
		var iat = getClaims().getIssuedAt();
		if (iat != null && iat.toInstant().isAfter(Instant.now().minus(Duration.of(15, ChronoUnit.MINUTES)))) {
			return true;
		}
		throw new FreshLoginException();
	}

	/**
	 * Is the current user logged in? Only if they have a user tag which is not blank.
	 * Otherwise, an anonymous request is being made and no user tag should be expected.
	 * Mods, Admins and SysAdmins cannot make anonymous requests, as they will be
	 * a default user tag +user.
	 */
	public boolean isLoggedIn() {
		return isNotBlank(getPrincipal()) && !getPrincipal().startsWith("@");
	}

	/**
	 * Can the user read this Ref?
	 * Only considers the Ref given, does not check if it is modified from
	 * the database version.
	 */
	public boolean canReadRef(HasTags ref) {
		// Sysadmin can always read anything
		if (hasRole(SA)) return true;
		// Mods can read anything in their local origin if multi tenant
		// In single tenant mods and above can read anything
		if (hasRole(MOD) && originSelector(getMultiTenantOrigin()).captures(originSelector(ref.getOrigin()))) return true;
		// Min Role
		if (!minRole()) return false;
		// No tags, only mods can read
		if (ref.getTags() == null) return false;
		// Add the ref's origin to its tag list
		var qualifiedTags = qtList(ref.getOrigin(), ref.getTags());
		// Check if owner
		if (owns(qualifiedTags)) return true;
		// Check if user read access tags capture anything in the ref tags
		return captures(getReadAccess(), qualifiedTags);
	}

	/**
	 * Can the user read a ref by tags.
	 */
	public boolean canReadRef(String url, String origin) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(url, origin);
		return maybeExisting.filter(this::canReadRef).isPresent();
	}

	/**
	 * Can the user update an existing Ref with given updated version?
	 * Checks the existing database version for write access and verifies any
	 * tag additions.
	 * @param ref the updated ref
	 */
	public boolean canWriteRef(Ref ref) {
		// First check if we can write to the existing Ref
		if (!canWriteRef(ref.getUrl(), ref.getOrigin())) return false;
		// If we can write to the existing we are granted permission
		// We do not need to check if we have write access to the updated Ref,
		// as self revocation is allowed
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		// We do need to check if we are allowed to add any of the new tags
		// by calling canAddTag on each one
		return newTags(ref.getTags(), maybeExisting.map(Ref::getTags)).allMatch(this::canAddTag);
	}

	/**
	 * Can the user write to an existing Ref.
	 */
	public boolean canWriteRef(String url, String origin) {
		// Only writing to the local origin ever permitted
		if (!local(origin)) return false;
		// Minimum role for writing Refs is USER
		if (!hasRole(USER)) return false;
		// Min Role
		if (!minRole()) return false;
		var maybeExisting = refRepository.findOneByUrlAndOrigin(url, origin);
		// If we're creating, simply having the role USER is enough
		if (maybeExisting.isEmpty()) return true;
		var existing = maybeExisting.get();
		if (existing.getTags() != null) {
			// First write check of an existing Ref must be for the locked tag
			if (existing.getTags().contains("locked")) return false;
			// Mods can write anything in their origin
			if (hasRole(MOD)) return true;
			var qualifiedTags = qtList(origin, existing.getTags());
			// Check if owner
			if (owns(qualifiedTags)) return true;
			// Check access tags
			return captures(getWriteAccess(), qualifiedTags);
		}
		return false;
	}

	/**
	 * Can subscribe to STOMP topic.
	 */
	public boolean canSubscribeTo(String destination) {
		// Min Role
		if (!minRole()) return false;
		if (destination == null) return false;
		if (destination.startsWith("/topic/tag/")) {
			var tag = destination.substring("/topic/tag/".length()).replace('>', '_').replace('<', '+');
            return canReadTag(tag);
		} else if (destination.startsWith("/topic/ref/")) {
			var ref = destination.substring("/topic/ref/".length());
			var origin = ref.substring(0, ref.indexOf('/'));
			if (origin.equals("default")) origin = "";
			var url = ref.substring(ref.indexOf('/') + 1);
			return canReadRef(url, origin);
		} else if (destination.startsWith("/topic/response/")) {
			return true;
		}
		return false;
	}

	/**
	 * Does the user have permission to use a tag when tagging Refs?
	 */
	public boolean canAddTag(String tag) {
		// Min Role
		if (!minRole()) return false;
		if (hasRole(MOD)) return true;
		if (!hasRole(USER)) return false;
		if (isPublicTag(tag)) return true;
		var qt = qt(tag + getOrigin());
		if (isUser(qt)) return true;
		return captures(getTagReadAccess(), qt);
	}

	/**
	 * Does the user have permission to use all tags when tagging Refs?
	 */
	public boolean canAddTags(List<String> tags) {
		// Min Role
		if (!minRole()) return false;
		if (hasRole(MOD)) return true;
		if (!hasRole(USER)) return false;
		return tags.stream().allMatch(this::canAddTag);
	}

	/**
	 * Can the user add these tags to an existing ref?
	 */
	public boolean canTagAll(List<String> tags, String url, String origin) {
		// Only writing to the local origin ever permitted
		if (!local(origin)) return false;
		// Min Role
		if (!minRole()) return false;
		if (hasRole(MOD)) return true;
		if (!hasRole(USER)) return false;
		for (var tag : tags) {
			if (!canTag(tag, url, origin)) return false;
		}
		return true;
	}

	public List<String> tagPatch(List<String> patch) {
		return patch.stream().map(p -> p.startsWith("-") ? p.substring(1) : p).toList();
	}

	/**
	 * Can the user add this tag to an existing ref?
	 */
	public boolean canTag(String tag, String url, String origin) {
		// Only writing to the local origin ever permitted
		if (!local(origin)) return false;
		// Min Role
		if (!minRole()) return false;
		if (hasRole(MOD)) return true;
		// Editor has special access to add public tags to Refs they can read
		if (hasRole(EDITOR) &&
			isPublicTag(tag) &&
			// Except for public, an Editor cannot make a private Ref public or vice-versa
			!tag.equals("public") &&
			// Except for locked, an Editor cannot make a locked Ref editable or vice-versa
			!tag.equals("locked") &&
			canReadRef(url, origin)) return true;
		// You can add the tag, and you can edit the ref
		return canAddTag(tag) && canWriteRef(url, origin);
	}

	/**
	 * Is this a public tag?
	 * Public tags start with a letter or number.
	 */
	public static boolean isPublicTag(String tag) {
		if (isPrivateTag(tag)) return false;
		if (isProtectedTag(tag)) return false;
		return true;
	}

	/**
	 * Is this a private tag?
	 * Private tags start with a _.
	 */
	public static boolean isPrivateTag(String tag) {
		return tag.startsWith("_");
	}

	/**
	 * Is this a protected tag?
	 * Protected tags start with a +.
	 */
	public static boolean isProtectedTag(String tag) {
		return tag.startsWith("+");
	}

	/**
	 * Can the user read the given qualified tag and any associated entities? (Ext, User, Plugin, Template)
	 * A selector is a tag that may contain an origin, or an origin with no tag.
	 * In multi-tenant mode you have no read access outside your origin by default.
	 */
	public boolean canReadTag(String qualifiedTag) {
		if (hasRole(SA)) return true;
		// Min Role
		if (!minRole()) return false;
		var qt = qt(qualifiedTag);
		// In single tenant mode, non private tags are all readable
		// In multi tenant mode, local non private tags are all readable
		if (!isPrivateTag(qualifiedTag) && tenantAccess(qt.origin)) return true;
		// In single tenant mode, mods can read anything
		// In multi tenant mode, mods can ready anything in their origin
		if (hasRole(MOD) && originSelector(getMultiTenantOrigin()).captures(qt)) return true;
		// Can read own user tag
		if (isUser(qualifiedTag)) return true;
		// Finally check access tags
		return captures(getTagReadAccess(), qt);
	}

	/**
	 * Can the user modify the associated Ext and User entities of a tag?
	 */
	public boolean canWriteTag(String qualifiedTag) {
		var qt = qt(qualifiedTag);
		// Only writing to the local origin ever permitted
		if (!local(qt.origin)) return false;
		// Min Role
		if (!minRole()) return false;
		// Mods can write anything in their origin
		if (hasRole(MOD)) return true;
		// Editors have special access to edit public tag Exts
		if (hasRole(EDITOR) && isPublicTag(qualifiedTag)) return true;
		// Viewers may only edit their user ext
		if (isUser(qt)) return true;
		// User is required to edit anything other than your own user
		if (!hasRole(USER)) return false;
		// Check access tags
		return captures(getTagWriteAccess(), qt);
	}

	/**
	 * Does the user's tag match this tag?
	 */
	public boolean isUser(QualifiedTag qt) {
		return isLoggedIn() && getUserTag().matches(qt);
	}

	public boolean isUser(String qualifiedTag) {
		return isUser(qt(qualifiedTag));
	}

	public boolean owns(List<QualifiedTag> qt) {
		return qt.stream().anyMatch(this::isUser);
	}

	/**
	 * Check all the individual selectors of the query and verify they can all
	 * be read.
	 */
	public boolean canReadQuery(Query filter) {
		// Min Role
		if (!minRole()) return false;
		// Anyone can read the empty query (retrieve all Refs)
		if (filter.getQuery() == null) return true;
		// Mod
		if (hasRole(MOD)) return true;
		var tagList = Arrays.stream(filter.getQuery().split("[!:|()\\s]+"))
			.filter(StringUtils::isNotBlank)
			.filter(Auth::isPrivateTag)
			.filter(qt -> !isUser(qt))
			.map(QualifiedTag::selector)
			.toList();
		if (tagList.isEmpty()) return true;
		return captures(getTagReadAccess(), tagList);
	}

	/**
	 * Has the minimum role.
	 */
	public boolean minRole() {
		return hasAnyRole(props.getMinRole());
	}

	/**
	 * Is the user Sysadmin role in multi tenant, Admin in single tenant.
	 */
	public boolean sysAdmin() {
		if (props.isMultiTenant()) return hasRole(SA);
		return hasRole(ADMIN);
	}

	/**
	 * Is the user Sysadmin role in multi tenant, Mod in single tenant.
	 */
	public boolean sysMod() {
		if (props.isMultiTenant()) return hasRole(SA);
		return hasRole(MOD);
	}

	/**
	 * Can this user be updated?
	 * Check if the user can write this tag, and that their role is not smaller.
	 */
	public boolean canWriteUser(String tag) {
		// Only writing to the local origin ever permitted
		if (!local(qt(tag).origin)) return false;
		if (hasRole(MOD)) return true;
		if (!canWriteTag(tag)) return false;
		var role = userRepository.findOneByQualifiedTag(tag).map(User::getRole).orElse(null);
		return isBlank(role) || hasRole(role);
	}

	/**
	 * Can this user be updated with the given user?
	 * Check that the user is writeable, and that the user has write access to all new tags.
	 * Do not allow public tags to be given write access.
	 */
	public boolean canWriteUser(User user) {
		if (!canWriteUser(user.getQualifiedTag())) return false;
		if (isNotBlank(user.getRole()) && !hasRole(user.getRole())) return false;
		if (hasRole(MOD)) return true;
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
		if (sysMod()) return where(null);
		var spec = where(hasRole(MOD) ? isOrigin(getMultiTenantOrigin()) : qt("public" + getOrigin()).refSpec());
		if (isLoggedIn()) {
			spec = spec.or(getUserTag().refSpec());
		}
		return spec.or(hasAnyQualifiedTag(getReadAccess()));
	}

	public <T extends Tag> Specification<T> tagReadSpec() {
		if (sysMod()) return where(null);
		var spec = Specification.<T>where(isOrigin(getMultiTenantOrigin()));
		if (!hasRole(MOD)) spec = spec.and(notPrivateTag());
		if (isLoggedIn()) {
			spec = spec.or(getUserTag().spec());
		}
		return spec.or(isAnyQualifiedTag(getTagReadAccess()));
	}

	protected boolean tagWriteAccessCaptures(String tag) {
		if (hasRole(MOD)) return true;
		var qt = qt(tag + getOrigin());
		if (isUser(qt)) return true; // Viewers may only edit their user ext
		if (!hasRole(USER)) return false;
		return captures(getTagWriteAccess(), qt);
	}

	protected boolean writeAccessCaptures(String tag) {
		if (hasRole(MOD)) return true;
		if (!hasRole(USER)) return false;
		var qt = qt(tag + getOrigin());
		if (isUser(qt)) return true;
		return captures(getWriteAccess(), qt);
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

	protected static boolean captures(List<QualifiedTag> selectors, QualifiedTag target) {
		if (selectors == null) return false;
		if (selectors.isEmpty()) return false;
		if (target == null) return false;
		for (var selector : selectors) {
			if (selector.captures(target)) return true;
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

	public String getPrincipal() {
		if (principal == null) {
			var authn = getAuthentication();
			if (authn == null) return null;
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
		}
		return principal;
	}

	public QualifiedTag getUserTag() {
		if (userTag == null) {
			if (!isLoggedIn()) return null;
			userTag = qt(getPrincipal());
		}
		return userTag;
	}

	protected Optional<User> getUser() {
		if (user == null) {
			var auth = Optional.ofNullable(getAuthentication());
			user = auth.map(a -> (User) a.getDetails());
			if (isLoggedIn() && user.isEmpty()) {
				user = userRepository.findOneByQualifiedTag(getUserTag().toString());
			}
		}
		return user;
	}

	public String getOrigin() {
		if (origin == null) {
			origin = props.getLocalOrigin();
			if (props.isAllowLocalOriginHeader() && getOriginHeader() != null) {
				origin = getOriginHeader().toLowerCase();
			} else if (isLoggedIn() &&
				props.getSecurity().hasClient(origin) &&
				props.getSecurity().getClient(origin).isAllowUsernameClaimOrigin() &&
				getPrincipal().contains("@")) {
				try {
					origin = qt(getPrincipal()).origin;
				} catch (UnsupportedOperationException ignored) {}
			}
		}
		return origin;
	}

	public Client getClient() {
		if (client == null) {
			client = props.getSecurity().getClient(getOrigin());
			if (client == null) {
				client = props.getSecurity().getClient(props.getLocalOrigin());
			}
		}
		return client;
	}

	public static String getOriginHeader() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attribs) {
			logger.trace("{}: {}", LOCAL_ORIGIN_HEADER, attribs.getRequest().getHeader(LOCAL_ORIGIN_HEADER));
			return attribs.getRequest().getHeader(LOCAL_ORIGIN_HEADER);
		}
		return null;
	}

	public List<QualifiedTag> getPublicTags() {
		if (publicTags == null) {
			if (props.isMultiTenant()) {
				publicTags = new ArrayList<>(List.of(qt("public" + getOrigin())));
				if (getClient().getTenantAccess() == null) return publicTags;
				for (var t : getClient().getTenantAccess()) {
					publicTags.add(qt("public" + t));
				}
			} else {
				publicTags = List.of(selector("public"));
			}
		}
		return publicTags;
	}

	protected String getMultiTenantOrigin() {
		return props.isMultiTenant() ? getOrigin() : "@*";
	}

	public List<QualifiedTag> getReadAccess() {
		if (readAccess == null) {
			readAccess = new ArrayList<>(getPublicTags());
			if (getClient().getDefaultReadAccess() != null) {
				readAccess.addAll(getQualifiedTags(getClient().getDefaultReadAccess()));
			}
			if (getClient().isAllowAuthHeaders()) {
				readAccess.addAll(getHeaderQualifiedTags(READ_ACCESS_HEADER));
			}
			readAccess.addAll(getClaimQualifiedTags(getClient().getReadAccessClaim()));
			if (isLoggedIn()) {
				readAccess.addAll(selectors(getMultiTenantOrigin(), getUser()
						.map(User::getReadAccess)
						.orElse(List.of())));
			}
		}
		return readAccess;
	}

	public List<QualifiedTag> getWriteAccess() {
		if (writeAccess == null) {
			writeAccess = new ArrayList<>();
			if (getClient().getDefaultWriteAccess() != null) {
				writeAccess.addAll(getQualifiedTags(getClient().getDefaultWriteAccess()));
			}
			if (getClient().isAllowAuthHeaders()) {
				writeAccess.addAll(getHeaderQualifiedTags(WRITE_ACCESS_HEADER));
			}
			writeAccess.addAll(getClaimQualifiedTags(getClient().getWriteAccessClaim()));
			if (isLoggedIn()) {
				writeAccess.addAll(selectors(getMultiTenantOrigin(), getUser()
						.map(User::getWriteAccess)
						.orElse(List.of())));
			}
		}
		return writeAccess;
	}

	public List<QualifiedTag> getTagReadAccess() {
		if (tagReadAccess == null) {
			tagReadAccess = new ArrayList<>(getReadAccess());
			if (getClient().getDefaultTagReadAccess() != null) {
				tagReadAccess.addAll(getQualifiedTags(getClient().getDefaultTagReadAccess()));
			}
			if (getClient().isAllowAuthHeaders()) {
				tagReadAccess.addAll(getHeaderQualifiedTags(TAG_READ_ACCESS_HEADER));
			}
			tagReadAccess.addAll(getClaimQualifiedTags(getClient().getTagReadAccessClaim()));
			if (isLoggedIn()) {
				tagReadAccess.addAll(selectors(getMultiTenantOrigin(), getUser()
						.map(User::getTagReadAccess)
						.orElse(List.of())));
			}
		}
		return tagReadAccess;
	}

	public List<QualifiedTag> getTagWriteAccess() {
		if (tagWriteAccess == null) {
			tagWriteAccess = new ArrayList<>(getWriteAccess());
			if (getClient().getDefaultTagWriteAccess() != null) {
				tagWriteAccess.addAll(getQualifiedTags(getClient().getDefaultTagWriteAccess()));
			}
			if (getClient().isAllowAuthHeaders()) {
				tagWriteAccess.addAll(getHeaderQualifiedTags(TAG_WRITE_ACCESS_HEADER));
			}
			tagWriteAccess.addAll(getClaimQualifiedTags(getClient().getTagWriteAccessClaim()));
			if (isLoggedIn()) {
				tagWriteAccess.addAll(selectors(getMultiTenantOrigin(), getUser()
						.map(User::getTagWriteAccess)
						.orElse(List.of())));
			}
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
		return minRole() && hasAnyRole(role);
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
		if (authentication == null) {
			authentication = SecurityContextHolder.getContext().getAuthentication();
		}
		return authentication;
	}

	public Claims getClaims() {
		if (claims == null) {
			var auth = getAuthentication();
			if (auth instanceof JwtAuthentication j) {
				claims = j.getClaims();
			} else {
				claims = new DefaultClaims();
			}
		}
		return claims;
	}

	public Set<String> getAuthoritySet() {
		if (roles == null) {
			if (getAuthentication() == null) {
				roles = new HashSet<>();
			} else {
				var userAuthorities = new ArrayList<>(getAuthentication().getAuthorities());
				roles = AuthorityUtils.authorityListToSet(roleHierarchy != null ?
					roleHierarchy.getReachableGrantedAuthorities(userAuthorities) :
					userAuthorities);
			}
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

	private static List<String> getHeaderList(String headerName) {
		var header = getHeader(headerName);
		if (header != null) {
			return List.of(header.split(","));
		}
		return List.of();
	}

	public static String getHeader(String headerName) {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
			return a.getRequest().getHeader(headerName);
		}
		return null;
	}

	private static List<QualifiedTag> getHeaderQualifiedTags(String headerName) {
		return getHeaderList(headerName).stream().map(QualifiedTag::selector).toList();
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
