package jasper.security;

import jasper.domain.User;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public class AuthUnitTest {

	Auth getAuth(User user, String ...roles) {
		var a = new Auth();
		a.userTag = user.getTag();
		a.user = Optional.of(user);
		a.origin = user.getOrigin();
		a.roles = new HashSet<>(List.of(roles));
		return a;
	}

	@Test
	void test() {

	}
}
