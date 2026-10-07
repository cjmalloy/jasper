package jasper.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.IntegrationTest;
import jasper.component.ConfigCache;
import jasper.component.Ingest;
import jasper.component.IngestExt;
import jasper.component.IngestPlugin;
import jasper.component.IngestTemplate;
import jasper.component.IngestUser;
import jasper.component.Messages;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.domain.proj.RefView;
import jasper.errors.AlreadyExistsException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import jasper.repository.filter.RefFilter;
import jasper.repository.filter.TagFilter;
import jasper.service.ExtService;
import jasper.service.PluginService;
import jasper.service.RefService;
import jasper.service.TemplateService;
import jasper.service.UserService;
import jasper.util.Archive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.verification.VerificationMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.RefSpec.isUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.data.domain.Sort.by;

@IntegrationTest
@ActiveProfiles({"archive", "test"})
// Use a separate database since the archive migration is one-way
@TestPropertySource(properties = "spring.datasource.url=jdbc:tc:postgresql:14.2:///jasper-archive?TC_TMPFS=/testtmpfs:rw")
@DisabledIfSystemProperty(named = "spring.profiles.active", matches = ".*sqlite.*")
@WithMockUser(value = "+user/tester", roles = "ADMIN")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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

	@Autowired
	RefService refService;

	@Autowired
	ExtService extService;

	@Autowired
	UserService userService;

	@Autowired
	PluginService pluginService;

	@Autowired
	TemplateService templateService;

	@Autowired
	ConfigCache configCache;

	@MockitoSpyBean
	Messages messages;

	Instant now;

	@BeforeEach
	void init() {
		refRepository.deleteAll();
		extRepository.deleteAll();
		userRepository.deleteAll();
		pluginRepository.deleteAll();
		templateRepository.deleteAll();
		now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		clearInvocations(messages);
	}

	@AfterEach
	void resetClock() {
		kinds().forEach(k -> k.setClock(Clock.systemUTC()));
	}

	List<VersionKind> kinds() {
		return VersionKind.all(
			ingest, refRepository,
			ingestExt, extRepository, extService,
			ingestUser, userRepository, userService,
			ingestPlugin, pluginRepository, pluginService,
			ingestTemplate, templateRepository, templateService,
			configCache);
	}

	List<VersionKind> tagKinds() {
		return kinds().stream().filter(VersionKind::hasDeletor).toList();
	}

	static VerificationMode sent() {
		return timeout(2000).times(1);
	}

	static VerificationMode notSent() {
		return after(300).never();
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
	void testCreateExistingRefFails() {
		push("", "First", now.minusSeconds(10));

		assertThatThrownBy(() -> ingest.create("", ref("", "Again", null)))
			.isInstanceOf(AlreadyExistsException.class);
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
	void testUpdateResponseSameTagsMarksOlderObsolete() {
		push("", "First", now.minusSeconds(10), "public");

		ingest.updateResponse("", ref("", "Second", now.minusSeconds(10), "public"));

		assertThat(refRepository.count())
			.isEqualTo(2);
		assertThat(version("", now.minusSeconds(10)).getMetadata().isObsolete())
			.isTrue();
		assertThat(refRepository.findAll(RefFilter.builder().obsolete(false).build().spec()))
			.extracting(Ref::getTitle)
			.containsExactly("Second");
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

	Ext ext(String tag, Instant modified, String name) {
		var ext = new Ext();
		ext.setTag(tag);
		ext.setName(name);
		ext.setModified(modified);
		return ext;
	}

	@Test
	void testExtCreateExistingFails() {
		ingestExt.push("", ext("test", now.minusSeconds(10), "First"), false, false);

		assertThatThrownBy(() -> ingestExt.create(ext("test", null, "Again")))
			.isInstanceOf(AlreadyExistsException.class);
	}

	@Test
	void testExtUpdateAppendsVersion() {
		ingestExt.push("", ext("test", now.minusSeconds(10), "First"), false, false);

		ingestExt.update(ext("test", now.minusSeconds(10), "Second"));

		assertThat(VersionKind.countTag(extRepository, "test", ""))
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

	// Generic scenarios run against every versioned type

	@ParameterizedTest(name = "{0}")
	@MethodSource("kinds")
	void testDeleteAppendsOneBlankVersion(VersionKind kind) {
		kind.push("", now.minusSeconds(20), "First");
		kind.push("", now.minusSeconds(10), "Second");

		kind.delete("");

		var versions = kind.versions("");
		assertThat(versions)
			.hasSize(3)
			.startsWith(now.minusSeconds(20), now.minusSeconds(10));
		assertThat(kind.latestTitle(""))
			.isEmpty();
		assertThat(kind.current(""))
			.isEmpty();
		assertThatThrownBy(() -> kind.get(""))
			.isInstanceOf(NotFoundException.class);
		kind.verifyDeleteNotice(messages, sent());
	}

	@Test
	void testDeleteMarksOlderRefVersionsObsolete() {
		push("", "First", now.minusSeconds(20));
		push("", "Second", now.minusSeconds(10));

		ingest.delete("", URL, "");

		assertThat(version("", now.minusSeconds(20)).getMetadata().isObsolete())
			.isTrue();
		assertThat(version("", now.minusSeconds(10)))
			.satisfies(r -> assertThat(r.getTitle()).isEqualTo("Second"))
			.satisfies(r -> assertThat(r.getMetadata().isObsolete()).isTrue());
		assertThat(refRepository.findOneByUrlAndOrigin(URL, ""))
			.get()
			.satisfies(r -> assertThat(Archive.isBlank(r)).isTrue())
			.satisfies(r -> assertThat(r.getMetadata().isObsolete()).isFalse());
	}

	@Test
	void testPushedDeleteNoticeRefIsTombstone() {
		push("", "First", now.minusSeconds(20));
		push("", "", now.minusSeconds(10), "internal", "plugin/delete");

		assertThat(ingest.current(URL, ""))
			.isEmpty();

		ingest.delete("", URL, "");

		assertThat(refRepository.count())
			.isZero();
		verify(messages, notSent()).deleteRef(any());
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("kinds")
	void testRecreateAfterDeleteKeepsTombstone(VersionKind kind) {
		kind.push("", now.minusSeconds(10), "First");
		kind.delete("");

		kind.create("", "Restored");

		assertThat(kind.count(""))
			.isEqualTo(3);
		assertThat(kind.current(""))
			.isPresent();
		assertThat(kind.latestTitle(""))
			.contains("Restored");
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("kinds")
	void testDeleteTwicePrunes(VersionKind kind) {
		kind.push("", now.minusSeconds(30), "First");
		kind.pushDeleteNotice("", now.minusSeconds(20));
		kind.push("", now.minusSeconds(10), "Second");
		kind.delete("");
		kind.verifyDeleteNotice(messages, sent());
		clearInvocations(messages);

		kind.delete("");

		assertThat(kind.count(""))
			.isZero();
		assertThat(kind.countDeletor(""))
			.isZero();
		kind.verifyDeleteNotice(messages, notSent());
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("tagKinds")
	void testDeleteDeletorPrunes(VersionKind kind) {
		kind.push("", now.minusSeconds(20), "First");
		kind.pushDeleteNotice("", now.minusSeconds(10));

		kind.deleteDeletor("");

		assertThat(kind.count(""))
			.isZero();
		assertThat(kind.countDeletor(""))
			.isZero();
		kind.verifyDeleteNotice(messages, notSent());
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("kinds")
	void testPruneLeavesOtherOrigin(VersionKind kind) {
		kind.push("", now.minusSeconds(20), "Local");
		kind.push("@other", now.minusSeconds(10), "Other");
		kind.delete("");

		kind.delete("");

		assertThat(kind.count(""))
			.isZero();
		assertThat(kind.count("@other"))
			.isEqualTo(1);
		assertThat(kind.current("@other"))
			.isPresent();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("kinds")
	void testPruneKeepsNewerThanStart(VersionKind kind) {
		kind.push("", now.minusSeconds(30), "First");
		kind.push("", now.minusSeconds(20), "Second");
		kind.pushBlank("", now.minusSeconds(5));
		kind.setClock(Clock.fixed(now.minusSeconds(15), ZoneOffset.UTC));

		kind.delete("");

		assertThat(kind.versions(""))
			.containsExactly(now.minusSeconds(5));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("kinds")
	void testDeleteMissingIsNoop(VersionKind kind) {
		kind.delete("");

		assertThat(kind.count(""))
			.isZero();
		assertThat(kind.countDeletor(""))
			.isZero();
		kind.verifyDeleteNotice(messages, notSent());
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("kinds")
	void testPushNeverRemovesRows(VersionKind kind) {
		kind.push("", now.minusSeconds(40), "First");
		kind.push("", now.minusSeconds(30), "Second");
		kind.pushBlank("", now.minusSeconds(20));
		kind.pushDeleteNotice("", now.minusSeconds(10));

		var expected = kind.hasDeletor() ? 3 : 4;
		assertThat(kind.count(""))
			.isEqualTo(expected);
		assertThat(kind.countDeletor(""))
			.isEqualTo(kind.hasDeletor() ? 1 : 0);

		kind.push("", now, "Third");

		assertThat(kind.count(""))
			.isEqualTo(expected + 1);
		assertThat(kind.current(""))
			.isPresent();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("kinds")
	void testOlderTombstoneKeepsNewerCurrent(VersionKind kind) {
		kind.push("", now.minusSeconds(10), "Current");

		kind.pushBlank("", now.minusSeconds(20));
		kind.pushDeleteNotice("", now.minusSeconds(30));

		assertThat(kind.current(""))
			.isPresent();
		assertThat(kind.latestTitle(""))
			.contains("Current");
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("kinds")
	void testCreatedBlankIsDeleted(VersionKind kind) {
		// Documented limitation: an item created blank reads as deleted
		kind.createBlank("");

		assertThat(kind.count(""))
			.isEqualTo(1);
		assertThat(kind.current(""))
			.isEmpty();
		assertThatThrownBy(() -> kind.get(""))
			.isInstanceOf(NotFoundException.class);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("kinds")
	void testRepeatedPushUpdatesSameVersion(VersionKind kind) {
		kind.push("", now.minusSeconds(10), "First");
		kind.push("", now, "Second");
		kind.push("", now, "Second Edited");

		assertThat(kind.versions(""))
			.containsExactly(now.minusSeconds(10), now);
		assertThat(kind.latestTitle(""))
			.contains("Second Edited");
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("tagKinds")
	void testPageIncludesOlderVersions(VersionKind kind) {
		// Documented limitation: page and count include older and deleted versions
		kind.push("", now.minusSeconds(20), "First");
		kind.push("", now.minusSeconds(10), "Second");
		kind.delete("");

		assertThat(kind.pageCount())
			.isGreaterThanOrEqualTo(3);
	}

	@Test
	void testExtCountIncludesOlderVersions() {
		ingestExt.push("", ext("test", now.minusSeconds(20), "First"), false, false);
		ingestExt.push("", ext("test", now.minusSeconds(10), "Second"), false, false);
		ingestExt.delete("test");

		assertThat(extService.count(TagFilter.builder().build()))
			.isEqualTo(3);
	}

	@Test
	void testAuthUserLookupSkipsTombstone() {
		var user = new User();
		user.setTag("+user/auth");
		user.setRole("ROLE_ADMIN");
		user.setModified(now.minusSeconds(10));
		ingestUser.push(user);
		ingestUser.delete("+user/auth");
		configCache.clearUserCache();

		assertThat(configCache.getUser("+user/auth"))
			.isNull();
	}

	@Test
	void testPruningUserDeletorInvalidatesCachedUser() {
		var user = new User();
		user.setTag("+user/auth");
		user.setRole("ROLE_ADMIN");
		user.setModified(now.minusSeconds(10));
		ingestUser.push(user);
		var deletor = new User();
		deletor.setTag("+user/auth/deleted");
		deletor.setModified(now);
		ingestUser.push(deletor);

		assertThat(configCache.getUser("+user/auth"))
			.extracting(User::getRole)
			.isEqualTo("ROLE_ADMIN");

		ingestUser.delete("+user/auth/deleted");

		assertThat(configCache.getUser("+user/auth"))
			.isNull();
	}

	@Test
	void testPruningTemplateDeletorInvalidatesCachedTemplate() {
		var template = new Template();
		template.setTag("test");
		template.setName("Test");
		template.setModified(now.minusSeconds(10));
		ingestTemplate.push(template);
		var deletor = new Template();
		deletor.setTag("test/deleted");
		deletor.setModified(now);
		ingestTemplate.push(deletor);

		assertThat(configCache.getTemplate("test", ""))
			.get()
			.extracting(Template::getName)
			.isEqualTo("Test");
		clearInvocations(messages);

		ingestTemplate.delete("test/deleted");

		assertThat(configCache.getTemplate("test", ""))
			.isEmpty();
		verify(messages).invalidateTemplate("test");
		verify(messages, notSent()).deleteTemplate(any());
	}

	@Test
	void testAuthUserLookupUsesLatestVersion() {
		var older = new User();
		older.setTag("+user/auth");
		older.setRole("ROLE_ADMIN");
		older.setModified(now.minusSeconds(20));
		ingestUser.push(older);
		var newer = new User();
		newer.setTag("+user/auth");
		newer.setRole("ROLE_USER");
		newer.setModified(now.minusSeconds(10));
		ingestUser.push(newer);
		configCache.clearUserCache();

		assertThat(configCache.getUser("+user/auth"))
			.extracting(User::getRole)
			.isEqualTo("ROLE_USER");
	}

	@Test
	void testRefGetTombstoneNotFound() {
		push("", "First", now.minusSeconds(10));
		ingest.delete("", URL, "");

		assertThatThrownBy(() -> refService.get(URL, ""))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void testConfigCachePluginSkipsTombstone() {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setName("Test");
		plugin.setConfig(new ObjectMapper().createObjectNode().put("value", 1));
		plugin.setModified(now.minusSeconds(10));
		ingestPlugin.push(plugin);
		ingestPlugin.delete("plugin/test");
		configCache.clearPluginCache();

		assertThat(configCache.getPlugin("plugin/test", ""))
			.isEmpty();
		assertThat(configCache.getPluginConfig("plugin/test", "", Object.class))
			.isEmpty();
	}

	@Test
	void testConfigCacheTemplateSkipsTombstone() {
		var template = new Template();
		template.setTag("test");
		template.setName("Test");
		template.setConfig(new ObjectMapper().createObjectNode().put("value", 1));
		template.setModified(now.minusSeconds(10));
		ingestTemplate.push(template);
		ingestTemplate.delete("test");
		configCache.clearTemplateCache();

		assertThat(configCache.getTemplate("test", ""))
			.isEmpty();
		assertThat(configCache.getTemplateConfig("test", "", Object.class))
			.isEmpty();
	}

	@Test
	void testServicePushBlankUserIsTombstone() {
		var user = new User();
		user.setTag("+user/blank");
		user.setRole("ROLE_USER");
		user.setPubKey("ssh-rsa AAAA".getBytes());
		user.setModified(now.minusSeconds(10));
		userService.push(user);
		var blank = new User();
		blank.setTag("+user/blank");
		blank.setModified(now);

		userService.push(blank);

		assertThat(VersionKind.countTag(userRepository, "+user/blank", ""))
			.isEqualTo(2);
		assertThat(ingestUser.current("+user/blank"))
			.isEmpty();
	}

	@Test
	void testSetExternalIdAppendsVersion() {
		var user = new User();
		user.setTag("+user/ext");
		user.setRole("ROLE_USER");
		user.setModified(now.minusSeconds(10));
		ingestUser.push(user);

		configCache.setExternalId("+user/ext", "", "ext@example.com");

		assertThat(VersionKind.countTag(userRepository, "+user/ext", ""))
			.isEqualTo(2);
		assertThat(userRepository.findAll().stream().filter(u -> u.getModified().equals(now.minusSeconds(10))).findFirst())
			.get()
			.satisfies(u -> assertThat(u.hasExternalId()).isFalse());
		assertThat(ingestUser.current("+user/ext"))
			.get()
			.satisfies(u -> assertThat(u.hasExternalId("ext@example.com")).isTrue())
			.extracting(User::getRole)
			.isEqualTo("ROLE_USER");
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("kinds")
	void testDeleteAndRecreateAfterFuturePush(VersionKind kind) {
		kind.push("", now.plusSeconds(60), "Future");

		kind.delete("");

		assertThat(kind.current(""))
			.isEmpty();

		kind.create("", "Restored");

		assertThat(kind.current(""))
			.isPresent();
		assertThat(kind.latestTitle(""))
			.contains("Restored");
		assertThat(kind.versions(""))
			.hasSize(3)
			.allSatisfy(m -> assertThat(m).isAfterOrEqualTo(now.plusSeconds(60)));
	}

	@Test
	void testUpdateAfterFuturePush() {
		push("", "Future", now.plusSeconds(60));

		ingest.update("", ref("", "Second", now.plusSeconds(60)));

		assertThat(ingest.current(URL, ""))
			.get()
			.satisfies(r -> assertThat(r.getTitle()).isEqualTo("Second"))
			.satisfies(r -> assertThat(r.getModified()).isAfter(now.plusSeconds(60)));
	}

	@Test
	void testExtUpdateAfterFuturePush() {
		ingestExt.push("", ext("test", now.plusSeconds(60), "Future"), false, false);

		ingestExt.update(ext("test", now.plusSeconds(60), "Second"));

		assertThat(ingestExt.current("test"))
			.get()
			.satisfies(e -> assertThat(e.getName()).isEqualTo("Second"))
			.satisfies(e -> assertThat(e.getModified()).isAfter(now.plusSeconds(60)));
	}

	@Test
	void testSilentAppendsVersion() {
		push("", "First", now.minusSeconds(10));
		var existing = refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow();
		existing.setTitle("Silent");

		ingest.silent("", existing);

		assertThat(refRepository.count())
			.isEqualTo(2);
		assertThat(version("", now.minusSeconds(10)).getTitle())
			.isEqualTo("First");
		assertThat(ingest.current(URL, ""))
			.get()
			.satisfies(r -> assertThat(r.getTitle()).isEqualTo("Silent"))
			.satisfies(r -> assertThat(r.getModified()).isEqualTo(now.minusSeconds(10).plus(1, ChronoUnit.MICROS)));
	}
}
