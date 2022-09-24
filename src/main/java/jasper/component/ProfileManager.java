package jasper.component;

import io.micrometer.core.annotation.Counted;
import jasper.service.dto.ProfileDto;
import org.springframework.data.domain.Page;

public interface ProfileManager {
	@Counted(value = "jasper.profile", extraTags = {"method", "create"})
	void createUser(String tag, String password, String[] roles);
	ProfileDto getUser(String tag);
	Page<ProfileDto> getUsers(int page, int size);
	@Counted(value = "jasper.profile", extraTags = {"method", "password"})
	void changePassword(String tag, String password);
	@Counted(value = "jasper.profile", extraTags = {"method", "activate"})
	void setActive(String tag, boolean active);
	@Counted(value = "jasper.profile", extraTags = {"method", "roles"})
	void changeRoles(String tag, String[] roles);
	@Counted(value = "jasper.profile", extraTags = {"method", "delete"})
	void deleteUser(String tag);
}
