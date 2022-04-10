package ca.hc.jasper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.*;

import ca.hc.jasper.IntegrationTest;
import ca.hc.jasper.domain.*;
import ca.hc.jasper.repository.*;
import ca.hc.jasper.repository.filter.RefFilter;
import ca.hc.jasper.errors.AlreadyExistsException;
import ca.hc.jasper.errors.ModifiedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

@WithMockUser("tester")
@IntegrationTest
@Transactional
public class RefServiceIT {

	@Autowired
	RefService refService;

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	UserRepository userRepository;

	static final String URL = "https://www.example.com/";

	@Test
	void testCreateUntaggedRef() {
		var ref = new Ref();
		ref.setUrl(URL);

		refService.create(ref);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
	}

	@Test
	void testCreateDuplicateRefFails() {
		var existing = new Ref();
		existing.setUrl(URL);
		existing.setTags(List.of("user/tester"));
		refRepository.save(existing);
		var ref = new Ref();
		ref.setUrl(URL);

		assertThatThrownBy(() -> refService.create(ref))
			.isInstanceOf(AlreadyExistsException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
	}

	@Test
	void testCreateDuplicateAltRefFails() {
		var existing = new Ref();
		existing.setUrl("https://www.different.com/");
		existing.setTags(List.of("user/tester"));
		existing.setAlternateUrls(List.of(URL));
		refRepository.save(existing);
		var ref = new Ref();
		ref.setUrl(URL);

		assertThatThrownBy(() -> refService.create(ref))
			.isInstanceOf(AlreadyExistsException.class);

		assertThat(refRepository.existsByUrlAndOrigin("https://www.different.com/", ""))
			.isTrue();
	}

	@Test
	void testCreateRefWithPublicTag() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("public"));

		refService.create(ref);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
	}

	@Test
	void testCreateRefWithReadableTags() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("public", "custom", "tags"));

		refService.create(ref);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
	}

	@Test
	void testCreateRefWithUnreadableTagsFails() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("_secret"));

		assertThatThrownBy(() -> refService.create(ref))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isFalse();
	}

	@Test
	void testCreateRefWithPrivateTags() {
		var user = new User();
		user.setTag("user/tester");
		user.setReadAccess(List.of("_secret"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("_secret"));

		refService.create(ref);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		assertThat(refRepository.findOneByUrlAndOrigin(URL, "").get().getTags())
			.containsExactly("_secret");
	}

	@Test
	void testCreateRefWithUserTags() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("user/tester"));

		refService.create(ref);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		assertThat(refRepository.findOneByUrlAndOrigin(URL, "").get().getTags())
			.containsExactly("user/tester");
	}

	@Test
	@WithMockUser(value = "tester", roles = {"USER", "PRIVATE"})
	void testCreateRefWithPrivateUserTags() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("_user/tester"));

		refService.create(ref);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		assertThat(refRepository.findOneByUrlAndOrigin(URL, "").get().getTags())
			.containsExactly("_user/tester");
	}

	@Test
	@WithMockUser(value = "tester", roles = {"USER", "PRIVATE"})
	void testCreateRefWithPrivateUserTagsFailed() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("_user/other"));

		assertThatThrownBy(() -> refService.create(ref))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isFalse();
	}

	@Test
	void testGetPageUntaggedRef() {
		var ref = new Ref();
		ref.setUrl(URL);
		refRepository.save(ref);

		var page = refService.page(
			RefFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	Ref refWithTags(String... tags) {
		var ref = new Ref();
		ref.setUrl(URL + UUID.randomUUID());
		ref.setTags(List.of(tags));
		refRepository.save(ref);
		return ref;
	}

	@Test
	void testGetPageRefWithQuery() {
		refWithTags("public");
		refWithTags("public", "custom", "extra");

		var page = refService.page(
			RefFilter
				.builder()
				.query("custom")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
	}

	@Test
	void testGetPageRefWithQueryPrivateTagFailed() {
		refWithTags("public");
		refWithTags("public", "_custom", "extra");

		assertThatThrownBy(() -> refService.page(
			RefFilter
				.builder()
				.query("_custom")
				.build(),
			PageRequest.of(0, 10)))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testGetPageRefWithNotQuery() {
		refWithTags("public", "custom");
		refWithTags("public", "custom", "extra");

		var page = refService.page(
			RefFilter
				.builder()
				.query("!custom extra")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
	}

	Ref refWithOriginTags(String origin, String... tags) {
		var ref = new Ref();
		ref.setOrigin(origin);
		ref.setUrl(URL + UUID.randomUUID());
		ref.setTags(List.of(tags));
		refRepository.save(ref);
		return ref;
	}

	@Test
	void testGetPageRefWithOriginQuery() {
		refWithOriginTags("@a", "public", "custom");
		refWithOriginTags("@b", "public", "custom", "extra");

		var page = refService.page(
			RefFilter
				.builder()
				.query("custom@a")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
	}

	@Test
	void testGetPageRefWithOriginOrQuery() {
		refWithOriginTags("@a", "public", "custom");
		refWithOriginTags("@b", "public", "custom", "extra");
		refWithOriginTags("@c", "public", "custom", "extra");

		var page = refService.page(
			RefFilter
				.builder()
				.query("custom@a custom@b")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(2);
	}

	@Test
	void testGetPageRefWithOriginOrExtraSpacesQuery() {
		refWithOriginTags("@a", "public", "custom");
		refWithOriginTags("@b", "public", "custom", "extra");
		refWithOriginTags("@c", "public", "custom", "extra");

		var page = refService.page(
			RefFilter
				.builder()
				.query("  custom@a  +  custom@b  : extra@b  ")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(2);
	}

	@Test
	void testGetPageRefWithNotOriginQuery() {
		refWithOriginTags("@a", "public", "custom");
		refWithOriginTags("@b", "public", "custom", "extra");
		refWithOriginTags("@c", "public", "custom", "extra");

		var page = refService.page(
			RefFilter
				.builder()
				.query("!custom@c:extra@*")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
	}

	@Test
	void testGetPageRefWithImpossibleOriginQuery() {
		refWithOriginTags("@a", "public", "custom");
		refWithOriginTags("@b", "public", "custom", "extra");
		refWithOriginTags("@c", "public", "custom", "extra");

		var page = refService.page(
			RefFilter
				.builder()
				.query("custom@a:custom@b")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	void testGetPageRefWithPublicTag() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("public"));
		refRepository.save(ref);

		var page = refService.page(
			RefFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
	}

	@Test
	void testGetPageRefWithReadableTags() {
		var user = new User();
		user.setTag("user/tester");
		user.setReadAccess(List.of("custom"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("custom"));
		refRepository.save(ref);

		var page = refService.page(
			RefFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
	}

	@Test
	void testGetPageRefWithReadablePrivateTags() {
		var user = new User();
		user.setTag("user/tester");
		user.setReadAccess(List.of("_secret", "_hot", "sauce"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("_secret", "_other"));
		refRepository.save(ref);

		var page = refService.page(
			RefFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().get(0).getTags())
			.containsExactly("_secret");
	}

	@Test
	void testGetPageRefWithUnreadablePrivateTags() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("_secret"));
		refRepository.save(ref);

		var page = refService.page(
			RefFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	void testGetPageRefFiltersUnreadablePrivateTags() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("public", "_secret"));
		refRepository.save(ref);

		var page = refService.page(
			RefFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().get(0).getTags())
			.containsExactly("public");
	}

	@Test
	void testUpdateUntaggedRefFailed() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setModified(ref.getModified());

		assertThatThrownBy(() -> refService.update(update))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	void testUpdateRefWithPublicTagFailed() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("public"));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(List.of("public"));
		update.setModified(ref.getModified());

		assertThatThrownBy(() -> refService.update(update))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	void testUpdateRefWithUserTag() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("user/tester"));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(List.of("user/tester"));
		update.setModified(ref.getModified());

		refService.update(update);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Second");
	}

	@Test
	void testUpdateLockedRefFailed() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("locked", "user/tester"));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(List.of("user/tester"));
		update.setModified(ref.getModified());

		assertThatThrownBy(() -> refService.update(update))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	@WithMockUser(value = "tester", roles = "Admin")
	void testAdminUpdateLockedRefFailed() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("locked", "user/tester"));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(List.of("user/tester"));
		update.setModified(ref.getModified());

		assertThatThrownBy(() -> refService.update(update))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	void testUpdateModifiedRefFailed() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("user/tester"));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(List.of("user/tester"));
		update.setModified(ref.getModified().minusSeconds(60));

		assertThatThrownBy(() -> refService.update(update))
			.isInstanceOf(ModifiedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	void testUpdateRefWithReadableTags() {
		var user = new User();
		user.setTag("user/tester");
		user.setReadAccess(List.of("custom"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("custom"));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(List.of("custom"));
		update.setModified(ref.getModified());

		assertThatThrownBy(() -> refService.update(update))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	void testUpdateRefWithWritableTags() {
		var user = new User();
		user.setTag("user/tester");
		user.setWriteAccess(List.of("custom"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("custom"));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(List.of("custom"));
		update.setModified(ref.getModified());

		refService.update(update);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Second");
	}

	@Test
	void testUpdateRefWithUnreadablePrivateTags() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("_secret"));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setModified(ref.getModified());

		assertThatThrownBy(() -> refService.update(update))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	void testUpdateRefWithLoosingHiddenTags() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("user/tester", "_secret"));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(new ArrayList<>(List.of("user/tester", "custom")));
		update.setModified(ref.getModified());

		refService.update(update);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Second");
		assertThat(fetched.getTags())
			.contains("user/tester", "custom", "_secret");
	}

	@Test
	void testUpdateRefWithReadablePrivateTags() {
		var user = new User();
		user.setTag("user/tester");
		user.setReadAccess(List.of("_secret"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("_secret"));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setModified(ref.getModified());

		assertThatThrownBy(() -> refService.update(update))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	void testUpdateRefWithWritablePrivateTags() {
		var user = new User();
		user.setTag("user/tester");
		user.setWriteAccess(List.of("_secret"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("_secret"));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setModified(ref.getModified());

		refService.update(update);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Second");
	}

	@Test
	void testUpdateRefCreatesMetadata() {
		var source = new Ref();
		source.setUrl(URL + "source");
		source.setTitle("Source");
		source.setTags(List.of("user/tester"));
		refService.create(source);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("user/tester"));
		refService.create(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setSources(List.of(URL + "source"));
		update.setTitle("Second");
		update.setTags(List.of("user/tester"));
		update.setModified(ref.getModified());

		refService.update(update);

		assertThat(refRepository.existsByUrlAndOrigin(URL + "source", ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL + "source", "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Source");
		assertThat(fetched.getMetadata().getResponses())
			.containsExactly(URL);
	}

	@Test
	void testUpdateRefCreatesMetadataWithoutInternal() {
		var source = new Ref();
		source.setUrl(URL + "source");
		source.setTitle("Source");
		source.setTags(List.of("user/tester"));
		refService.create(source);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("user/tester", "internal"));
		refService.create(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setSources(List.of(URL + "source"));
		update.setTitle("Second");
		update.setTags(List.of("user/tester", "internal"));
		update.setModified(ref.getModified());

		refService.update(update);

		assertThat(refRepository.existsByUrlAndOrigin(URL + "source", ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL + "source", "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Source");
		assertThat(fetched.getMetadata().getResponses())
			.isEmpty();
	}

	@Test
	void testUpdateRefUpdatesMetadata() {
		var source = new Ref();
		source.setUrl(URL + "source");
		source.setTitle("Source");
		source.setTags(List.of("user/tester"));
		refService.create(source);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setSources(List.of(URL + "source"));
		ref.setTitle("First");
		ref.setTags(List.of("user/tester"));
		refService.create(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(List.of("user/tester"));
		update.setModified(ref.getModified());

		refService.update(update);

		assertThat(refRepository.existsByUrlAndOrigin(URL + "source", ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL + "source", "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Source");
		assertThat(fetched.getMetadata().getResponses())
			.isEmpty();
	}

	@Test
	void testUpdateRefCreatesPluginMetadata() {
		var plugin = new Plugin();
		plugin.setTag("plugin/comment");
		plugin.setGenerateMetadata(true);
		pluginRepository.save(plugin);
		var source = new Ref();
		source.setUrl(URL + "source");
		source.setTitle("Source");
		source.setTags(List.of("user/tester"));
		refService.create(source);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("user/tester", "plugin/comment"));
		refService.create(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setSources(List.of(URL + "source"));
		update.setTitle("Second");
		update.setTags(List.of("user/tester", "plugin/comment"));
		update.setModified(ref.getModified());

		refService.update(update);

		assertThat(refRepository.existsByUrlAndOrigin(URL + "source", ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL + "source", "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Source");
		assertThat(fetched.getMetadata().getResponses())
			.containsExactly(URL);
		assertThat(fetched.getMetadata().getPlugins().get("plugin/comment"))
			.containsExactly(URL);
	}

	@Test
	void testUpdateRefUpdatesPluginMetadata() {
		var plugin = new Plugin();
		plugin.setTag("plugin/comment");
		plugin.setGenerateMetadata(true);
		pluginRepository.save(plugin);
		var source = new Ref();
		source.setUrl(URL + "source");
		source.setTitle("Source");
		source.setTags(List.of("user/tester"));
		refService.create(source);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setSources(List.of(URL + "source"));
		ref.setTitle("First");
		ref.setTags(List.of("user/tester", "plugin/comment"));
		refService.create(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(List.of("user/tester", "plugin/comment"));
		update.setModified(ref.getModified());

		refService.update(update);

		assertThat(refRepository.existsByUrlAndOrigin(URL + "source", ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL + "source", "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Source");
		assertThat(fetched.getMetadata().getResponses())
			.isEmpty();
		assertThat(fetched.getMetadata().getPlugins().get("plugin/comment"))
			.isEmpty();
	}

	@Test
	void testDeleteUntaggedRef() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		refRepository.save(ref);

		assertThatThrownBy(() -> refService.delete(ref.getUrl()))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	void testDeleteRefWithPublicTag() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("public"));
		refRepository.save(ref);

		assertThatThrownBy(() -> refService.delete(ref.getUrl()))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	void testDeleteRefWithUserTag() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("user/tester"));
		refRepository.save(ref);

		refService.delete(ref.getUrl());

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isFalse();
	}

	@Test
	void testDeleteRefWithReadableTags() {
		var user = new User();
		user.setTag("user/tester");
		user.setReadAccess(List.of("custom"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("custom"));
		refRepository.save(ref);

		assertThatThrownBy(() -> refService.delete(ref.getUrl()))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	void testDeleteRefWithWritableTags() {
		var user = new User();
		user.setTag("user/tester");
		user.setWriteAccess(List.of("custom"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("custom"));
		refRepository.save(ref);

		refService.delete(ref.getUrl());

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isFalse();
	}

	@Test
	void testDeleteRefWithUnreadablePrivateTags() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("_secret"));
		refRepository.save(ref);

		assertThatThrownBy(() -> refService.delete(ref.getUrl()))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	void testDeleteRefWithReadablePrivateTags() {
		var user = new User();
		user.setTag("user/tester");
		user.setReadAccess(List.of("_secret"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("_secret"));
		refRepository.save(ref);

		assertThatThrownBy(() -> refService.delete(ref.getUrl()))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	void testDeleteRefWithWritablePrivateTags() {
		var user = new User();
		user.setTag("user/tester");
		user.setWriteAccess(List.of("_secret"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("_secret"));
		refRepository.save(ref);

		refService.delete(ref.getUrl());

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isFalse();
	}

}
