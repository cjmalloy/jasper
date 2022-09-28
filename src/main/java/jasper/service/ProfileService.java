package jasper.service;

import io.micrometer.core.annotation.Counted;
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

import static jasper.security.AuthoritiesConstants.*;

@Profile("scim")
@Service
public class ProfileService {
	private static final Set<String> ROLES = Sets.newHashSet(ADMIN, MOD, EDITOR, USER, VIEWER);

	@Autowired
	ProfileManager profileManager;

	@Autowired
	Auth auth;

	@PreAuthorize("hasRole('MOD')")
	@Counted(value = "jasper.service", extraTags = {"service", "profile", "method", "create"})
	public void create(String tag, String password, String role) {
		if (role.equals(ADMIN) && !auth.hasRole(ADMIN)) {
			throw new InvalidUserProfileException("Cannot assign elevated role.");
		}
		if (tag.startsWith("user/")) {
			throw new InvalidUserProfileException("User tag must be protected or private.");
		}
		if (!tag.startsWith("_user/") && !tag.startsWith("+user/")) {
			throw new InvalidUserProfileException("User tag must be start with user/");
		}
		profileManager.createUser(tag.substring("+user/".length()), password, getRoles(tag, role));
	}

	private String[] getRoles(String tag, String role) {
		if (!ROLES.contains(role)) {
			throw new InvalidUserProfileException("Invalid role: " + role);
		}
		if (tag.startsWith("_")) {
			return new String[]{ role, PRIVATE };
		} else {
			return new String[]{ role };
		}
	}

	@PreAuthorize("@auth.canReadTag(#tag)")
	@Counted(value = "jasper.service", extraTags = {"service", "profile", "method", "get"})
	public ProfileDto get(String tag) {
		return profileManager.getUser(tag.substring("+user/".length()));
	}

	@PreAuthorize("hasRole('MOD')")
	@Counted(value = "jasper.service", extraTags = {"service", "profile", "method", "page"})
	public Page<ProfileDto> page(int pageNumber, int pageSize) {
		return profileManager.getUsers(pageNumber, pageSize);
	}

	@PreAuthorize("@auth.freshLogin() and @auth.canWriteTag(#tag)")
	@Counted(value = "jasper.service", extraTags = {"service", "profile", "method", "password"})
	public void changePassword(String tag, String password) {
		profileManager.changePassword(tag.substring("+user/".length()), password);
	}

	@PreAuthorize("hasRole('MOD')")
	@Counted(value = "jasper.service", extraTags = {"service", "profile", "method", "role"})
	public void changeRole(String tag, String role) {
		if (role.equals(ADMIN) && !auth.hasRole(ADMIN)) {
			throw new InvalidUserProfileException("Cannot assign elevated role.");
		}
		profileManager.changeRoles(tag.substring("+user/".length()), getRoles(tag, role));
	}

	@PreAuthorize("hasRole('MOD')")
	@Counted(value = "jasper.service", extraTags = {"service", "profile", "method", "active"})
	public void setActive(String tag, boolean active) {
		if (!active && auth.getUserTag().equals(tag)) {
			throw new DeactivateSelfException();
		}
		profileManager.setActive(tag.substring("+user/".length()), active);
	}

	@PreAuthorize("@auth.canWriteTag(#tag)")
	@Counted(value = "jasper.service", extraTags = {"service", "profile", "method", "delete"})
	public void delete(String tag) {
		profileManager.deleteUser(tag.substring("+user/".length()));
	}
}
