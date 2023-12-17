package jasper.component;

import jasper.IntegrationTest;
import jasper.domain.Ref;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@IntegrationTest
@ExtendWith(MockitoExtension.class)
public class IngestIT {

	@InjectMocks
	@Autowired
	Ingest ingest;

	@Autowired
	RefRepository refRepository;


	static final String URL = "https://www.example.com/";
	static final String OTHER_URL = "https://www.example.com/other";

	@BeforeEach
	void init() {
		refRepository.deleteAll();
	}

	@Test
	void testIngestRef() {
		var ref = new Ref();
		ref.setUrl(URL);

		ingest.ingest(ref, false);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
	}

	@Test
	void testCreateDuplicateRefFails() {
		var existing = new Ref();
		existing.setUrl(URL);
		refRepository.save(existing);
		var ref = new Ref();
		ref.setUrl(URL);

		assertThatThrownBy(() -> ingest.ingest(ref, false))
			.isInstanceOf(AlreadyExistsException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
	}

	@Test
	void testDoubleIngestRefFails() {
		var ref1 = new Ref();
		ref1.setUrl(URL);
		ref1.setTitle("First");
		var ref2 = new Ref();
		ref2.setUrl(URL);
		ref2.setTitle("Second");

		ingest.ensureUniqueModified(ref1, true);
		assertThatThrownBy(() -> ingest.ensureUniqueModified(ref2, true))
			.isInstanceOf(AlreadyExistsException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
	}

	@Test
	void testUpdateRef() {
		var existing = new Ref();
		existing.setUrl(URL);
		existing.setTitle("First");
		refRepository.save(existing);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("Second");

		ingest.update(ref, false);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Second");
	}

	@Test
	void testDuplicateCreateModifiedFails() {
		var fixedClock = Clock.fixed(Instant.ofEpochSecond(1640000000), ZoneOffset.UTC);
		setField(ingest, "ensureUniqueModifiedClock", fixedClock);
		try {
			var modified = Instant.now(fixedClock);
			var ref1 = new Ref();
			ref1.setUrl(URL);
			ref1.setTitle("First");
			ref1.setModified(modified);
			ingest.ingest(ref1, false);
			var ref2 = new Ref();
			ref2.setUrl(OTHER_URL);
			ref2.setTitle("Second");
			ref2.setModified(modified);

			assertThatThrownBy(() -> ingest.ingest(ref2, false))
				.isInstanceOf(DuplicateModifiedDateException.class);

			assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
				.isTrue();
			var fetched1 = refRepository.findOneByUrlAndOrigin(URL, "").get();
			assertThat(fetched1.getTitle())
				.isEqualTo("First");
			assertThat(fetched1.getModified())
				.isEqualTo(modified);
			assertThat(refRepository.existsByUrlAndOrigin(OTHER_URL, ""))
				.isFalse();
		} finally {
			setField(ingest, "ensureUniqueModifiedClock", Clock.systemUTC());
		}
	}

	@Test
	void testDuplicateUpdateModifiedFails() {
		var fixedClock = Clock.fixed(Instant.ofEpochSecond(1640000000), ZoneOffset.UTC);
		try {
			var modified = Instant.now(fixedClock);
			var ref1 = new Ref();
			ref1.setUrl(URL);
			ref1.setTitle("First");
			ref1.setModified(Instant.now());
			ingest.ingest(ref1, false);
			setField(ingest, "ensureUniqueModifiedClock", fixedClock);
			var ref2 = new Ref();
			ref2.setUrl(OTHER_URL);
			ref2.setTitle("Second");
			ref2.setModified(modified);
			ingest.ingest(ref2, false);
			var update = new Ref();
			update.setUrl(URL);
			update.setTitle("Modified");
			update.setModified(modified);

			assertThatThrownBy(() -> ingest.update(update, false))
				.isInstanceOf(DuplicateModifiedDateException.class);

			assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
				.isTrue();
			var fetched1 = refRepository.findOneByUrlAndOrigin(URL, "").get();
			assertThat(fetched1.getTitle())
				.isEqualTo("First");
			var fetched2 = refRepository.findOneByUrlAndOrigin(OTHER_URL, "").get();
			assertThat(fetched2.getTitle())
				.isEqualTo("Second");
			assertThat(fetched2.getModified())
				.isEqualTo(modified);
		} finally {
			setField(ingest, "ensureUniqueModifiedClock", Clock.systemUTC());
		}
	}

}
