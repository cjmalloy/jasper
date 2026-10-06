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
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import jasper.repository.filter.RefFilter;
import jasper.util.Archive;
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
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

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
	void testDeleteAppendsBlankVersion() {
		push("", "First", now.minusSeconds(20));
		push("", "Second", now.minusSeconds(10));

		ingest.delete("", URL, "");

		assertThat(refRepository.count())
			.isEqualTo(3);
		assertThat(refRepository.findOneByUrlAndOrigin(URL, ""))
			.get()
			.satisfies(r -> assertThat(Archive.isBlank(r)).isTrue())
			.satisfies(r -> assertThat(r.getMetadata().isObsolete()).isFalse());
		assertThat(ingest.current(URL, ""))
			.isEmpty();
		assertThat(version("", now.minusSeconds(10)).getMetadata().isObsolete())
			.isTrue();
		assertThat(version("", now.minusSeconds(20)).getMetadata().isObsolete())
			.isTrue();
	}

	@Test
	void testDeleteTombstonePrunes() {
		push("", "First", now.minusSeconds(20));
		push("", "Second", now.minusSeconds(10));
		ingest.delete("", URL, "");
		push("@other", "Other", now.minusSeconds(5));

		ingest.delete("", URL, "");

		assertThat(refRepository.findAll(isUrl(URL).and(isOrigin(""))))
			.isEmpty();
		assertThat(refRepository.findOneByUrlAndOrigin(URL, "@other"))
			.isPresent();
	}

	@Test
	void testDeleteNoticePrunes() {
		push("", "First", now.minusSeconds(20));
		push("", "", now, "internal", "plugin/delete");

		ingest.delete("", URL, "");

		assertThat(refRepository.count())
			.isZero();
	}

	@Test
	void testDeleteMissingIsNoop() {
		ingest.delete("", URL, "");

		assertThat(refRepository.count())
			.isZero();
	}

	@Test
	void testCreateExistingRefFails() {
		push("", "First", now.minusSeconds(10));

		assertThatThrownBy(() -> ingest.create("", ref("", "Again", null)))
			.isInstanceOf(AlreadyExistsException.class);
	}

	@Test
	void testCreateAfterDeleteAddsVersion() {
		push("", "First", now.minusSeconds(10));
		ingest.delete("", URL, "");

		ingest.create("", ref("", "Restored", null));

		assertThat(refRepository.count())
			.isEqualTo(3);
		assertThat(ingest.current(URL, ""))
			.get()
			.extracting(Ref::getTitle)
			.isEqualTo("Restored");
	}

	@Test
	void testUpdateAppendsVersion() {
		push("", "First", now.minusSeconds(10));

		ingest.update("", ref("", "Second", now.minusSeconds(10)));

		assertThat(refRepository.count())
			.isEqualTo(2);
		assertThat(ingest.current(URL, ""))
			.get()
			.extracting(Ref::getTitle)
			.isEqualTo("Second");
		assertThat(version("", now.minusSeconds(10)))
			.satisfies(r -> assertThat(r.getTitle()).isEqualTo("First"))
			.satisfies(r -> assertThat(r.getMetadata().isObsolete()).isTrue());
	}

	@Test
	void testUpdateStaleCursorFails() {
		push("", "First", now.minusSeconds(10));

		assertThatThrownBy(() -> ingest.update("", ref("", "Second", now.minusSeconds(20))))
			.isInstanceOf(ModifiedException.class);
		assertThat(refRepository.count())
			.isEqualTo(1);
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
	 * Pushes never remove rows. Deleting appends a blank version, deleting again prunes.
	 */
	<T extends Tag> void assertTombstoneHistory(
		QualifiedTagMixin<T> repo,
		String tag,
		BiFunction<String, Instant, T> entity,
		Consumer<T> push,
		Consumer<String> delete,
		Function<String, Optional<T>> current
	) {
		var deletor = deletorTag(tag);
		push.accept(entity.apply(tag, now.minusSeconds(40)));
		push.accept(entity.apply(deletor, now.minusSeconds(30)));
		push.accept(entity.apply(tag, now.minusSeconds(20)));

		assertThat(count(repo, tag))
			.isEqualTo(2);
		assertThat(count(repo, deletor))
			.isEqualTo(1);
		assertThat(current.apply(tag))
			.isPresent();

		delete.accept(tag);

		assertThat(count(repo, tag))
			.isEqualTo(3);
		assertThat(count(repo, deletor))
			.isEqualTo(1);
		assertThat(repo.findOneByQualifiedTag(tag))
			.get()
			.extracting(Tag::getModified)
			.isNotIn(now.minusSeconds(40), now.minusSeconds(20));
		assertThat(current.apply(tag))
			.isEmpty();

		delete.accept(tag);

		assertThat(count(repo, tag))
			.isZero();
		assertThat(count(repo, deletor))
			.isZero();

		delete.accept(tag);

		assertThat(count(repo, tag))
			.isZero();
	}

	<T extends Tag> void assertDeleteDeletorPrunes(
		QualifiedTagMixin<T> repo,
		String tag,
		BiFunction<String, Instant, T> entity,
		Consumer<T> push,
		Consumer<String> delete
	) {
		var deletor = deletorTag(tag);
		push.accept(entity.apply(tag, now.minusSeconds(20)));
		push.accept(entity.apply(deletor, now.minusSeconds(10)));

		assertThat(count(repo, tag))
			.isEqualTo(1);

		delete.accept(deletor);

		assertThat(count(repo, tag))
			.isZero();
		assertThat(count(repo, deletor))
			.isZero();
	}

	Ext ext(String tag, Instant modified, String name) {
		var ext = new Ext();
		ext.setTag(tag);
		ext.setName(name);
		ext.setModified(modified);
		return ext;
	}

	User user(String tag, Instant modified) {
		var user = new User();
		user.setTag(tag);
		user.setName("Name");
		user.setModified(modified);
		return user;
	}

	Plugin plugin(String tag, Instant modified) {
		var plugin = new Plugin();
		plugin.setTag(tag);
		plugin.setName("Name");
		plugin.setModified(modified);
		return plugin;
	}

	Template template(String tag, Instant modified) {
		var template = new Template();
		template.setTag(tag);
		template.setName("Name");
		template.setModified(modified);
		return template;
	}

	@Test
	void testExtTombstoneKeepsHistory() {
		assertTombstoneHistory(extRepository, "test", (tag, modified) -> ext(tag, modified, "Name"),
			ext -> ingestExt.push("", ext, false, false), ingestExt::delete, ingestExt::current);
	}

	@Test
	void testUserTombstoneKeepsHistory() {
		assertTombstoneHistory(userRepository, "+user/test", this::user,
			ingestUser::push, ingestUser::delete, ingestUser::current);
	}

	@Test
	void testPluginTombstoneKeepsHistory() {
		assertTombstoneHistory(pluginRepository, "plugin/test", this::plugin,
			ingestPlugin::push, ingestPlugin::delete, ingestPlugin::current);
	}

	@Test
	void testTemplateTombstoneKeepsHistory() {
		assertTombstoneHistory(templateRepository, "test", this::template,
			ingestTemplate::push, ingestTemplate::delete, ingestTemplate::current);
	}

	@Test
	void testExtDeleteDeletorPrunes() {
		assertDeleteDeletorPrunes(extRepository, "test", (tag, modified) -> ext(tag, modified, "Name"),
			ext -> ingestExt.push("", ext, false, false), ingestExt::delete);
	}

	@Test
	void testUserDeleteDeletorPrunes() {
		assertDeleteDeletorPrunes(userRepository, "+user/test", this::user, ingestUser::push, ingestUser::delete);
	}

	@Test
	void testPluginDeleteDeletorPrunes() {
		assertDeleteDeletorPrunes(pluginRepository, "plugin/test", this::plugin, ingestPlugin::push, ingestPlugin::delete);
	}

	@Test
	void testTemplateDeleteDeletorPrunes() {
		assertDeleteDeletorPrunes(templateRepository, "test", this::template, ingestTemplate::push, ingestTemplate::delete);
	}

	@Test
	void testExtCreateExistingFails() {
		ingestExt.push("", ext("test", now.minusSeconds(10), "First"), false, false);

		assertThatThrownBy(() -> ingestExt.create(ext("test", null, "Again")))
			.isInstanceOf(AlreadyExistsException.class);
	}

	@Test
	void testExtCreateAfterDeleteAddsVersion() {
		ingestExt.push("", ext("test", now.minusSeconds(10), "First"), false, false);
		ingestExt.delete("test");

		ingestExt.create(ext("test", null, "Restored"));

		assertThat(count(extRepository, "test"))
			.isEqualTo(3);
		assertThat(ingestExt.current("test"))
			.get()
			.extracting(Ext::getName)
			.isEqualTo("Restored");
	}

	@Test
	void testExtUpdateAppendsVersion() {
		ingestExt.push("", ext("test", now.minusSeconds(10), "First"), false, false);

		ingestExt.update(ext("test", now.minusSeconds(10), "Second"));

		assertThat(count(extRepository, "test"))
			.isEqualTo(2);
		assertThat(ingestExt.current("test"))
			.get()
			.extracting(Ext::getName)
			.isEqualTo("Second");
	}

	@Test
	void testExtUpdateDeletedFails() {
		ingestExt.push("", ext("test", now.minusSeconds(10), "First"), false, false);
		ingestExt.delete("test");

		assertThatThrownBy(() -> ingestExt.update(ext("test", now.minusSeconds(10), "Second")))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void testCreateDeletorForLiveTagFails() {
		ingestExt.push("", ext("test", now.minusSeconds(10), "First"), false, false);

		assertThatThrownBy(() -> ingestExt.create(ext("test/deleted", null, null)))
			.isInstanceOf(AlreadyExistsException.class);
	}
}
