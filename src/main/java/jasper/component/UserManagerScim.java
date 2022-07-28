package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unboundid.scim2.common.types.Email;
import jasper.client.ScimClient;
import jasper.component.dto.CustomClaims;
import jasper.component.dto.ScimPatchOp;
import jasper.component.dto.ScimUserResource;
import jasper.service.dto.ProfileDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Profile("scim")
@Component
public class UserManagerScim implements UserManager {
	private static final Logger logger = LoggerFactory.getLogger(UserManagerScim.class);

	@Autowired
	ScimClient scimClient;

	@Autowired
	AccessToken accessToken;

	@Autowired
	ObjectMapper objectMapper;

	@Override
	public void createUser(String tag, String password, String[] roles) {
		var user = ScimUserResource.builder()
			.userName(tag)
			.password(password)
			.customClaims(new CustomClaims().setRoles(roles))
			.emails(List.of(new Email().setValue(tag + "@jasper.local")))
			.build();
		scimClient.createUser(accessToken.getAdminToken(), user);
	}

	private String[] getRoles(ScimUserResource user) {
		if (user.getCustomClaims() != null &&
			user.getCustomClaims().getAuth() != null &&
			!user.getCustomClaims().getAuth().isBlank()) {
			return user.getCustomClaims().getAuth().split(",");
		}
		return new String[] { "ROLE_VIEWER" };
	}

	@Override
	public ProfileDto getUser(String tag) {
		return mapUser(_getUser(tag));
	}

	private ProfileDto mapUser(ScimUserResource user) {
		var result = new ProfileDto();
		result.setActive(user.isActive());
		var roles = Arrays.asList(getRoles(user));
		for (var role : roles) {
			if (!role.equals("ROLE_PRIVATE")) {
				result.setRole(role);
				break;
			}
		}
		if (roles.contains("ROLE_PRIVATE")) {
			result.setTag("_user/" + user.getUserName());
		} else {
			result.setTag("+user/" + user.getUserName());
		}
		return result;
	}

	private ScimUserResource _getUser(String tag) {
		return objectMapper.convertValue(scimClient
			.getUser(accessToken.getAdminToken(), tag).getResources()
			.get(0), ScimUserResource.class);
	}

	public Page<ProfileDto> getUsers(int page, int size) {
		var users = scimClient
			.getUsers(accessToken.getAdminToken(), page + 1, size);
		return new PageImpl<>(
			users.getResources(),
			PageRequest.of(users.getStartIndex() - 1, users.getItemsPerPage()),
			users.getTotalResults())
			.map(m -> objectMapper.convertValue(m, ScimUserResource.class))
			.map(this::mapUser);
	}

	@Override
	public void changePassword(String tag, String password) {
		var id = (String) scimClient
			.getUser(accessToken.getAdminToken(), tag).getResources()
			.get(0)
			.get("id");
		var patch = ScimPatchOp.builder().operations(List.of(
			ScimPatchOp.Operation.builder()
				.op("replace")
				.path("password")
				.value(password)
				.build()
		)).build();
		scimClient.patchUser(accessToken.getAdminToken(), id, patch);
	}

	@Override
	public void setActive(String tag, boolean active) {
		var id = (String) scimClient
			.getUser(accessToken.getAdminToken(), tag).getResources()
			.get(0)
			.get("id");
		var patch = ScimPatchOp.builder().operations(List.of(
			ScimPatchOp.Operation.builder()
				.op("replace")
				.path("active")
				.value(active)
				.build()
		)).build();
		scimClient.patchUser(accessToken.getAdminToken(), id, patch);
	}

	@Override
	public void changeRoles(String tag, String[] roles) {
		var id = (String) scimClient
			.getUser(accessToken.getAdminToken(), tag).getResources()
			.get(0)
			.get("id");
		var patch = ScimPatchOp.builder().operations(List.of(
			ScimPatchOp.Operation.builder()
				.op("replace")
				.path("customClaims")
				.value(new CustomClaims().setRoles(roles))
				.build()
			)).build();
		scimClient.patchUser(accessToken.getAdminToken(), id, patch);
	}

	@Override
	public void deleteUser(String tag) {
		var user = _getUser(tag);
		scimClient.deleteUser(accessToken.getAdminToken(), user.getId());
	}
}
