package jasper.service;

import jakarta.validation.ConstraintViolationException;
import jasper.IntegrationTest;
import jasper.domain.User;
import jasper.domain.User_;
import jasper.errors.NotFoundException;
import jasper.repository.UserRepository;
import jasper.repository.filter.TagFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.data.domain.Sort.by;

@WithMockUser("+user/tester")
@IntegrationTest
public class UserServiceIT {

	@Autowired
	UserService userService;

	@Autowired
	UserRepository userRepository;

	@BeforeEach
	void init() {
		userRepository.deleteAll();
	}

	@Test
	void testCreateUser() {
		var user = new User();
		user.setTag("+user/tester");
		user.setName("Custom");

		userService.create(user);

		assertThat(userRepository.existsByQualifiedTag("+user/tester"))
			.isTrue();
		var fetched = userRepository.findOneByQualifiedTag("+user/tester").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/tester");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	void testCreateUserFailed() {
		var user = new User();
		user.setTag("+user/other");
		user.setName("Custom");

		assertThatThrownBy(() -> userService.create(user))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(userRepository.existsByQualifiedTag("+user/tester"))
			.isFalse();
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = "MOD")
	void testModCreateUser() {
		var user = new User();
		user.setTag("+user/other");
		user.setName("Custom");

		userService.create(user);

		assertThat(userRepository.existsByQualifiedTag("+user/other"))
			.isTrue();
		var fetched = userRepository.findOneByQualifiedTag("+user/other").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/other");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = "MOD")
	void testModCreateUserWithTags() {
		var user = new User();
		user.setTag("+user/other");
		user.setName("Custom");
		user.setReadAccess(List.of("custom"));
		user.setWriteAccess(List.of("custom"));

		userService.create(user);

		assertThat(userRepository.existsByQualifiedTag("+user/other"))
			.isTrue();
		var fetched = userRepository.findOneByQualifiedTag("+user/other").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/other");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
		assertThat(fetched.getReadAccess())
			.containsExactly("custom");
		assertThat(fetched.getWriteAccess())
			.containsExactly("custom");
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = "MOD")
	void testModCreateUserWithNotTagSelectorsFailed() {
		var user = new User();
		user.setTag("+user/other");
		user.setWriteAccess(List.of("!@excluded"));

		assertThatThrownBy(() -> userService.create(user))
			.isInstanceOf(ConstraintViolationException.class);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = "MOD")
	void testModCreateUserWithPrivateTags() {
		var user = new User();
		user.setTag("+user/other");
		user.setName("Custom");
		user.setReadAccess(List.of("_custom"));
		user.setWriteAccess(List.of("_custom"));

		userService.create(user);

		assertThat(userRepository.existsByQualifiedTag("+user/other"))
			.isTrue();
		var fetched = userRepository.findOneByQualifiedTag("+user/other").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/other");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
		assertThat(fetched.getReadAccess())
			.containsExactly("_custom");
		assertThat(fetched.getWriteAccess())
			.containsExactly("_custom");
	}

	@Test
	void testCreateUserWithTags() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("+custom", "+user/other"));
		user.setWriteAccess(List.of("+custom", "+user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("+user/other");
		other.setName("Custom");
		other.setReadAccess(List.of("+custom"));
		other.setWriteAccess(List.of("+custom"));

		userService.create(other);

		assertThat(userRepository.existsByQualifiedTag("+user/other"))
			.isTrue();
		var fetched = userRepository.findOneByQualifiedTag("+user/other").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/other");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
		assertThat(fetched.getReadAccess())
			.containsExactly("+custom");
		assertThat(fetched.getWriteAccess())
			.containsExactly("+custom");
	}

	@Test
	void testCreateUserWithPrivateTags() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_secret", "+user/other"));
		user.setWriteAccess(List.of("_secret", "+user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("+user/other");
		other.setName("Custom");
		other.setReadAccess(List.of("_secret"));
		other.setWriteAccess(List.of("_secret"));

		userService.create(other);

		assertThat(userRepository.existsByQualifiedTag("+user/other"))
			.isTrue();
		var fetched = userRepository.findOneByQualifiedTag("+user/other").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/other");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
		assertThat(fetched.getReadAccess())
			.containsExactly("_secret");
		assertThat(fetched.getWriteAccess())
			.containsExactly("_secret");
	}

	@Test
	void testCreateUserWithTagsFailed() {
		var other = new User();
		other.setTag("+user/other");
		other.setName("Custom");
		other.setReadAccess(List.of("custom"));
		other.setWriteAccess(List.of("custom"));

		assertThatThrownBy(() -> userService.create(other))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(userRepository.existsByQualifiedTag("+user/other"))
			.isFalse();
	}

	@Test
	void testCreateUserWithPrivateTagsFailed() {
		var other = new User();
		other.setTag("+user/other");
		other.setName("Custom");
		other.setReadAccess(List.of("_custom"));
		other.setWriteAccess(List.of("_custom"));

		assertThatThrownBy(() -> userService.create(other))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(userRepository.existsByQualifiedTag("+user/other"))
			.isFalse();
	}

	@Test
	void testReadNonExistentUser() {
		assertThatThrownBy(() -> userService.get("+user/other"))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void testReadNonExistentPrivateUser() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_user/other"));
		userRepository.save(user);

		assertThatThrownBy(() -> userService.get("_user/other"))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void testReadPrivateUserDenied() {
		assertThatThrownBy(() -> userService.get("_user/other"))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testReadPublicUser() {
		var user = new User();
		user.setTag("+user/other");
		user.setName("Custom");
		userRepository.save(user);

		var fetched = userService.get("+user/other");

		assertThat(fetched.getTag())
			.isEqualTo("+user/other");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	void testReadUser() {
		var user = new User();
		user.setTag("+user/other");
		user.setName("Custom");
		userRepository.save(user);

		var fetched = userService.get("+user/other");

		assertThat(fetched.getTag())
			.isEqualTo("+user/other");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	void testReadUserSelf() {
		var user = new User();
		user.setTag("+user/tester");
		user.setName("Custom");
		userRepository.save(user);

		var fetched = userService.get("+user/tester");

		assertThat(fetched.getTag())
			.isEqualTo("+user/tester");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	void testReadPrivateUserFailed() {
		var user = new User();
		user.setTag("_user/other");
		user.setName("Secret");
		userRepository.save(user);

		assertThatThrownBy(() -> userService.get("_user/other"))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testReadPrivateOtherUser() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("_user/other");
		other.setName("Secret");
		userRepository.save(other);

		var fetched = userService.get("_user/other");

		assertThat(fetched.getTag())
			.isEqualTo("_user/other");
		assertThat(fetched.getName())
			.isEqualTo("Secret");
	}

	@Test
	@WithMockUser("_user/tester")
	void testReadPrivateUser() {
		var user = new User();
		user.setTag("_user/tester");
		user.setName("Secret");
		userRepository.save(user);

		var fetched = userService.get("_user/tester");

		assertThat(fetched.getTag())
			.isEqualTo("_user/tester");
		assertThat(fetched.getName())
			.isEqualTo("Secret");
	}

	@Test
	void testReadPrivateUserUserFailed() {
		var user = new User();
		user.setTag("_user/other");
		user.setName("Secret");
		userRepository.save(user);

		assertThatThrownBy(() -> userService.get("_user/other"))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testReadOtherUserWithAllOriginSelector() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("_user/other");
		other.setName("Secret");
		userRepository.save(other);

		var fetched = userService.get("_user/other");

		assertThat(fetched.getTag())
			.isEqualTo("_user/other");
		assertThat(fetched.getName())
			.isEqualTo("Secret");
	}

	@Test
	void testPagePublicUser() {
		var user = new User();
		user.setTag("+user/other");
		user.setName("Custom");
		userRepository.save(user);

		var page = userService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10, by(User_.TAG)));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().get(0).getTag())
			.isEqualTo("+user/other");
		assertThat(page.getContent().get(0).getName())
			.isEqualTo("Custom");
	}

	@Test
	void testPagePrivateTagHidden() {
		var user = new User();
		user.setTag("_user/other");
		user.setName("Secret");
		userRepository.save(user);

		var page = userService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10, by(User_.TAG)));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	void testPagePrivateUser() {
		var user = new User();
		user.setTag("+user/tester");
		user.setName("Custom");
		user.setReadAccess(List.of("_user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("_user/other");
		other.setName("Secret");
		userRepository.save(other);

		var page = userService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10, by(User_.TAG)));

		assertThat(page.getTotalElements())
			.isEqualTo(2);
		assertThat(page.getContent().get(0).getTag())
			.isIn("+user/tester", "_user/other");
		assertThat(page.getContent().get(0).getName())
			.isIn("Custom", "Secret");
		assertThat(page.getContent().get(1).getTag())
			.isIn("+user/tester", "_user/other");
		assertThat(page.getContent().get(1).getName())
			.isIn("Custom", "Secret");
	}

	@Test
	void testPagePrivateUserWithoutHiddenTags() {
		var user = new User();
		user.setTag("+user/tester");
		user.setName("Custom");
		user.setReadAccess(List.of("_user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("_user/other");
		other.setName("Secret");
		other.setReadAccess(List.of("custom", "_secret"));
		other.setWriteAccess(List.of("custom", "_secret"));
		userRepository.save(other);

		var page = userService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10, by(User_.TAG)));

		assertThat(page.getTotalElements())
			.isEqualTo(2);
		assertThat(page.getContent().get(0).getTag())
			.isEqualTo("_user/other");
		assertThat(page.getContent().get(0).getName())
			.isEqualTo("Secret");
		assertThat(page.getContent().get(0).getReadAccess())
			.containsExactly("custom");
		assertThat(page.getContent().get(0).getWriteAccess())
			.containsExactly("custom");
	}

	@Test
	void testPagePrivateUserWithHiddenTags() {
		var user = new User();
		user.setTag("+user/tester");
		user.setName("Custom");
		user.setReadAccess(List.of("_secret", "_user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("_user/other");
		other.setName("Secret");
		other.setReadAccess(List.of("_secret"));
		other.setWriteAccess(List.of("_secret"));
		userRepository.save(other);

		var page = userService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10, by(User_.TAG)));

		assertThat(page.getTotalElements())
			.isEqualTo(2);
		assertThat(page.getContent().get(0).getTag())
			.isEqualTo("_user/other");
		assertThat(page.getContent().get(0).getName())
			.isEqualTo("Secret");
		assertThat(page.getContent().get(0).getReadAccess())
			.containsExactly("_secret");
		assertThat(page.getContent().get(0).getWriteAccess())
			.containsExactly("_secret");
	}

	@Test
	@WithMockUser("_user/tester")
	void testPagePrivateUserUser() {
		var user = new User();
		user.setTag("_user/tester");
		user.setName("Secret");
		userRepository.save(user);

		var page = userService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10, by(User_.TAG)));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().get(0).getTag())
			.isEqualTo("_user/tester");
		assertThat(page.getContent().get(0).getName())
			.isEqualTo("Secret");
	}

	@Test
	@WithMockUser("_user/tester")
	void testPagePrivateUserUserFailed() {
		var user = new User();
		user.setTag("_user/other");
		user.setName("Secret");
		userRepository.save(user);

		var page = userService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	void testPageUserUser() {
		var user = new User();
		user.setTag("+user/tester");
		user.setName("Secret");
		userRepository.save(user);

		var page = userService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().get(0).getTag())
			.isEqualTo("+user/tester");
		assertThat(page.getContent().get(0).getName())
			.isEqualTo("Secret");
	}

	User user(String tag) {
		var u = new User();
		u.setTag(tag);
		userRepository.save(u);
		return u;
	}

	@Test
	void testGetPageRefWithQuery() {
		user("+user/public");
		user("+user/custom");
		user("+user/extra");

		var page = userService.page(
			TagFilter
				.builder()
				.query("+user/custom")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
	}

	@Test
	void testGetEmptyPageRefWithEmptyQuery() {
		user("+user/custom");
		user("+user/extra");

		var page = userService.page(
			TagFilter
				.builder()
				.query("!@*")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	void testGetEmptyPageRefWithEmptyQueryRoot() {
		user("+user");
		user("+user/custom");
		user("+user/extra");

		var page = userService.page(
			TagFilter
				.builder()
				.query("!@*")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	void testGetPageWithNotQueryRef() {
		user("+user/test");

		var page = userService.page(
			TagFilter
				.builder()
				.query("!+user/test")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	void testGetPageWithNotQueryFoundRef() {
		user("+user/public");

		var page = userService.page(
			TagFilter
				.builder()
				.query("!+user/test")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
	}

	@Test
	void testUpdateOtherUserWithoutRemovingHiddenTags() {
		var user = new User();
		user.setTag("+user/tester");
		user.setWriteAccess(List.of("+user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("+user/other");
		other.setName("First");
		other.setReadAccess(List.of("_secret"));
		other.setModified(Instant.now());
		userRepository.save(other);
		var updated = new User();
		updated.setTag("+user/other");
		updated.setName("Second");
		updated.setModified(other.getModified());

		userService.update(updated);

		assertThat(userRepository.existsByQualifiedTag("+user/other"))
			.isTrue();
		var fetched = userRepository.findOneByQualifiedTag("+user/other").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/other");
		assertThat(fetched.getName())
			.isEqualTo("Second");
		assertThat(fetched.getReadAccess())
			.containsExactly("_secret");
	}

	@Test
	void testUpdateOtherUserAddingTags() {
		var user = new User();
		user.setTag("+user/tester");
		user.setWriteAccess(List.of("+user/other", "+custom"));
		userRepository.save(user);
		var other = new User();
		other.setTag("+user/other");
		other.setName("First");
		other.setReadAccess(List.of("_secret"));
		other.setModified(Instant.now());
		userRepository.save(other);
		var updated = new User();
		updated.setTag("+user/other");
		updated.setName("Second");
		updated.setReadAccess(new ArrayList<>(List.of("+custom")));
		updated.setModified(other.getModified());

		userService.update(updated);

		assertThat(userRepository.existsByQualifiedTag("+user/other"))
			.isTrue();
		var fetched = userRepository.findOneByQualifiedTag("+user/other").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/other");
		assertThat(fetched.getName())
			.isEqualTo("Second");
		assertThat(fetched.getReadAccess())
			.contains("+custom", "_secret");
	}

	@Test
	void testUpdateOtherUser() {
		var user = new User();
		user.setTag("+user/tester");
		user.setWriteAccess(List.of("+user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("+user/other");
		other.setName("First");
		other.setModified(Instant.now());
		userRepository.save(other);
		var updated = new User();
		updated.setTag("+user/other");
		updated.setName("Second");
		updated.setModified(other.getModified());

		userService.update(updated);

		assertThat(userRepository.existsByQualifiedTag("+user/other"))
			.isTrue();
		var fetched = userRepository.findOneByQualifiedTag("+user/other").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/other");
		assertThat(fetched.getName())
			.isEqualTo("Second");
	}

	@Test
	void testUpdateOtherUserFailed() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("+user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("+user/other");
		other.setName("First");
		userRepository.save(other);
		var updated = new User();
		updated.setTag("+user/other");
		updated.setName("Second");

		assertThatThrownBy(() -> userService.update(updated))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(userRepository.existsByQualifiedTag("+user/other"))
			.isTrue();
		var fetched = userRepository.findOneByQualifiedTag("+user/other").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/other");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testUpdateUser() {
		var user = new User();
		user.setTag("+user/tester");
		user.setName("First");
		user.setModified(Instant.now());
		userRepository.save(user);
		var updated = new User();
		updated.setTag("+user/tester");
		updated.setName("Second");
		updated.setModified(user.getModified());

		userService.update(updated);

		assertThat(userRepository.existsByQualifiedTag("+user/tester"))
			.isTrue();
		var fetched = userRepository.findOneByQualifiedTag("+user/tester").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/tester");
		assertThat(fetched.getName())
			.isEqualTo("Second");
	}

	@Test
	void testUpdateUserFailed() {
		var user = new User();
		user.setTag("+user/other");
		user.setName("First");
		userRepository.save(user);
		var updated = new User();
		updated.setTag("+user/other");
		updated.setName("Second");

		assertThatThrownBy(() -> userService.update(updated))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(userRepository.existsByQualifiedTag("+user/other"))
			.isTrue();
		var fetched = userRepository.findOneByQualifiedTag("+user/other").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/other");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testUpdatePrivateUser() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_user/other"));
		user.setWriteAccess(List.of("_user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("_user/other");
		other.setName("First");
		other.setModified(Instant.now());
		userRepository.save(other);
		var updated = new User();
		updated.setTag("_user/other");
		updated.setName("Second");
		updated.setModified(other.getModified());

		userService.update(updated);

		assertThat(userRepository.existsByQualifiedTag("_user/other"))
			.isTrue();
		var fetched = userRepository.findOneByQualifiedTag("_user/other").get();
		assertThat(fetched.getTag())
			.isEqualTo("_user/other");
		assertThat(fetched.getName())
			.isEqualTo("Second");
	}

	@Test
	void testUpdatePrivateUserFailed() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("_user/other");
		other.setName("First");
		userRepository.save(other);
		var updated = new User();
		updated.setTag("_user/other");
		updated.setName("Second");

		assertThatThrownBy(() -> userService.update(updated))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(userRepository.existsByQualifiedTag("_user/other"))
			.isTrue();
		var fetched = userRepository.findOneByQualifiedTag("_user/other").get();
		assertThat(fetched.getTag())
			.isEqualTo("_user/other");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testDeleteUser() {
		var user = new User();
		user.setTag("+user/tester");
		user.setWriteAccess(List.of("+user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("+user/other");
		other.setName("First");
		userRepository.save(other);

		userService.delete("+user/other");

		assertThat(userRepository.existsByQualifiedTag("+user/other"))
			.isFalse();
	}

	@Test
	void testDeleteUserFailed() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("+user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("+user/other");
		other.setName("First");
		userRepository.save(other);

		assertThatThrownBy(() -> userService.delete("+user/other"))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(userRepository.existsByQualifiedTag("+user/other"))
			.isTrue();
		var fetched = userRepository.findOneByQualifiedTag("+user/other").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/other");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testDeletePrivateUser() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("+user/other"));
		user.setWriteAccess(List.of("+user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("+user/other");
		other.setName("First");
		userRepository.save(other);

		userService.delete("+user/other");

		assertThat(userRepository.existsByQualifiedTag("+user/other"))
			.isFalse();
	}

	@Test
	void testDeletePrivateUserFailed() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("_user/other");
		other.setName("First");
		userRepository.save(other);

		assertThatThrownBy(() -> userService.delete("_user/other"))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(userRepository.existsByQualifiedTag("_user/other"))
			.isTrue();
		var fetched = userRepository.findOneByQualifiedTag("_user/other").get();
		assertThat(fetched.getTag())
			.isEqualTo("_user/other");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}
}
