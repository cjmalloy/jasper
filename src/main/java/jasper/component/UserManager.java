package jasper.component;

import org.springframework.data.domain.Page;

public interface UserManager {
	void createUser(String tag, String password, String[] roles);
	String[] getRoles(String tag);
	Page<String> getUsers(int page, int size);
	void changePassword(String tag, String password);
	void changeRoles(String tag, String[] roles);
	void deleteUser(String tag);
}
