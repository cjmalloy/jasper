package ca.hc.jasper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import ca.hc.jasper.IntegrationTest;
import ca.hc.jasper.domain.User;
import ca.hc.jasper.repository.UserRepository;
import ca.hc.jasper.repository.filter.TagFilter;
import ca.hc.jasper.service.errors.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

@WithMockUser("tester")
@IntegrationTest
@Transactional
public class UserServiceIT {

	@Autowired
	UserService userService;

	@Autowired
	UserRepository userRepository;

	@Test
	void testCreateUser() {
		var user = new User();
		user.setTag("user/tester");
		user.setName("Custom");

		userService.create(user);

		assertThat(userRepository.existsByTagAndOrigin("user/tester", ""))
			.isTrue();
		assertThat(userRepository.findOneByTagAndOrigin("user/tester", "").get().getTag())
			.isEqualTo("user/tester");
		assertThat(userRepository.findOneByTagAndOrigin("user/tester", "").get().getName())
			.isEqualTo("Custom");
	}

	@Test
	void testCreateUserFailed() {
		var user = new User();
		user.setTag("user/other");
		user.setName("Custom");

		assertThatThrownBy(() -> userService.create(user))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(userRepository.existsByTagAndOrigin("user/other", ""))
			.isFalse();
	}

	@Test
	@WithMockUser(value = "tester", roles = "MOD")
	void testModCreateUser() {
		var user = new User();
		user.setTag("user/other");
		user.setName("Custom");

		userService.create(user);

		assertThat(userRepository.existsByTagAndOrigin("user/other", ""))
			.isTrue();
		assertThat(userRepository.findOneByTagAndOrigin("user/other", "").get().getTag())
			.isEqualTo("user/other");
		assertThat(userRepository.findOneByTagAndOrigin("user/other", "").get().getName())
			.isEqualTo("Custom");
	}

	@Test
	@WithMockUser(value = "tester", roles = "MOD")
	void testModCreateUserWithTags() {
		var user = new User();
		user.setTag("user/other");
		user.setName("Custom");
		user.setReadAccess(List.of("custom"));
		user.setWriteAccess(List.of("custom"));

		userService.create(user);

		assertThat(userRepository.existsByTagAndOrigin("user/other", ""))
			.isTrue();
		assertThat(userRepository.findOneByTagAndOrigin("user/other", "").get().getTag())
			.isEqualTo("user/other");
		assertThat(userRepository.findOneByTagAndOrigin("user/other", "").get().getName())
			.isEqualTo("Custom");
		assertThat(userRepository.findOneByTagAndOrigin("user/other", "").get().getReadAccess())
			.containsExactly("custom");
		assertThat(userRepository.findOneByTagAndOrigin("user/other", "").get().getWriteAccess())
			.containsExactly("custom");
	}

	@Test
	@WithMockUser(value = "tester", roles = "MOD")
	void testModCreateUserWithPrivateTags() {
		var user = new User();
		user.setTag("user/other");
		user.setName("Custom");
		user.setReadAccess(List.of("_custom"));
		user.setWriteAccess(List.of("_custom"));

		userService.create(user);

		assertThat(userRepository.existsByTagAndOrigin("user/other", ""))
			.isTrue();
		assertThat(userRepository.findOneByTagAndOrigin("user/other", "").get().getTag())
			.isEqualTo("user/other");
		assertThat(userRepository.findOneByTagAndOrigin("user/other", "").get().getName())
			.isEqualTo("Custom");
		assertThat(userRepository.findOneByTagAndOrigin("user/other", "").get().getReadAccess())
			.containsExactly("_custom");
		assertThat(userRepository.findOneByTagAndOrigin("user/other", "").get().getWriteAccess())
			.containsExactly("_custom");
	}

	@Test
	void testCreateUserWithTags() {
		var user = new User();
		user.setTag("user/tester");
		user.setReadAccess(List.of("custom", "user/other"));
		user.setWriteAccess(List.of("custom", "user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("user/other");
		other.setName("Custom");
		other.setReadAccess(List.of("custom"));
		other.setWriteAccess(List.of("custom"));

		userService.create(other);

		assertThat(userRepository.existsByTagAndOrigin("user/other", ""))
			.isTrue();
		assertThat(userRepository.findOneByTagAndOrigin("user/other", "").get().getTag())
			.isEqualTo("user/other");
		assertThat(userRepository.findOneByTagAndOrigin("user/other", "").get().getName())
			.isEqualTo("Custom");
		assertThat(userRepository.findOneByTagAndOrigin("user/other", "").get().getReadAccess())
			.containsExactly("custom");
		assertThat(userRepository.findOneByTagAndOrigin("user/other", "").get().getWriteAccess())
			.containsExactly("custom");
	}

	@Test
	void testCreateUserWithPrivateTags() {
		var user = new User();
		user.setTag("user/tester");
		user.setReadAccess(List.of("_secret", "user/other"));
		user.setWriteAccess(List.of("_secret", "user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("user/other");
		other.setName("Custom");
		other.setReadAccess(List.of("_secret"));
		other.setWriteAccess(List.of("_secret"));

		userService.create(other);

		assertThat(userRepository.existsByTagAndOrigin("user/other", ""))
			.isTrue();
		assertThat(userRepository.findOneByTagAndOrigin("user/other", "").get().getTag())
			.isEqualTo("user/other");
		assertThat(userRepository.findOneByTagAndOrigin("user/other", "").get().getName())
			.isEqualTo("Custom");
		assertThat(userRepository.findOneByTagAndOrigin("user/other", "").get().getReadAccess())
			.containsExactly("_secret");
		assertThat(userRepository.findOneByTagAndOrigin("user/other", "").get().getWriteAccess())
			.containsExactly("_secret");
	}

	@Test
	void testCreateUserWithTagsFailed() {
		var other = new User();
		other.setTag("user/other");
		other.setName("Custom");
		other.setReadAccess(List.of("custom"));
		other.setWriteAccess(List.of("custom"));

		assertThatThrownBy(() -> userService.create(other))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(userRepository.existsByTagAndOrigin("user/other", ""))
			.isFalse();
	}

	@Test
	void testCreateUserWithPrivateTagsFailed() {
		var other = new User();
		other.setTag("user/other");
		other.setName("Custom");
		other.setReadAccess(List.of("_custom"));
		other.setWriteAccess(List.of("_custom"));

		assertThatThrownBy(() -> userService.create(other))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(userRepository.existsByTagAndOrigin("user/other", ""))
			.isFalse();
	}

	@Test
	void testReadNonExistentUser() {
		assertThatThrownBy(() -> userService.get("user/other", ""))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void testReadNonExistentPrivateUser() {
		assertThatThrownBy(() -> userService.get("_user/other", ""))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void testReadPublicUser() {
		var user = new User();
		user.setTag("user/other");
		user.setName("Custom");
		userRepository.save(user);

		var fetched = userService.get("user/other", "");

		assertThat(fetched.getTag())
			.isEqualTo("user/other");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	void testReadUser() {
		var user = new User();
		user.setTag("user/other");
		user.setName("Custom");
		userRepository.save(user);

		var fetched = userService.get("user/other", "");

		assertThat(fetched.getTag())
			.isEqualTo("user/other");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	void testReadUserSelf() {
		var user = new User();
		user.setTag("user/tester");
		user.setName("Custom");
		userRepository.save(user);

		var fetched = userService.get("user/tester", "");

		assertThat(fetched.getTag())
			.isEqualTo("user/tester");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	void testReadPrivateUserFailed() {
		var user = new User();
		user.setTag("_user/other");
		user.setName("Secret");
		userRepository.save(user);

		assertThatThrownBy(() -> userService.get("_user/other", ""))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testReadPrivateOtherUser() {
		var user = new User();
		user.setTag("user/tester");
		user.setReadAccess(List.of("_user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("_user/other");
		other.setName("Secret");
		userRepository.save(other);

		var fetched = userService.get("_user/other", "");

		assertThat(fetched.getTag())
			.isEqualTo("_user/other");
		assertThat(fetched.getName())
			.isEqualTo("Secret");
	}

	@Test
	@WithMockUser(value = "tester", roles = {"USER", "PRIVATE"})
	void testReadPrivateUser() {
		var user = new User();
		user.setTag("_user/tester");
		user.setName("Secret");
		userRepository.save(user);

		var fetched = userService.get("_user/tester", "");

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

		assertThatThrownBy(() -> userService.get("_user/other", ""))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testPagePublicUser() {
		var user = new User();
		user.setTag("user/other");
		user.setName("Custom");
		userRepository.save(user);

		var page = userService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10, Sort.by("tag")));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().get(0).getTag())
			.isEqualTo("user/other");
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
			PageRequest.of(0, 10, Sort.by("tag")));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	void testPagePrivateUser() {
		var user = new User();
		user.setTag("user/tester");
		user.setName("Custom");
		user.setReadAccess(List.of("_user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("_user/other");
		other.setName("Secret");
		userRepository.save(other);

		var page = userService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10, Sort.by("tag")));

		assertThat(page.getTotalElements())
			.isEqualTo(2);
		assertThat(page.getContent().get(0).getTag())
			.isIn("user/tester", "_user/other");
		assertThat(page.getContent().get(0).getName())
			.isIn("Custom", "Secret");
		assertThat(page.getContent().get(1).getTag())
			.isIn("user/tester", "_user/other");
		assertThat(page.getContent().get(1).getName())
			.isIn("Custom", "Secret");
	}

	@Test
	void testPagePrivateUserWithoutHiddenTags() {
		var user = new User();
		user.setTag("user/tester");
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
			PageRequest.of(0, 10, Sort.by("tag")));

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
		user.setTag("user/tester");
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
			PageRequest.of(0, 10, Sort.by("tag")));

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
	@WithMockUser(value = "tester", roles = {"USER", "PRIVATE"})
	void testPagePrivateUserUser() {
		var user = new User();
		user.setTag("_user/tester");
		user.setName("Secret");
		userRepository.save(user);

		var page = userService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10, Sort.by("tag")));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().get(0).getTag())
			.isEqualTo("_user/tester");
		assertThat(page.getContent().get(0).getName())
			.isEqualTo("Secret");
	}

	@Test
	@WithMockUser(value = "tester", roles = {"USER", "PRIVATE"})
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
		user.setTag("user/tester");
		user.setName("Secret");
		userRepository.save(user);

		var page = userService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().get(0).getTag())
			.isEqualTo("user/tester");
		assertThat(page.getContent().get(0).getName())
			.isEqualTo("Secret");
	}

	@Test
	void testUpdateOtherUserWithoutRemovingHiddenTags() {
		var user = new User();
		user.setTag("user/tester");
		user.setWriteAccess(List.of("user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("user/other");
		other.setName("First");
		other.setReadAccess(List.of("_secret"));
		userRepository.save(other);
		var updated = new User();
		updated.setTag("user/other");
		updated.setName("Second");

		userService.update(updated);

		var fetched = userRepository.findOneByTagAndOrigin("user/other", "").get();
		assertThat(fetched.getTag())
			.isEqualTo("user/other");
		assertThat(fetched.getName())
			.isEqualTo("Second");
		assertThat(fetched.getReadAccess())
			.containsExactly("_secret");
	}

	@Test
	void testUpdateOtherUserAddingTags() {
		var user = new User();
		user.setTag("user/tester");
		user.setWriteAccess(List.of("user/other", "custom"));
		userRepository.save(user);
		var other = new User();
		other.setTag("user/other");
		other.setName("First");
		other.setReadAccess(List.of("_secret"));
		userRepository.save(other);
		var updated = new User();
		updated.setTag("user/other");
		updated.setName("Second");
		updated.setReadAccess(new ArrayList<>(List.of("custom")));

		userService.update(updated);

		var fetched = userRepository.findOneByTagAndOrigin("user/other", "").get();
		assertThat(fetched.getTag())
			.isEqualTo("user/other");
		assertThat(fetched.getName())
			.isEqualTo("Second");
		assertThat(fetched.getReadAccess())
			.contains("custom", "_secret");
	}

	@Test
	void testUpdateOtherUser() {
		var user = new User();
		user.setTag("user/tester");
		user.setWriteAccess(List.of("user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("user/other");
		other.setName("First");
		userRepository.save(other);
		var updated = new User();
		updated.setTag("user/other");
		updated.setName("Second");

		userService.update(updated);

		var fetched = userService.get("user/other", "");
		assertThat(fetched.getTag())
			.isEqualTo("user/other");
		assertThat(fetched.getName())
			.isEqualTo("Second");
	}

	@Test
	void testUpdateOtherUserFailed() {
		var user = new User();
		user.setTag("user/tester");
		user.setReadAccess(List.of("user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("user/other");
		other.setName("First");
		userRepository.save(other);
		var updated = new User();
		updated.setTag("user/other");
		updated.setName("Second");

		assertThatThrownBy(() -> userService.update(updated))
			.isInstanceOf(AccessDeniedException.class);

		var fetched = userService.get("user/other", "");
		assertThat(fetched.getTag())
			.isEqualTo("user/other");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testUpdateUser() {
		var user = new User();
		user.setTag("user/tester");
		user.setName("First");
		userRepository.save(user);
		var updated = new User();
		updated.setTag("user/tester");
		updated.setName("Second");

		userService.update(updated);

		var fetched = userService.get("user/tester", "");
		assertThat(fetched.getTag())
			.isEqualTo("user/tester");
		assertThat(fetched.getName())
			.isEqualTo("Second");
	}

	@Test
	void testUpdateUserFailed() {
		var user = new User();
		user.setTag("user/other");
		user.setName("First");
		userRepository.save(user);
		var updated = new User();
		updated.setTag("user/other");
		updated.setName("Second");

		assertThatThrownBy(() -> userService.update(updated))
			.isInstanceOf(AccessDeniedException.class);

		var fetched = userService.get("user/other", "");
		assertThat(fetched.getTag())
			.isEqualTo("user/other");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testUpdatePrivateUser() {
		var user = new User();
		user.setTag("user/tester");
		user.setReadAccess(List.of("_user/other"));
		user.setWriteAccess(List.of("_user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("_user/other");
		other.setName("First");
		userRepository.save(other);
		var updated = new User();
		updated.setTag("_user/other");
		updated.setName("Second");

		userService.update(updated);

		var fetched = userService.get("_user/other", "");
		assertThat(fetched.getTag())
			.isEqualTo("_user/other");
		assertThat(fetched.getName())
			.isEqualTo("Second");
	}

	@Test
	void testUpdatePrivateUserFailed() {
		var user = new User();
		user.setTag("user/tester");
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

		var fetched = userService.get("_user/other", "");
		assertThat(fetched.getTag())
			.isEqualTo("_user/other");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testDeleteUser() {
		var user = new User();
		user.setTag("user/tester");
		user.setWriteAccess(List.of("user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("user/other");
		other.setName("First");
		userRepository.save(other);

		userService.delete("user/other");

		assertThatThrownBy(() -> userService.get("user/other", ""))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void testDeleteUserFailed() {
		var user = new User();
		user.setTag("user/tester");
		user.setReadAccess(List.of("user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("user/other");
		other.setName("First");
		userRepository.save(other);

		assertThatThrownBy(() -> userService.delete("user/other"))
			.isInstanceOf(AccessDeniedException.class);

		var fetched = userService.get("user/other", "");
		assertThat(fetched.getTag())
			.isEqualTo("user/other");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testDeletePrivateUser() {
		var user = new User();
		user.setTag("user/tester");
		user.setReadAccess(List.of("user/other"));
		user.setWriteAccess(List.of("user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("user/other");
		other.setName("First");
		userRepository.save(other);

		userService.delete("user/other");

		assertThatThrownBy(() -> userService.get("user/other", ""))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void testDeletePrivateUserFailed() {
		var user = new User();
		user.setTag("user/tester");
		user.setReadAccess(List.of("_user/other"));
		userRepository.save(user);
		var other = new User();
		other.setTag("_user/other");
		other.setName("First");
		userRepository.save(other);

		assertThatThrownBy(() -> userService.delete("_user/other"))
			.isInstanceOf(AccessDeniedException.class);

		var fetched = userService.get("_user/other", "");
		assertThat(fetched.getTag())
			.isEqualTo("_user/other");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}
}
