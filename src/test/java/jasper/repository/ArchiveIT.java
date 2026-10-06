package jasper.repository;

import jasper.IntegrationTest;
import jasper.component.Ingest;
import jasper.component.IngestExt;
import jasper.component.IngestUser;
import jasper.domain.Ext;
import jasper.domain.Ref;
import jasper.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static jasper.repository.spec.UserSpec.hasAuthorizedKeys;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@ActiveProfiles({"archive", "test"})
// Use a separate database so the archive triggers do not affect other tests
@TestPropertySource(properties = "spring.datasource.url=jdbc:tc:postgresql:14.2:///jasper-archive?TC_TMPFS=/testtmpfs:rw")
@DisabledIfSystemProperty(named = "spring.profiles.active", matches = ".*sqlite.*")
public class ArchiveIT {
	@Autowired
	Ingest ingest;

	@Autowired
	IngestExt ingestExt;

	@Autowired
	IngestUser ingestUser;

	@Autowired
	RefRepository refRepository;

	@Autowired
	ExtRepository extRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void init() {
		refRepository.deleteAll();
		extRepository.deleteAll();
		userRepository.deleteAll();
		jdbc.execute("TRUNCATE ref_archive, ext_archive, users_archive, plugin_archive, template_archive");
	}

	@Test
	void testRefUpdateArchivesPreviousVersion() {
		var URL = "https://www.example.com/update";
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ingest.create("", ref);
		var update = refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow();
		update.setTitle("Second");
		ingest.update("", update);

		assertThat(refRepository.count())
			.isEqualTo(1);
		assertThat(refRepository.findOneByUrlAndOrigin(URL, ""))
			.get()
			.extracting(Ref::getTitle)
			.isEqualTo("Second");
		assertThat(jdbc.queryForList("SELECT title FROM ref_archive WHERE url = ?", String.class, URL))
			.containsExactly("First");
	}

	@Test
	void testRefDeleteArchivesLastVersion() {
		var URL = "https://www.example.com/delete";
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ingest.create("", ref);

		ingest.delete("", URL, "");

		assertThat(refRepository.count())
			.isZero();
		assertThat(jdbc.queryForList("SELECT title FROM ref_archive WHERE url = ?", String.class, URL))
			.containsExactly("First");
	}

	@Test
	void testExtUpdateArchivesPreviousVersion() {
		var ext = new Ext();
		ext.setTag("test");
		ext.setName("First");
		ingestExt.create(ext);
		var update = extRepository.findOneByQualifiedTag("test").orElseThrow();
		update.setName("Second");
		ingestExt.update(update);

		assertThat(extRepository.count())
			.isEqualTo(1);
		assertThat(extRepository.findOneByQualifiedTag("test"))
			.get()
			.extracting(Ext::getName)
			.isEqualTo("Second");
		assertThat(jdbc.queryForList("SELECT name FROM ext_archive WHERE tag = ?", String.class, "test"))
			.containsExactly("First");
	}

	@Test
	void testArchivedUserAuthorizedKeysIgnored() {
		var user = new User();
		user.setTag("+user/test");
		user.setAuthorizedKeys("ssh-ed25519 AAAA");
		ingestUser.create(user);
		var update = userRepository.findOneByQualifiedTag("+user/test").orElseThrow();
		update.setAuthorizedKeys(null);
		ingestUser.update(update);

		assertThat(userRepository.findAll(hasAuthorizedKeys()))
			.isEmpty();
		assertThat(jdbc.queryForList("SELECT authorized_keys FROM users_archive WHERE tag = ?", String.class, "+user/test"))
			.containsExactly("ssh-ed25519 AAAA");
	}
}
