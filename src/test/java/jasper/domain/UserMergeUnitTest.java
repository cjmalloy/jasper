package jasper.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static jasper.security.AuthoritiesConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

public class UserMergeUnitTest {

	@Test
	void testMergeEmptyList() {
		var result = User.merge(List.of());

		assertThat(result).isEmpty();
	}

	@Test
	void testMergeSingleUser() {
		var user = new User();
		user.setTag("+user/test");
		user.setOrigin("");
		user.setName("Test User");
		user.setRole(USER);
		user.setReadAccess(List.of("tag1", "tag2"));
		user.setWriteAccess(List.of("tag3"));

		var result = User.merge(List.of(user));

		assertThat(result).isPresent();
		assertThat(result.get().getTag()).isEqualTo("+user/test");
		assertThat(result.get().getOrigin()).isEqualTo("");
		assertThat(result.get().getName()).isEqualTo("Test User");
		assertThat(result.get().getRole()).isEqualTo(USER);
		assertThat(result.get().getReadAccess()).containsExactly("tag1", "tag2");
		assertThat(result.get().getWriteAccess()).containsExactly("tag3");
	}

	@Test
	void testMergeRolePrecedence_AdminWins() {
		var user1 = createUser("+user/test", VIEWER);
		var user2 = createUser("+user/test", ADMIN);
		var user3 = createUser("+user/test", MOD);

		var result = User.merge(List.of(user1, user2, user3));

		assertThat(result).isPresent();
		assertThat(result.get().getRole()).isEqualTo(ADMIN);
	}

	@Test
	void testMergeRolePrecedence_ModWins() {
		var user1 = createUser("+user/test", USER);
		var user2 = createUser("+user/test", MOD);
		var user3 = createUser("+user/test", VIEWER);

		var result = User.merge(List.of(user1, user2, user3));

		assertThat(result).isPresent();
		assertThat(result.get().getRole()).isEqualTo(MOD);
	}

	@Test
	void testMergeRolePrecedence_EditorWins() {
		var user1 = createUser("+user/test", VIEWER);
		var user2 = createUser("+user/test", EDITOR);
		var user3 = createUser("+user/test", USER);

		var result = User.merge(List.of(user1, user2, user3));

		assertThat(result).isPresent();
		assertThat(result.get().getRole()).isEqualTo(EDITOR);
	}

	@Test
	void testMergeRolePrecedence_UserWins() {
		var user1 = createUser("+user/test", VIEWER);
		var user2 = createUser("+user/test", USER);

		var result = User.merge(List.of(user1, user2));

		assertThat(result).isPresent();
		assertThat(result.get().getRole()).isEqualTo(USER);
	}

	@Test
	void testMergeRolePrecedence_ViewerWins() {
		var user1 = createUser("+user/test", ANONYMOUS);
		var user2 = createUser("+user/test", VIEWER);

		var result = User.merge(List.of(user1, user2));

		assertThat(result).isPresent();
		assertThat(result.get().getRole()).isEqualTo(VIEWER);
	}

	@Test
	void testMergeRolePrecedence_AnonymousDefault() {
		var user1 = createUser("+user/test", ANONYMOUS);
		var user2 = createUser("+user/test", ANONYMOUS);

		var result = User.merge(List.of(user1, user2));

		assertThat(result).isPresent();
		assertThat(result.get().getRole()).isEqualTo(ANONYMOUS);
	}

	@Test
	void testMergeRolePrecedence_BannedUser() {
		var user1 = createUser("+user/test", BANNED);
		var user2 = createUser("+user/test", USER);

		var result = User.merge(List.of(user1, user2));

		assertThat(result).isPresent();
		// BANNED is not in the precedence list, so USER should win
		assertThat(result.get().getRole()).isEqualTo(USER);
	}

	@Test
	void testMergeAccessPermissions_Distinct() {
		var user1 = new User();
		user1.setTag("+user/test");
		user1.setOrigin("");
		user1.setReadAccess(List.of("tag1", "tag2"));
		user1.setWriteAccess(List.of("tag3"));
		user1.setTagReadAccess(List.of("tag4"));
		user1.setTagWriteAccess(List.of("tag5"));

		var user2 = new User();
		user2.setTag("+user/test");
		user2.setOrigin("");
		user2.setReadAccess(List.of("tag2", "tag6")); // tag2 is duplicate
		user2.setWriteAccess(List.of("tag3", "tag7")); // tag3 is duplicate
		user2.setTagReadAccess(List.of("tag4", "tag8")); // tag4 is duplicate
		user2.setTagWriteAccess(List.of("tag5", "tag9")); // tag5 is duplicate

		var result = User.merge(List.of(user1, user2));

		assertThat(result).isPresent();
		// Verify distinct operation removes duplicates
		assertThat(result.get().getReadAccess()).containsExactlyInAnyOrder("tag1", "tag2", "tag6");
		assertThat(result.get().getWriteAccess()).containsExactlyInAnyOrder("tag3", "tag7");
		assertThat(result.get().getTagReadAccess()).containsExactlyInAnyOrder("tag4", "tag8");
		assertThat(result.get().getTagWriteAccess()).containsExactlyInAnyOrder("tag5", "tag9");
	}

	@Test
	void testMergeAccessPermissions_AllUnique() {
		var user1 = new User();
		user1.setTag("+user/test");
		user1.setOrigin("");
		user1.setReadAccess(List.of("tag1", "tag2"));
		user1.setWriteAccess(List.of("tag3"));

		var user2 = new User();
		user2.setTag("+user/test");
		user2.setOrigin("");
		user2.setReadAccess(List.of("tag4", "tag5"));
		user2.setWriteAccess(List.of("tag6"));

		var result = User.merge(List.of(user1, user2));

		assertThat(result).isPresent();
		assertThat(result.get().getReadAccess()).containsExactlyInAnyOrder("tag1", "tag2", "tag4", "tag5");
		assertThat(result.get().getWriteAccess()).containsExactlyInAnyOrder("tag3", "tag6");
	}

	@Test
	void testMergeAccessPermissions_WithNulls() {
		var user1 = new User();
		user1.setTag("+user/test");
		user1.setOrigin("");
		user1.setReadAccess(List.of("tag1"));
		user1.setWriteAccess(null); // null access list

		var user2 = new User();
		user2.setTag("+user/test");
		user2.setOrigin("");
		user2.setReadAccess(null); // null access list
		user2.setWriteAccess(List.of("tag2"));

		var result = User.merge(List.of(user1, user2));

		assertThat(result).isPresent();
		// Verify emptyIfNull handles null access lists properly
		assertThat(result.get().getReadAccess()).containsExactly("tag1");
		assertThat(result.get().getWriteAccess()).containsExactly("tag2");
	}

	@Test
	void testMergePreservesFirstUserProperties() {
		var user1 = new User();
		user1.setTag("+user/first");
		user1.setOrigin("origin1");
		user1.setName("First User");
		user1.setKey("key1".getBytes());
		user1.setPubKey("pubKey1".getBytes());
		user1.setAuthorizedKeys("auth1\nauth2");
		user1.setExternal(External.builder().ids(List.of("ext1")).build());

		var user2 = new User();
		user2.setTag("+user/second");
		user2.setOrigin("origin2");
		user2.setName("Second User");
		user2.setKey("key2".getBytes());
		user2.setPubKey("pubKey2".getBytes());
		user2.setAuthorizedKeys("auth3");
		user2.setExternal(External.builder().ids(List.of("ext2")).build());

		var result = User.merge(List.of(user1, user2));

		assertThat(result).isPresent();
		// All basic properties should come from first user
		assertThat(result.get().getTag()).isEqualTo("+user/first");
		assertThat(result.get().getOrigin()).isEqualTo("origin1");
		assertThat(result.get().getName()).isEqualTo("First User");
		assertThat(result.get().getKey()).isEqualTo("key1".getBytes());
		assertThat(result.get().getPubKey()).isEqualTo("pubKey1".getBytes());
		assertThat(result.get().getAuthorizedKeys()).isEqualTo("auth1\nauth2");
		assertThat(result.get().getExternal()).isNotNull();
		assertThat(result.get().getExternal().getIds()).containsExactly("ext1");
	}

	@Test
	void testMergeMultipleUsers_ComplexScenario() {
		var user1 = new User();
		user1.setTag("+user/test");
		user1.setOrigin("");
		user1.setName("Primary");
		user1.setRole(EDITOR);
		user1.setReadAccess(List.of("tag1", "tag2"));

		var user2 = new User();
		user2.setTag("_user/test");
		user2.setOrigin("");
		user2.setRole(ADMIN);
		user2.setReadAccess(List.of("tag2", "tag3"));
		user2.setWriteAccess(List.of("tag4"));

		var user3 = new User();
		user3.setTag("+user/test");
		user3.setOrigin("@other");
		user3.setRole(USER);
		user3.setTagReadAccess(List.of("tag5"));

		var result = User.merge(List.of(user1, user2, user3));

		assertThat(result).isPresent();
		assertThat(result.get().getTag()).isEqualTo("+user/test");
		assertThat(result.get().getName()).isEqualTo("Primary");
		assertThat(result.get().getRole()).isEqualTo(ADMIN); // Highest role
		assertThat(result.get().getReadAccess()).containsExactlyInAnyOrder("tag1", "tag2", "tag3");
		assertThat(result.get().getWriteAccess()).containsExactly("tag4");
		assertThat(result.get().getTagReadAccess()).containsExactly("tag5");
	}

	private User createUser(String tag, String role) {
		var user = new User();
		user.setTag(tag);
		user.setOrigin("");
		user.setRole(role);
		return user;
	}
}
