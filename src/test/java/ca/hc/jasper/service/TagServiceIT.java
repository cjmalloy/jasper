package ca.hc.jasper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import ca.hc.jasper.IntegrationTest;
import ca.hc.jasper.domain.Tag;
import ca.hc.jasper.domain.User;
import ca.hc.jasper.repository.TagRepository;
import ca.hc.jasper.repository.UserRepository;
import ca.hc.jasper.service.errors.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

@WithMockUser("tester")
@IntegrationTest
@Transactional
public class TagServiceIT {

	@Autowired
	TagService tagService;

	@Autowired
	TagRepository tagRepository;

	@Autowired
	UserRepository userRepository;

	@Test
	void testCreateTag() {
		var tag = new Tag();
		tag.setTag("custom");
		tag.setName("Custom");

		assertThatThrownBy(() -> tagService.create(tag))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testReadNonExistentTag() {
		assertThatThrownBy(() -> tagService.get("custom", ""))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void testReadPublicTag() {
		var tag = new Tag();
		tag.setTag("custom");
		tag.setName("Custom");
		tagRepository.save(tag);

		var fetched = tagService.get("custom", "");

		assertThat(fetched.getTag())
			.isEqualTo("custom");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	void testReadPrivateTagFail() {
		var tag = new Tag();
		tag.setTag("_secret");
		tag.setName("Secret");
		tagRepository.save(tag);

		assertThatThrownBy(() -> tagService.get("_secret", ""))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testReadPrivateTag() {
		var user = new User();
		user.setTag("user/tester");
		user.setReadAccess(List.of("_secret"));
		userRepository.save(user);
		var tag = new Tag();
		tag.setTag("_secret");
		tag.setName("Secret");
		tagRepository.save(tag);

		var fetched = tagService.get("_secret", "");

		assertThat(fetched.getTag())
			.isEqualTo("_secret");
		assertThat(fetched.getName())
			.isEqualTo("Secret");
	}

	@Test
	void testPagePublicTag() {
		var tag = new Tag();
		tag.setTag("custom");
		tag.setName("Custom");
		tagRepository.save(tag);

		var page = tagService.page(PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().get(0).getTag())
			.isEqualTo("custom");
		assertThat(page.getContent().get(0).getName())
			.isEqualTo("Custom");
	}

	@Test
	void testPagePrivateTagHidden() {
		var tag = new Tag();
		tag.setTag("_secret");
		tag.setName("Secret");
		tagRepository.save(tag);

		var page = tagService.page(PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	void testPagePrivateTag() {
		var user = new User();
		user.setTag("user/tester");
		user.setReadAccess(List.of("_secret"));
		userRepository.save(user);
		var tag = new Tag();
		tag.setTag("_secret");
		tag.setName("Secret");
		tagRepository.save(tag);

		var page = tagService.page(PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().get(0).getTag())
			.isEqualTo("_secret");
		assertThat(page.getContent().get(0).getName())
			.isEqualTo("Secret");
	}

	//TODO: more tests
}
