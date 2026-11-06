package jasper.repository.spec;

import jasper.domain.User;
import org.springframework.data.jpa.domain.Specification;

public class UserSpec {

	public static Specification<User> hasAuthorizedKeys() {
		return (root, query, cb) ->
			cb.isNotNull(
				root.get("authorizedKeys"));
	}
}
