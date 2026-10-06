package jasper.repository;

import jasper.IntegrationTest;
import jasper.domain.Ext;
import jasper.domain.Ref;
import jasper.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static jasper.repository.spec.UserSpec.hasAuthorizedKeys;
import static jasper.repository.spec.UserSpec.isLatest;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@ActiveProfiles({"archive", "test"})
// Use a separate database since the archive migration is one-way
@TestPropertySource(properties = "spring.datasource.url=jdbc:tc:postgresql:14.2:///jasper-archive?TC_TMPFS=/testtmpfs:rw")
@DisabledIfSystemProperty(named = "spring.profiles.active", matches = ".*sqlite.*")
public class ArchiveIT {
	static final String URL = "https://www.example.com/";

	@Autowired
	RefRepository refRepository;

	@Autowired
	ExtRepository extRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	PlatformTransactionManager transactionManager;

	@BeforeEach
	void init() {
		refRepository.deleteAll();
		extRepository.deleteAll();
		userRepository.deleteAll();
	}

	Ref ref(String title, Instant modified) {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle(title);
		ref.setModified(modified);
		return ref;
	}

	Ext ext(String name, Instant modified) {
		var ext = new Ext();
		ext.setTag("test");
		ext.setName(name);
		ext.setModified(modified);
		return ext;
	}

	@Test
	void testRefKeepsPreviousVersions() {
		var now = Instant.now();
		refRepository.save(ref("First", now.minusSeconds(10)));
		refRepository.save(ref("Second", now));

		assertThat(refRepository.count())
			.isEqualTo(2);
		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		assertThat(refRepository.findOneByUrlAndOrigin(URL, ""))
			.get()
			.extracting(Ref::getTitle)
			.isEqualTo("Second");
	}

	@Test
	void testRefDeleteRemovesAllVersions() {
		var now = Instant.now();
		refRepository.save(ref("First", now.minusSeconds(10)));
		refRepository.save(ref("Second", now));

		new TransactionTemplate(transactionManager).executeWithoutResult(status ->
			refRepository.deleteByUrlAndOrigin(URL, ""));

		assertThat(refRepository.count())
			.isZero();
	}

	@Test
	void testExtKeepsPreviousVersions() {
		var now = Instant.now();
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
	void testArchivedUserAuthorizedKeysIgnored() {
		var now = Instant.now();
		var old = new User();
		old.setTag("+user/test");
		old.setAuthorizedKeys("ssh-ed25519 AAAA");
		old.setModified(now.minusSeconds(10));
		userRepository.save(old);
		var latest = new User();
		latest.setTag("+user/test");
		latest.setModified(now);
		userRepository.save(latest);

		assertThat(userRepository.count())
			.isEqualTo(2);
		assertThat(userRepository.findAll(hasAuthorizedKeys().and(isLatest())))
			.isEmpty();
		assertThat(userRepository.findAllByQualifiedSuffix("user/test"))
			.hasSize(1)
			.first()
			.extracting(User::getAuthorizedKeys)
			.isNull();
	}
}
