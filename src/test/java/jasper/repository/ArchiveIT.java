package jasper.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.IntegrationTest;
import jasper.component.Ingest;
import jasper.component.IngestExt;
import jasper.component.IngestPlugin;
import jasper.component.IngestTemplate;
import jasper.component.IngestUser;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.domain.proj.RefView;
import jasper.domain.proj.Tag;
import jasper.errors.AlreadyExistsException;
import jasper.repository.filter.RefFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static jasper.component.Replicator.deletorTag;
import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.RefSpec.isUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.data.domain.Sort.by;

@IntegrationTest
@ActiveProfiles({"archive", "test"})
// Use a separate database since the archive migration is one-way
@TestPropertySource(properties = "spring.datasource.url=jdbc:tc:postgresql:14.2:///jasper-archive?TC_TMPFS=/testtmpfs:rw")
@DisabledIfSystemProperty(named = "spring.profiles.active", matches = ".*sqlite.*")
public class ArchiveIT {
	static final String URL = "https://www.example.com/";

	@Autowired
	Ingest ingest;

	@Autowired
	RefRepository refRepository;

	@Autowired
	BackfillRepository backfillRepository;

	@Autowired
	ExtRepository extRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	TemplateRepository templateRepository;

	@Autowired
	IngestExt ingestExt;

	@Autowired
	IngestUser ingestUser;

	@Autowired
	IngestPlugin ingestPlugin;

	@Autowired
	IngestTemplate ingestTemplate;

	@Autowired
	PlatformTransactionManager transactionManager;

	Instant now;

	@BeforeEach
	void init() {
		refRepository.deleteAll();
		extRepository.deleteAll();
		userRepository.deleteAll();
		pluginRepository.deleteAll();
		templateRepository.deleteAll();
		now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
	}

	Ref ref(String origin, String title, Instant modified, String ...tags) {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin(origin);
		ref.setTitle(title);
		ref.setTags(new ArrayList<>(List.of(tags)));
		ref.setModified(modified);
		return ref;
	}

	void push(String origin, String title, Instant modified, String ...tags) {
		ingest.push("", ref(origin, title, modified, tags), false, false);
	}

	Ref version(String origin, Instant modified) {
		return refRepository.findAll(isUrl(URL).and(isOrigin(origin))).stream()
			.filter(r -> r.getModified().equals(modified))
			.findFirst()
			.orElseThrow();
	}

	Ext ext(String name, Instant modified) {
		var ext = new Ext();
		ext.setTag("test");
		ext.setName(name);
		ext.setModified(modified);
		return ext;
	}

	@Test
	void testPushNewerVersionMarksOlderObsolete() {
		push("", "First", now.minusSeconds(10));
		push("", "Second", now);

		assertThat(refRepository.count())
			.isEqualTo(2);
		assertThat(version("", now.minusSeconds(10)).getMetadata().isObsolete())
			.isTrue();
		assertThat(version("", now).getMetadata().isObsolete())
			.isFalse();
	}

	@Test
	void testFindOneReturnsNewestVersion() {
		push("", "First", now.minusSeconds(10));
		push("", "Second", now);

		assertThat(refRepository.findOneByUrlAndOrigin(URL, ""))
			.get()
			.extracting(Ref::getTitle)
			.isEqualTo("Second");
		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
	}

	@Test
	void testPushOutOfOrder() {
		push("", "Second", now);
		push("", "First", now.minusSeconds(10));

		assertThat(refRepository.count())
			.isEqualTo(2);
		assertThat(refRepository.findOneByUrlAndOrigin(URL, ""))
			.get()
			.extracting(Ref::getTitle)
			.isEqualTo("Second");
		assertThat(version("", now).getMetadata().isObsolete())
			.isFalse();
		assertThat(version("", now.minusSeconds(10)).getMetadata().isObsolete())
			.isTrue();
	}

	@Test
	void testRepeatedPushUpdatesSameVersion() {
		push("", "First", now.minusSeconds(10));
		push("", "Second", now);
		push("", "Second Edited", now);

		assertThat(refRepository.count())
			.isEqualTo(2);
		assertThat(version("", now).getTitle())
			.isEqualTo("Second Edited");
		assertThat(version("", now.minusSeconds(10)).getTitle())
			.isEqualTo("First");
	}

	@Test
	void testDeleteNoticeBecomesCurrentVersion() {
		push("", "First", now.minusSeconds(20));
		push("", "Second", now.minusSeconds(10));
		push("", "", now, "internal", "plugin/delete");

		assertThat(refRepository.count())
			.isEqualTo(3);
		assertThat(refRepository.findOneByUrlAndOrigin(URL, ""))
			.get()
			.satisfies(r -> assertThat(r.getTags()).containsExactly("internal", "plugin/delete"))
			.satisfies(r -> assertThat(r.getMetadata().isObsolete()).isFalse());
		assertThat(version("", now.minusSeconds(20)).getMetadata().isObsolete())
			.isTrue();
		assertThat(version("", now.minusSeconds(10)).getMetadata().isObsolete())
			.isTrue();
	}

	@Test
	void testObsoleteFilterExcludesOlderVersions() {
		push("", "First", now.minusSeconds(10));
		push("", "Second", now);

		assertThat(refRepository.findAll(RefFilter.builder().obsolete(false).build().spec()))
			.extracting(Ref::getTitle)
			.containsExactly("Second");
		assertThat(refRepository.findAll(RefFilter.builder().obsolete(true).build().spec()))
			.extracting(Ref::getTitle)
			.containsExactly("First");
	}

	@Test
	void testOtherOriginParticipatesInObsolete() {
		push("", "First", now.minusSeconds(20));
		push("", "Second", now.minusSeconds(10));
		push("@other", "Other", now);

		assertThat(refRepository.count())
			.isEqualTo(3);
		assertThat(version("@other", now).getMetadata().isObsolete())
			.isFalse();
		assertThat(version("", now.minusSeconds(10)).getMetadata().isObsolete())
			.isTrue();
		assertThat(version("", now.minusSeconds(20)).getMetadata().isObsolete())
			.isTrue();
		assertThat(refRepository.findOneByUrlAndOrigin(URL, ""))
			.get()
			.extracting(Ref::getTitle)
			.isEqualTo("Second");
	}

	@Test
	void testOlderOtherOriginIsObsolete() {
		push("@other", "Other", now.minusSeconds(20));
		push("", "First", now.minusSeconds(10));
		push("", "Second", now);

		assertThat(version("", now).getMetadata().isObsolete())
			.isFalse();
		assertThat(version("", now.minusSeconds(10)).getMetadata().isObsolete())
			.isTrue();
		assertThat(version("@other", now.minusSeconds(20)).getMetadata().isObsolete())
			.isTrue();
	}

	@Test
	void testUpdateMetadataOnlyChangesTargetedVersion() {
		push("", "First", now.minusSeconds(10));
		push("", "Second", now);
		var older = version("", now.minusSeconds(10));
		var before = version("", now).getMetadata();

		older.getMetadata().setResponses(new ArrayList<>(List.of("https://www.example.com/response")));
		assertThat(refRepository.updateMetadataVersion(URL, "", older.getModified(), older.getMetadata()))
			.isEqualTo(1);

		assertThat(version("", now.minusSeconds(10)).getMetadata())
			.satisfies(m -> assertThat(m.getResponses()).containsExactly("https://www.example.com/response"))
			.satisfies(m -> assertThat(m.isObsolete()).isTrue());
		assertThat(version("", now).getMetadata())
			.satisfies(m -> assertThat(m.getResponses()).isEqualTo(before.getResponses()))
			.satisfies(m -> assertThat(m.isObsolete()).isFalse());
	}

	@Test
	void testMarkCascadeOnlyChangesTargetedVersion() {
		push("", "First", now.minusSeconds(10));
		push("", "Second", now);

		assertThat(refRepository.markCascadeVersion(URL, "", now))
			.isEqualTo(1);

		assertThat(version("", now).getMetadata().isCascade())
			.isTrue();
		assertThat(version("", now.minusSeconds(10)).getMetadata())
			.satisfies(m -> assertThat(m.isCascade()).isFalse())
			.satisfies(m -> assertThat(m.isObsolete()).isTrue());
	}

	@Test
	void testBackfillOnlyChangesTargetedVersions() {
		// Old modified dates skip metadata generation on push and flag the Refs for regen
		var old = now.minus(1, ChronoUnit.DAYS);
		push("", "First", old.minusSeconds(10));
		push("", "Second", old);

		assertThat(backfillRepository.backfillMetadata("", 1))
			.isEqualTo(1);
		assertThat(backfillRepository.backfillMetadata("", 1))
			.isEqualTo(1);
		assertThat(backfillRepository.backfillMetadata("", 1))
			.isZero();

		assertThat(version("", old.minusSeconds(10)).getMetadata().isObsolete())
			.isTrue();
		assertThat(version("", old).getMetadata().isObsolete())
			.isFalse();
	}

	@Test
	void testHardDeleteRemovesAllVersions() {
		push("", "First", now.minusSeconds(20));
		push("", "Second", now.minusSeconds(10));
		push("", "", now, "internal", "plugin/delete");

		ingest.delete("", URL, "");

		assertThat(refRepository.count())
			.isZero();
		assertThat(refRepository.findOneByUrlAndOrigin(URL, ""))
			.isEmpty();
	}

	@Test
	void testRefTombstoneThenNewVersion() {
		push("", "First", now.minusSeconds(20));
		push("", "", now.minusSeconds(10), "internal", "plugin/delete");
		push("", "Restored", now);

		assertThat(refRepository.count())
			.isEqualTo(3);
		assertThat(refRepository.findOneByUrlAndOrigin(URL, ""))
			.get()
			.satisfies(r -> assertThat(r.getTitle()).isEqualTo("Restored"))
			.satisfies(r -> assertThat(r.getMetadata().isObsolete()).isFalse());
		assertThat(version("", now.minusSeconds(10)).getMetadata().isObsolete())
			.isTrue();
		assertThat(version("", now.minusSeconds(20)).getMetadata().isObsolete())
			.isTrue();
	}

	@Test
	void testReplicationReturnsAllVersions() {
		push("", "Second", now.minusSeconds(10));
		push("", "Third", now);
		push("", "First", now.minusSeconds(20));

		assertThat(refRepository.getCursor(""))
			.isEqualTo(now);
		assertThat(refRepository.findAll(
				RefFilter.builder().origin("").modifiedAfter(now.minusSeconds(30)).build().spec(),
				PageRequest.of(0, 10, by(Ref_.MODIFIED))))
			.extracting(Ref::getTitle)
			.containsExactly("First", "Second", "Third");
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			try (var stream = refRepository.streamAllByOriginOrderByModifiedDesc("")) {
				assertThat(stream.map(RefView::getTitle).toList())
					.containsExactly("Third", "Second", "First");
			}
		});
	}

	@Test
	void testExtKeepsPreviousVersions() {
		extRepository.save(ext("First", now.minusSeconds(10)));
		extRepository.save(ext("Second", now));

		assertThat(extRepository.count())
			.isEqualTo(2);
		assertThat(extRepository.findOneByQualifiedTag("test"))
			.get()
			.extracting(Ext::getName)
			.isEqualTo("Second");
	}

	@Test
	void testPluginLookupReturnsLatestVersion() {
		var first = new Plugin();
		first.setTag("plugin/test");
		first.setName("First");
		first.setModified(now.minusSeconds(10));
		pluginRepository.save(first);
		var second = new Plugin();
		second.setTag("plugin/test");
		second.setName("Second");
		second.setModified(now);
		pluginRepository.save(second);

		assertThat(pluginRepository.findByTagAndOrigin("plugin/test", ""))
			.get()
			.extracting(Plugin::getName)
			.isEqualTo("Second");

		var disabled = new Plugin();
		disabled.setTag("plugin/test");
		disabled.setConfig(new ObjectMapper().createObjectNode().put("disabled", true));
		disabled.setModified(now.plusSeconds(10));
		pluginRepository.save(disabled);

		assertThat(pluginRepository.findByTagAndOrigin("plugin/test", ""))
			.isEmpty();
	}

	@Test
	void testTemplateLookupReturnsLatestVersion() {
		var first = new Template();
		first.setTag("test");
		first.setName("First");
		first.setModified(now.minusSeconds(10));
		templateRepository.save(first);
		var second = new Template();
		second.setTag("test");
		second.setName("Second");
		second.setModified(now);
		templateRepository.save(second);

		assertThat(templateRepository.findByTemplateAndOrigin("test", ""))
			.get()
			.extracting(Template::getName)
			.isEqualTo("Second");
	}

	<T extends Tag> long count(QualifiedTagMixin<T> repo, String tag) {
		Specification<T> spec = (root, query, cb) -> cb.equal(root.get("tag"), tag);
		return repo.count(spec);
	}

	/**
	 * Delete notices are stored as versions and never remove rows. Hard delete purges every version.
	 */
	<T extends Tag> void assertTombstoneHistory(
		QualifiedTagMixin<T> repo,
		String tag,
		BiFunction<String, Instant, T> entity,
		Consumer<T> push,
		Consumer<String> hardDelete
	) {
		var deletor = deletorTag(tag);
		push.accept(entity.apply(tag, now.minusSeconds(30)));
		push.accept(entity.apply(tag, now.minusSeconds(20)));
		push.accept(entity.apply(deletor, now.minusSeconds(10)));

		assertThat(count(repo, tag))
			.isEqualTo(2);
		assertThat(count(repo, deletor))
			.isEqualTo(1);
		assertThat(repo.findOneByQualifiedTag(deletor))
			.get()
			.extracting(Tag::getModified)
			.isEqualTo(now.minusSeconds(10));
		assertThat(repo.existsLiveByQualifiedTag(tag, deletor))
			.isFalse();

		push.accept(entity.apply(tag, now));

		assertThat(count(repo, tag))
			.isEqualTo(3);
		assertThat(count(repo, deletor))
			.isEqualTo(1);
		assertThat(repo.findOneByQualifiedTag(tag))
			.get()
			.extracting(Tag::getModified)
			.isEqualTo(now);
		assertThat(repo.existsLiveByQualifiedTag(tag, deletor))
			.isTrue();

		hardDelete.accept(tag);

		assertThat(count(repo, tag))
			.isZero();
		assertThat(count(repo, deletor))
			.isZero();
		assertThat(repo.findOneByQualifiedTag(tag))
			.isEmpty();
	}

	@Test
	void testExtTombstoneKeepsHistory() {
		assertTombstoneHistory(extRepository, "test", (tag, modified) -> {
			var ext = new Ext();
			ext.setTag(tag);
			ext.setModified(modified);
			return ext;
		}, ext -> ingestExt.push("", ext, false, false), ingestExt::delete);
	}

	@Test
	void testUserTombstoneKeepsHistory() {
		assertTombstoneHistory(userRepository, "+user/test", (tag, modified) -> {
			var user = new User();
			user.setTag(tag);
			user.setModified(modified);
			return user;
		}, ingestUser::push, ingestUser::delete);
	}

	@Test
	void testPluginTombstoneKeepsHistory() {
		assertTombstoneHistory(pluginRepository, "plugin/test", (tag, modified) -> {
			var plugin = new Plugin();
			plugin.setTag(tag);
			plugin.setModified(modified);
			return plugin;
		}, ingestPlugin::push, ingestPlugin::delete);
	}

	@Test
	void testTemplateTombstoneKeepsHistory() {
		assertTombstoneHistory(templateRepository, "test", (tag, modified) -> {
			var template = new Template();
			template.setTag(tag);
			template.setModified(modified);
			return template;
		}, ingestTemplate::push, ingestTemplate::delete);
	}

	@Test
	void testCreateTombstoneAfterTombstone() {
		var ext = new Ext();
		ext.setTag("test");
		ext.setModified(now.minusSeconds(20));
		ingestExt.push("", ext, false, false);
		var deletor = new Ext();
		deletor.setTag("test/deleted");
		deletor.setModified(now.minusSeconds(10));
		ingestExt.push("", deletor, false, false);

		var again = new Ext();
		again.setTag("test/deleted");
		ingestExt.create(again);

		assertThat(count(extRepository, "test"))
			.isEqualTo(1);
		assertThat(count(extRepository, "test/deleted"))
			.isEqualTo(2);
	}

	@Test
	void testCreateTombstoneForLiveTagFails() {
		var deletor = new Ext();
		deletor.setTag("test/deleted");
		deletor.setModified(now.minusSeconds(20));
		ingestExt.push("", deletor, false, false);
		var ext = new Ext();
		ext.setTag("test");
		ext.setModified(now.minusSeconds(10));
		ingestExt.push("", ext, false, false);

		var again = new Ext();
		again.setTag("test/deleted");
		assertThatThrownBy(() -> ingestExt.create(again))
			.isInstanceOf(AlreadyExistsException.class);
	}
}
