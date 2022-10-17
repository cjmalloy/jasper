package jasper.service;

import io.micrometer.core.annotation.Timed;
import jasper.component.ProfileManager;
import jasper.errors.DeactivateSelfException;
import jasper.errors.InvalidUserProfileException;
import jasper.security.Auth;
import jasper.service.dto.ProfileDto;
import org.apache.commons.compress.utils.Sets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.Set;

import static jasper.repository.spec.QualifiedTag.selector;
import static jasper.security.AuthoritiesConstants.ADMIN;
import static jasper.security.AuthoritiesConstants.EDITOR;
import static jasper.security.AuthoritiesConstants.MOD;
import static jasper.security.AuthoritiesConstants.PRIVATE;
import static jasper.security.AuthoritiesConstants.SA;
import static jasper.security.AuthoritiesConstants.USER;
import static jasper.security.AuthoritiesConstants.VIEWER;

@Profile("scim")
@Service
public class ProfileService {
	private static final Set<String> ROLES = Sets.newHashSet(SA, ADMIN, MOD, EDITOR, USER, VIEWER);

	@Autowired
	ProfileManager profileManager;

	@Autowired
	Auth auth;

	@PreAuthorize("@auth.isSysMod()")
	@Timed(value = "jasper.service", extraTags = {"service", "profile"}, histogram = true)
	public void create(String qualifiedTag, String password, String role) {
		validateRole(role);
		if (qualifiedTag.startsWith("user/")) {
			throw new InvalidUserProfileException("User tag must be protected or private.");
		}
		if (!qualifiedTag.startsWith("_user/") && !qualifiedTag.startsWith("+user/")) {
			throw new InvalidUserProfileException("User tag must be start with user/");
		}
		var qt = selector(qualifiedTag);
		profileManager.createUser(qt.tag.substring("+user/".length()), password, qt.origin, getRoles(qualifiedTag, role));
	}

	private void validateRole(String role) {
		if (role.equals(SA) && !auth.hasRole(SA) ||
			role.equals(ADMIN) && !auth.hasRole(ADMIN)) {
			throw new InvalidUserProfileException("Cannot assign elevated role.");
		}
	}

	private String[] getRoles(String qualifiedTag, String role) {
		if (!ROLES.contains(role)) {
			throw new InvalidUserProfileException("Invalid role: " + role);
		}
		if (qualifiedTag.startsWith("_")) {
			return new String[]{ role, PRIVATE };
		} else {
			return new String[]{ role };
		}
	}

	@PreAuthorize("@auth.canReadTag(#qualifiedTag)")
	@Timed(value = "jasper.service", extraTags = {"service", "profile"}, histogram = true)
	public ProfileDto get(String qualifiedTag) {
		var qt = selector(qualifiedTag);
		return profileManager.getUser(qt.tag.substring("+user/".length()), qt.origin);
	}

	@PreAuthorize("@auth.isSysMod()")
	@Timed(value = "jasper.service", extraTags = {"service", "profile"}, histogram = true)
	public Page<ProfileDto> page(int pageNumber, int pageSize) {
		return profileManager.getUsers(auth.getOrigin(), pageNumber, pageSize);
	}

	@PreAuthorize("@auth.freshLogin() and (@auth.isUser(#qualifiedTag) or @auth.isSysMod())")
	@Timed(value = "jasper.service", extraTags = {"service", "profile"}, histogram = true)
	public void changePassword(String qualifiedTag, String password) {
		profileManager.changePassword(qualifiedTag, password);
	}

	@PreAuthorize("hasRole('MOD') and @auth.canWriteTag(#qualifiedTag)")
	@Timed(value = "jasper.service", extraTags = {"service", "profile"}, histogram = true)
	public void changeRole(String qualifiedTag, String role) {
		validateRole(role);
		var qt = selector(qualifiedTag);
		profileManager.changeRoles(qt.tag.substring("+user/".length()), qt.origin, getRoles(qualifiedTag, role));
	}

	@PreAuthorize("@auth.isSysMod()")
	@Timed(value = "jasper.service", extraTags = {"service", "profile"}, histogram = true)
	public void setActive(String qualifiedTag, boolean active) {
		if (!active && auth.getUserTag().tag.equals(selector(qualifiedTag).tag)) {
			throw new DeactivateSelfException();
		}
		var qt = selector(qualifiedTag);
		profileManager.setActive(qt.tag.substring("+user/".length()), active);
	}

	@PreAuthorize("@auth.isSysMod()")
	@Timed(value = "jasper.service", extraTags = {"service", "profile"}, histogram = true)
	public void delete(String qualifiedTag) {
		var qt = selector(qualifiedTag);
		profileManager.deleteUser(qt.tag.substring("+user/".length()));
	}
}
