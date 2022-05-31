package jasper.component;

import jasper.service.dto.ProfileDto;
import org.springframework.data.domain.Page;

public interface UserManager {
	void createUser(String tag, String password, String[] roles);
	ProfileDto getUser(String tag);
	Page<ProfileDto> getUsers(int page, int size);
	void changePassword(String tag, String password);
	void setActive(String tag, boolean active);
	void changeRoles(String tag, String[] roles);
	void deleteUser(String tag);
}
