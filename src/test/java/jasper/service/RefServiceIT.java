package jasper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.IntegrationTest;
import jasper.component.Ingest;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.User;
import jasper.errors.AlreadyExistsException;
import jasper.errors.ModifiedException;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.UserRepository;
import jasper.repository.filter.RefFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WithMockUser("+user/tester")
@IntegrationTest
public class RefServiceIT {

	@Autowired
	RefService refService;

	@Autowired
	RefRepository refRepository;

	@Autowired
	Ingest ingest;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	UserRepository userRepository;

	static final String URL = "https://www.example.com/";

	Plugin getPlugin(String tag) {
		var plugin = new Plugin();
		plugin.setTag(tag);
		var mapper = new ObjectMapper();
		try {
			plugin.setSchema((ObjectNode) mapper.readTree("""
			{
				"properties": {
					"name": { "type": "string" },
					"age": { "type": "uint32" }
				}
			}"""));
			plugin.setDefaults(mapper.readTree("""
			{
				"name": "bob",
				"age": 42
			}"""));
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
		return plugin;
	}

	@BeforeEach
	void init() {
		refRepository.deleteAll();
		pluginRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void testCreateUntaggedRef() {
		var ref = new Ref();
		ref.setUrl(URL);

		refService.create(ref, false);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
	}

	@Test
	void testCreateUntaggedRemoteRefFailed() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("@remote");

		assertThatThrownBy(() -> refService.create(ref, false))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testCreateDuplicateRefFails() {
		var existing = new Ref();
		existing.setUrl(URL);
		existing.setTags(new ArrayList<>(List.of("+user/tester")));
		refRepository.save(existing);
		var ref = new Ref();
		ref.setUrl(URL);

		assertThatThrownBy(() -> refService.create(ref, false))
			.isInstanceOf(AlreadyExistsException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
	}

	@Test
	void testCreateDuplicateAltRefPasses() {
		var existing = new Ref();
		existing.setUrl("https://www.different.com/");
		existing.setTags(new ArrayList<>(List.of("+user/tester")));
		existing.setAlternateUrls(List.of(URL));
		refRepository.save(existing);
		var ref = new Ref();
		ref.setUrl(URL);

		refService.create(ref, false);

		assertThat(refRepository.existsByUrlAndOrigin("https://www.different.com/", ""))
			.isTrue();
		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
	}

	@Test
	void testCreateRefWithPublicTag() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(new ArrayList<>(List.of("public")));

		refService.create(ref, false);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
	}

	@Test
	void testCreateRefWithPublicTagRemoteFails() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("@remote");
		ref.setTags(new ArrayList<>(List.of("public")));

		assertThatThrownBy(() -> refService.create(ref, false))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testCreateRefWithReadableTags() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(new ArrayList<>(List.of("public", "custom", "tags")));

		refService.create(ref, false);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
	}

	@Test
	void testCreateRefWithUnreadableTagsFails() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(new ArrayList<>(List.of("_secret")));

		assertThatThrownBy(() -> refService.create(ref, false))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isFalse();
	}

	@Test
	void testCreateRefWithPrivateTags() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_secret"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(new ArrayList<>(List.of("_secret")));

		refService.create(ref, false);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		assertThat(refRepository.findOneByUrlAndOrigin(URL, "").get().getTags())
			.containsExactly("_secret");
	}

	@Test
	void testCreateRefWithUserTags() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(new ArrayList<>(List.of("+user/tester")));

		refService.create(ref, false);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		assertThat(refRepository.findOneByUrlAndOrigin(URL, "").get().getTags())
			.containsExactly("+user/tester", "+user");
	}

	@Test
	@WithMockUser("_user/tester")
	void testCreateRefWithPrivateUserTags() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(new ArrayList<>(List.of("_user/tester")));

		refService.create(ref, false);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		assertThat(refRepository.findOneByUrlAndOrigin(URL, "").get().getTags())
			.containsExactly("_user/tester", "_user");
	}

	@Test
	@WithMockUser("_user/tester")
	void testCreateRefWithPrivateUserTagsFailed() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(new ArrayList<>(List.of("_user/other")));

		assertThatThrownBy(() -> refService.create(ref, false))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isFalse();
	}

	@Test
	void testGetUntaggedRef() {
		var ref = new Ref();
		ref.setUrl(URL);
		refRepository.save(ref);

		assertThatThrownBy(() -> refService.get(ref.getUrl(), ref.getOrigin()))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"MOD"})
	void testGetUntaggedRefMod() {
		var ref = new Ref();
		ref.setUrl(URL);
		refRepository.save(ref);

		var fetch = refService.get(ref.getUrl(), ref.getOrigin());

		assertThat(fetch)
			.isNotNull();
	}

	@Test
	void testGetUntaggedRemoteRef() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("@remote");
		refRepository.save(ref);

		assertThatThrownBy(() -> refService.get(ref.getUrl(), ref.getOrigin()))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"MOD"})
	void testGetUntaggedRemoteRefMod() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("@remote");
		refRepository.save(ref);

		var fetch = refService.get(ref.getUrl(), ref.getOrigin());

		assertThat(fetch)
			.isNotNull();
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

	@Test
	@WithMockUser(value = "+user/tester", roles = {"MOD"})
	void testGetPageUntaggedRef_Mod() {
		var ref = new Ref();
		ref.setUrl(URL);
		refRepository.save(ref);

		var page = refService.page(
			RefFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
	}

	@Test
	void testGetPageUntaggedRemoteRef() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("@remote");
		refRepository.save(ref);

		var page = refService.page(
			RefFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"MOD"})
	void testGetPageUntaggedRemoteRef_Mod() {
		var ref = new Ref();
		ref.setOrigin("@remote");
		ref.setUrl(URL);
		refRepository.save(ref);

		var page = refService.page(
			RefFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"ADMIN"})
	void testGetPageUntaggedRemoteRef_Admin() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("@remote");
		refRepository.save(ref);

		var page = refService.page(
			RefFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
	}

	Ref refWithTags(String... tags) {
		var ref = new Ref();
		ref.setUrl(URL + UUID.randomUUID());
		ref.setTags(new ArrayList<>(List.of(tags)));
		refRepository.save(ref);
		return ref;
	}

	@Test
	void testGetPageRef() {
		refWithTags("public");

		var page = refService.page(
			RefFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
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
	void testGetEmptyPageRefWithEmptyQuery() {
		refWithTags("public");
		refWithTags("public", "custom", "extra");

		var page = refService.page(
			RefFilter
				.builder()
				.query("!@*")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	void testGetEmptyPageRefWithEmptyQueryNoTags() {
		refWithTags();
		refWithTags("public");
		refWithTags("public", "custom", "extra");

		var page = refService.page(
			RefFilter
				.builder()
				.query("!@*")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
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
				.query("!custom | extra")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
	}

	Ref refWithOriginTags(String origin, String... tags) {
		var ref = new Ref();
		ref.setOrigin(origin);
		ref.setUrl(URL + UUID.randomUUID());
		ref.setTags(new ArrayList<>(List.of(tags)));
		refRepository.save(ref);
		return ref;
	}

	@Test
	void testGetPageRemoteRefWithQuery() {
		refWithOriginTags("@remote", "public");
		refWithOriginTags("@remote", "public", "custom", "extra");

		var page = refService.page(
			RefFilter
				.builder()
				.query("custom@*")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
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
	void testGetEmptyPageRefWithOriginEmptyQuery() {
		refWithOriginTags("@a", "public");
		refWithOriginTags("@a", "public", "custom", "extra");

		var page = refService.page(
			RefFilter
				.builder()
				.query("!@*")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	void testGetEmptyPageRefWithOriginEmptyQueryNoTags() {
		refWithOriginTags("@a");
		refWithOriginTags("@b");
		refWithOriginTags("@a", "public");
		refWithOriginTags("@a", "public", "custom", "extra");

		var page = refService.page(
			RefFilter
				.builder()
				.query("!@*")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	void testGetPageRefWithOriginOrQuery() {
		refWithOriginTags("@a", "public", "custom");
		refWithOriginTags("@b", "public", "custom", "extra");
		refWithOriginTags("@c", "public", "custom", "extra");

		var page = refService.page(
			RefFilter
				.builder()
				.query("custom@a | custom@b")
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
				.query("  custom@a  |  custom@b  : extra@b  ")
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
		user.setTag("+user/tester");
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
		user.setTag("+user/tester");
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
	void testGetPageRefWithPlugin() {
		pluginRepository.save(getPlugin("plugin/test"));
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(new ArrayList<>(List.of("public", "plugin/test")));
		ingest.create(ref, false);

		var page = refService.page(
			RefFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().get(0).getTags())
			.containsExactly("public", "plugin/test");
		assertThat(page.getContent().get(0).getPlugins().has("plugin/test"))
			.isTrue();
	}

	@Test
	void testGetPageRefWithUnreadablePlugin() {
		pluginRepository.save(getPlugin("_plugin/test"));
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(new ArrayList<>(List.of("public", "_plugin/test")));
		ingest.create(ref, false);

		var page = refService.page(
			RefFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().get(0).getTags())
			.containsExactly("public");
		assertThat(page.getContent().get(0).getPlugins())
			.isNull();
	}

	@Test
	void testGetPageRefWithFilteredPlugin() {
		pluginRepository.save(getPlugin("plugin/test"));
		pluginRepository.save(getPlugin("_plugin/test"));
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(new ArrayList<>(List.of("public", "plugin/test", "_plugin/test")));
		ingest.create(ref, false);

		var page = refService.page(
			RefFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().get(0).getTags())
			.containsExactly("public", "plugin/test");
		assertThat(page.getContent().get(0).getPlugins().has("plugin/test"))
			.isTrue();
	}

	@Test
	void testGetPageRefResponses() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("public"));
		refRepository.save(ref);
		var response = new Ref();
		var responseURL = URL + UUID.randomUUID();
		response.setUrl(responseURL);
		response.setSources(List.of(URL));
		response.setTags(List.of("public"));
		refRepository.save(response);

		var page = refService.page(
			RefFilter.builder()
				.responses(URL)
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().getFirst().getUrl())
			.isEqualTo(responseURL);
	}

	@Test
	void testGetPageRefSources() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("public"));
		refRepository.save(ref);
		var response = new Ref();
		var responseURL = URL + UUID.randomUUID();
		response.setUrl(responseURL);
		response.setSources(List.of(URL));
		response.setTags(List.of("public"));
		refService.create(response, false);

		var page = refService.page(
			RefFilter.builder()
				.sources(responseURL)
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().getFirst().getUrl())
			.isEqualTo(URL);
	}

	@Test
	void testGetPageRefScheme() {
		var ref1 = new Ref();
		ref1.setUrl(URL);
		ref1.setTags(List.of("public"));
		refRepository.save(ref1);
		var ref2 = new Ref();
		ref2.setUrl("other:test");
		ref2.setTags(List.of("public"));
		refRepository.save(ref2);

		var page = refService.page(
			RefFilter.builder()
				.scheme("other:")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().getFirst().getUrl())
			.isEqualTo("other:test");
	}

	@Test
	void testGetPageRefSearch() {
		var ref1 = new Ref();
		ref1.setUrl(URL);
		ref1.setTitle("Yes");
		ref1.setTags(List.of("public"));
		refRepository.save(ref1);
		var ref2 = new Ref();
		ref1.setTitle("No");
		ref2.setUrl(URL + "2");
		ref2.setTags(List.of("public"));
		refRepository.save(ref2);

		var page = refService.page(
			RefFilter.builder()
				.search("yes")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().getFirst().getUrl())
			.isEqualTo(URL);
	}

	@Test
	void testGetPageRefEndsTitle() {
		var ref1 = new Ref();
		ref1.setUrl(URL);
		ref1.setTitle("Title");
		ref1.setTags(List.of("public"));
		refRepository.save(ref1);
		var ref2 = new Ref();
		ref1.setTitle("Other");
		ref2.setUrl(URL + "2");
		ref2.setTags(List.of("public"));
		refRepository.save(ref2);

		var page = refService.page(
			RefFilter.builder()
				.endsTitle("Re: Title")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().getFirst().getUrl())
			.isEqualTo(URL);
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

		assertThatThrownBy(() -> refService.update(update, false))
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
		ref.setTags(new ArrayList<>(List.of("public")));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(new ArrayList<>(List.of("public")));
		update.setModified(ref.getModified());

		assertThatThrownBy(() -> refService.update(update, false))
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
		ref.setTags(new ArrayList<>(List.of("+user/tester")));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(new ArrayList<>(List.of("+user/tester")));
		update.setModified(ref.getModified());

		refService.update(update, false);

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
		ref.setTags(new ArrayList<>(List.of("locked", "+user/tester")));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(new ArrayList<>(List.of("+user/tester")));
		update.setModified(ref.getModified());

		assertThatThrownBy(() -> refService.update(update, false))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = "Admin")
	void testAdminUpdateLockedRefFailed() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("locked", "+user/tester")));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(new ArrayList<>(List.of("+user/tester")));
		update.setModified(ref.getModified());

		assertThatThrownBy(() -> refService.update(update, false))
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
		ref.setTags(new ArrayList<>(List.of("+user/tester")));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(new ArrayList<>(List.of("+user/tester")));
		update.setModified(ref.getModified().minusSeconds(60));

		assertThatThrownBy(() -> refService.update(update, false))
			.isInstanceOf(ModifiedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	void testUpdateRefWithReadableTagsFailed() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("custom"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("custom")));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(new ArrayList<>(List.of("custom")));
		update.setModified(ref.getModified());

		assertThatThrownBy(() -> refService.update(update, false))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	void testUpdateRemoteRefFailed() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("+custom"));
		user.setWriteAccess(List.of("+custom"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("@remote");
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("+custom")));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setOrigin("@remote");
		update.setTitle("Second");
		update.setTags(new ArrayList<>(List.of("+custom")));
		update.setModified(ref.getModified());

		assertThatThrownBy(() -> refService.update(update, false))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, "@remote"))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "@remote").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	void testUpdateRefWithWritableTags() {
		var user = new User();
		user.setTag("+user/tester");
		user.setWriteAccess(List.of("+custom"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("+custom")));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(new ArrayList<>(List.of("+custom")));
		update.setModified(ref.getModified());

		refService.update(update, false);

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
		ref.setTags(new ArrayList<>(List.of("_secret")));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setModified(ref.getModified());

		assertThatThrownBy(() -> refService.update(update, false))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	void testUpdateRefWithoutLoosingHiddenTags() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("+user/tester", "_secret")));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(new ArrayList<>(List.of("+user/tester", "custom")));
		update.setModified(ref.getModified());

		refService.update(update, false);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Second");
		assertThat(fetched.getTags())
			.contains("+user/tester", "custom", "_secret");
	}

	@Test
	void testUpdateRefWithoutLoosingHiddenPlugins() {
		pluginRepository.save(getPlugin("_plugin/test"));
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("+user/tester", "_plugin/test")));
		ingest.create(ref, false);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(new ArrayList<>(List.of("+user/tester", "custom")));
		update.setModified(ref.getModified());

		refService.update(update, false);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Second");
		assertThat(fetched.getTags())
			.contains("+user/tester", "custom", "_plugin/test");
		assertThat(fetched.getPlugins().has("_plugin/test"))
			.isTrue();
	}

	@Test
	void testUpdateRefWithoutLoosingProtectedPluginData() throws JsonProcessingException {
		pluginRepository.save(getPlugin("+plugin/test"));
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("+user/tester", "+plugin/test")));
		var mapper = new ObjectMapper();
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"+plugin/test": {
				"name": "bob",
				"age": 42
			}
		}"""));
		ingest.create(ref, false);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(new ArrayList<>(List.of("+user/tester", "+plugin/test", "custom")));
		update.setPlugins((ObjectNode) mapper.readTree("""
		{
			"+plugin/test": {
				"name": "charlie",
				"age": 100
			}
		}"""));
		update.setModified(ref.getModified());

		refService.update(update, false);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Second");
		assertThat(fetched.getTags())
			.contains("+user/tester", "custom", "+plugin/test");
		assertThat(fetched.getPlugins().has("+plugin/test"))
			.isTrue();
		var plugin = fetched.getPlugin("+plugin/test");
		assertThat(plugin.get("name").textValue())
			.isEqualTo("bob");
		assertThat(plugin.get("age").numberValue())
			.isEqualTo(42);
	}

	@Test
	void testUpdateRefWithReadablePrivateTags() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_secret"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("_secret")));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setModified(ref.getModified());

		assertThatThrownBy(() -> refService.update(update, false))
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
		user.setTag("+user/tester");
		user.setWriteAccess(List.of("_secret"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("_secret")));
		refRepository.save(ref);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setModified(ref.getModified());

		refService.update(update, false);

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
		source.setTags(new ArrayList<>(List.of("+user/tester")));
		refService.create(source, false);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("+user/tester")));
		refService.create(ref, false);
		var update = new Ref();
		update.setUrl(URL);
		update.setSources(List.of(URL + "source"));
		update.setTitle("Second");
		update.setTags(new ArrayList<>(List.of("+user/tester")));
		update.setModified(ref.getModified());

		refService.update(update, false);

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
		source.setTags(new ArrayList<>(List.of("+user/tester")));
		refService.create(source, false);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("+user/tester", "internal")));
		refService.create(ref, false);
		var update = new Ref();
		update.setUrl(URL);
		update.setSources(List.of(URL + "source"));
		update.setTitle("Second");
		update.setTags(new ArrayList<>(List.of("+user/tester", "internal")));
		update.setModified(ref.getModified());

		refService.update(update, false);

		assertThat(refRepository.existsByUrlAndOrigin(URL + "source", ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL + "source", "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Source");
		assertThat(fetched.getMetadata().getResponses())
			.isNull();
	}

	@Test
	void testUpdateRefUpdatesMetadata() {
		var source = new Ref();
		source.setUrl(URL + "source");
		source.setTitle("Source");
		source.setTags(new ArrayList<>(List.of("+user/tester")));
		refService.create(source, false);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setSources(List.of(URL + "source"));
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("+user/tester")));
		refService.create(ref, false);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(new ArrayList<>(List.of("+user/tester")));
		update.setModified(ref.getModified());

		refService.update(update, false);

		assertThat(refRepository.existsByUrlAndOrigin(URL + "source", ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL + "source", "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Source");
		assertThat(fetched.getMetadata().getResponses())
			.isNull();
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
		source.setTags(new ArrayList<>(List.of("+user/tester")));
		refService.create(source, false);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("+user/tester", "plugin/comment")));
		refService.create(ref, false);
		var update = new Ref();
		update.setUrl(URL);
		update.setSources(List.of(URL + "source"));
		update.setTitle("Second");
		update.setTags(new ArrayList<>(List.of("+user/tester", "plugin/comment")));
		update.setModified(ref.getModified());

		refService.update(update, false);

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
		source.setTags(new ArrayList<>(List.of("+user/tester")));
		refService.create(source, false);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setSources(List.of(URL + "source"));
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("+user/tester", "plugin/comment")));
		refService.create(ref, false);
		var update = new Ref();
		update.setUrl(URL);
		update.setTitle("Second");
		update.setTags(new ArrayList<>(List.of("+user/tester", "plugin/comment")));
		update.setModified(ref.getModified());

		refService.update(update, false);

		assertThat(refRepository.existsByUrlAndOrigin(URL + "source", ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL + "source", "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Source");
		assertThat(fetched.getMetadata().getResponses())
			.isNull();
		assertThat(fetched.getMetadata().getPlugins().get("plugin/comment"))
			.isEmpty();
	}

	@Test
	void testDeleteUntaggedRef() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		refRepository.save(ref);

		assertThatThrownBy(() -> refService.delete(ref.getUrl(), ref.getOrigin()))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	void testDeleteRemoteRefFailed() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setOrigin("@remote");
		refRepository.save(ref);

		assertThatThrownBy(() -> refService.delete(ref.getUrl(), ref.getOrigin()))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, "@remote"))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "@remote").get();
		assertThat(fetched.getTitle())
			.isEqualTo("First");
	}

	@Test
	void testDeleteRefWithPublicTag() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("public")));
		refRepository.save(ref);

		assertThatThrownBy(() -> refService.delete(ref.getUrl(), ref.getOrigin()))
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
		ref.setTags(new ArrayList<>(List.of("+user/tester")));
		refRepository.save(ref);

		refService.delete(ref.getUrl(), ref.getOrigin());

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isFalse();
	}

	@Test
	void testDeleteRefWithReadableTags() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("custom"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("custom")));
		refRepository.save(ref);

		assertThatThrownBy(() -> refService.delete(ref.getUrl(), ref.getOrigin()))
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
		user.setTag("+user/tester");
		user.setWriteAccess(List.of("+custom"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("+custom")));
		refRepository.save(ref);

		refService.delete(ref.getUrl(), ref.getOrigin());

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isFalse();
	}

	@Test
	void testDeleteRefWithUnreadablePrivateTags() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("_secret")));
		refRepository.save(ref);

		assertThatThrownBy(() -> refService.delete(ref.getUrl(), ref.getOrigin()))
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
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_secret"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("_secret")));
		refRepository.save(ref);

		assertThatThrownBy(() -> refService.delete(ref.getUrl(), ref.getOrigin()))
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
		user.setTag("+user/tester");
		user.setWriteAccess(List.of("_secret"));
		userRepository.save(user);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(List.of("_secret")));
		refRepository.save(ref);

		refService.delete(ref.getUrl(), ref.getOrigin());

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isFalse();
	}

}
