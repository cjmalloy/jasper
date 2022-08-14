package jasper.service;

import jasper.component.UserManager;
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

@Profile("scim")
@Service
public class ProfileService {
	private static final Set<String> ROLES = Sets.newHashSet("ROLE_ADMIN", "ROLE_MOD", "ROLE_EDITOR", "ROLE_USER", "ROLE_VIEWER");

	@Autowired
	UserManager userManager;

	@Autowired
	Auth auth;

	@PreAuthorize("hasRole('MOD')")
	public void create(String tag, String password, String role) {
		if (role.equals("ROLE_ADMIN") && !auth.hasRole("ADMIN")) {
			throw new InvalidUserProfileException("Cannot assign elevated role.");
		}
		if (tag.startsWith("user/")) {
			throw new InvalidUserProfileException("User tag must be protected or private.");
		}
		if (!tag.startsWith("_user/") && !tag.startsWith("+user/")) {
			throw new InvalidUserProfileException("User tag must be start with user/");
		}
		userManager.createUser(tag.substring("+user/".length()), password, getRoles(tag, role));
	}

	private String[] getRoles(String tag, String role) {
		if (!ROLES.contains(role)) {
			throw new InvalidUserProfileException("Invalid role: " + role);
		}
		if (tag.startsWith("_")) {
			return new String[]{ role, "ROLE_PRIVATE" };
		} else {
			return new String[]{ role };
		}
	}

	@PreAuthorize("@auth.canReadTag(#tag)")
	public ProfileDto getUser(String tag) {
		return userManager.getUser(tag.substring("+user/".length()));
	}

	@PreAuthorize("hasRole('MOD')")
	public Page<ProfileDto> getUsers(int pageNumber, int pageSize) {
		return userManager.getUsers(pageNumber, pageSize);
	}

	@PreAuthorize("@auth.freshLogin() and @auth.canWriteTag(#tag)")
	public void changePassword(String tag, String password) {
		userManager.changePassword(tag.substring("+user/".length()), password);
	}

	@PreAuthorize("hasRole('MOD')")
	public void changeRole(String tag, String role) {
		if (role.equals("ROLE_ADMIN") && !auth.hasRole("ADMIN")) {
			throw new InvalidUserProfileException("Cannot assign elevated role.");
		}
		userManager.changeRoles(tag.substring("+user/".length()), getRoles(tag, role));
	}

	@PreAuthorize("hasRole('MOD')")
	public void setActive(String tag, boolean active) {
		if (!active && auth.getUserTag().equals(tag)) {
			throw new DeactivateSelfException();
		}
		userManager.setActive(tag.substring("+user/".length()), active);
	}

	@PreAuthorize("@auth.canWriteTag(#tag)")
	public void delete(String tag) {
		userManager.deleteUser(tag.substring("+user/".length()));
	}
}
