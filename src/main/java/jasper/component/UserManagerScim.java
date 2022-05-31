package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unboundid.scim2.common.types.Email;
import jasper.client.ScimClient;
import jasper.component.dto.CustomClaims;
import jasper.component.dto.ScimPatchOp;
import jasper.component.dto.ScimUserResource;
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
		scimClient.createUser(accessToken.getAccessToken(), user);
	}

	@Override
	public String[] getRoles(String tag) {
		return getRoles(getUser(tag));
	}

	private String[] getRoles(ScimUserResource user) {
		if (user.getCustomClaims() != null &&
			user.getCustomClaims().getAuth() != null &&
			!user.getCustomClaims().getAuth().isBlank()) {
			return user.getCustomClaims().getAuth().split(",");
		}
		return new String[] { "ROLE_VIEWER" };
	}

	private ScimUserResource getUser(String tag) {
		return objectMapper.convertValue(scimClient
			.getUser(accessToken.getAccessToken(), tag).getResources()
			.get(0), ScimUserResource.class);
	}

	public Page<String> getUsers(int page, int size) {
		var users = scimClient
			.getUsers(accessToken.getAccessToken(), page + 1, size);
		return new PageImpl<>(
			users.getResources(),
			PageRequest.of(users.getStartIndex() - 1, users.getItemsPerPage()),
			users.getTotalResults())
			.map(map -> {
				var user = objectMapper.convertValue(map, ScimUserResource.class);
				if (Arrays.asList(getRoles(user)).contains("ROLE_PRIVATE")) {
					return "_user/" + user.getUserName();
				} else {
					return "+user/" + user.getUserName();
				}
			});
	}

	@Override
	public void changePassword(String tag, String password) {
		var id = (String) scimClient
			.getUser(accessToken.getAccessToken(), tag).getResources()
			.get(0)
			.get("id");
		var patch = ScimPatchOp.builder().operations(List.of(
			ScimPatchOp.Operation.builder()
				.op("replace")
				.path("password")
				.value(password)
				.build()
		)).build();
		scimClient.patchUser(accessToken.getAccessToken(), id, patch);
	}

	@Override
	public void changeRoles(String tag, String[] roles) {
		var id = (String) scimClient
			.getUser(accessToken.getAccessToken(), tag).getResources()
			.get(0)
			.get("id");
		var patch = ScimPatchOp.builder().operations(List.of(
			ScimPatchOp.Operation.builder()
				.op("replace")
				.path("customClaims")
				.value(new CustomClaims().setRoles(roles))
				.build()
			)).build();
		scimClient.patchUser(accessToken.getAccessToken(), id, patch);
	}

	@Override
	public void deleteUser(String tag) {
		var user = getUser(tag);
		scimClient.deleteUser(accessToken.getAccessToken(), user.getId());
	}
}
