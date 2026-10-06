package jasper.repository;

import jasper.IntegrationTest;
import jasper.component.ConfigCache;
import jasper.component.Ingest;
import jasper.component.IngestExt;
import jasper.component.IngestPlugin;
import jasper.component.IngestTemplate;
import jasper.component.IngestUser;
import jasper.component.Messages;
import jasper.service.ExtService;
import jasper.service.PluginService;
import jasper.service.TemplateService;
import jasper.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;

/**
 * Delete behavior on a regular (non-archive) server, to make sure the
 * archive changes don't leak.
 */
@IntegrationTest
@WithMockUser(value = "+user/tester", roles = "ADMIN")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RegularDeleteIT {

	@Autowired
	Ingest ingest;
	@Autowired
	RefRepository refRepository;
	@Autowired
	IngestExt ingestExt;
	@Autowired
	ExtRepository extRepository;
	@Autowired
	ExtService extService;
	@Autowired
	IngestUser ingestUser;
	@Autowired
	UserRepository userRepository;
	@Autowired
	UserService userService;
	@Autowired
	IngestPlugin ingestPlugin;
	@Autowired
	PluginRepository pluginRepository;
	@Autowired
	PluginService pluginService;
	@Autowired
	IngestTemplate ingestTemplate;
	@Autowired
	TemplateRepository templateRepository;
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

	@ParameterizedTest(name = "{0}")
	@MethodSource("kinds")
	void testDeleteRemovesRowAndSendsNotice(VersionKind kind) {
		kind.push("", now.minusSeconds(10), "First");

		kind.delete("");

		assertThat(kind.count(""))
			.isZero();
		assertThat(kind.current(""))
			.isEmpty();
		kind.verifyDeleteNotice(messages, timeout(2000).times(1));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("tagKinds")
	void testPushDeletorRemovesTag(VersionKind kind) {
		kind.push("", now.minusSeconds(20), "First");

		kind.pushDeleteNotice("", now.minusSeconds(10));

		assertThat(kind.count(""))
			.isZero();
		assertThat(kind.countDeletor(""))
			.isEqualTo(1);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("kinds")
	void testPushedBlankIsNotDeleted(VersionKind kind) {
		kind.push("", now.minusSeconds(20), "First");

		kind.pushBlank("", now.minusSeconds(10));

		assertThat(kind.count(""))
			.isEqualTo(1);
		assertThat(kind.current(""))
			.isPresent();
		assertThat(kind.get(""))
			.isNotNull();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("kinds")
	void testCreatedBlankIsNotDeleted(VersionKind kind) {
		kind.createBlank("");

		assertThat(kind.count(""))
			.isEqualTo(1);
		assertThat(kind.current(""))
			.isPresent();
		assertThat(kind.get(""))
			.isNotNull();
	}
}
