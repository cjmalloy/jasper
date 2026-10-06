package jasper.repository;

import jasper.IntegrationTest;
import jasper.component.Ingest;
import jasper.domain.Ext;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.domain.proj.RefView;
import jasper.repository.filter.RefFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.RefSpec.isUrl;
import static org.assertj.core.api.Assertions.assertThat;
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
	PlatformTransactionManager transactionManager;

	Instant now;

	@BeforeEach
	void init() {
		refRepository.deleteAll();
		extRepository.deleteAll();
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
	void testDeletedPluginBecomesCurrentVersion() {
		push("", "First", now.minusSeconds(20));
		push("", "Second", now.minusSeconds(10));
		push("", "", now, "plugin/deleted");

		assertThat(refRepository.count())
			.isEqualTo(3);
		assertThat(refRepository.findOneByUrlAndOrigin(URL, ""))
			.get()
			.satisfies(r -> assertThat(r.getTags()).containsExactly("plugin/deleted"))
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
	void testDeleteOnlyRemovesLatestVersion() {
		push("", "First", now.minusSeconds(10));
		push("", "Second", now);

		ingest.delete("", URL, "");

		assertThat(refRepository.count())
			.isEqualTo(1);
		assertThat(refRepository.findOneByUrlAndOrigin(URL, ""))
			.get()
			.satisfies(r -> assertThat(r.getTitle()).isEqualTo("First"))
			.satisfies(r -> assertThat(r.getMetadata().isObsolete()).isFalse());
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
}
