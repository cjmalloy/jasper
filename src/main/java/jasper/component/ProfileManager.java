package jasper.component;

import jasper.service.dto.ProfileDto;
import org.springframework.data.domain.Page;

public interface ProfileManager {
	void createUser(String userName, String password, String origin, String[] roles);
	ProfileDto getUser(String userName, String origin);
	Page<ProfileDto> getUsers(String origin, int page, int size);
	void changePassword(String userName, String password);
	void setActive(String userName, boolean active);
	void changeRoles(String userName, String origin, String[] roles);
	void deleteUser(String userName);
}
