package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.config.JacksonConfiguration;
import jasper.domain.Ref;
import jasper.errors.AlreadyExistsException;
import jasper.errors.ModifiedException;
import jasper.plugin.Cache;
import jasper.repository.RefRepository;
import jasper.service.dto.RefDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static jasper.domain.Ref.from;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TaggerTest {

	static final String URL = "https://example.com/video/seg0.ts";
	static final String SOURCE = "https://example.com/video/index.m3u8";

	Tagger tagger;
	RefRepository refRepository;
	Ingest ingest;

	@BeforeEach
	void init() {
		ReflectionTestUtils.setField(JacksonConfiguration.class, "om", new ObjectMapper());
		tagger = new Tagger();
		tagger.configs = mock(ConfigCache.class);
		tagger.refRepository = refRepository = mock(RefRepository.class);
		tagger.ingest = ingest = mock(Ingest.class);
	}

	Ref winner() {
		return from(URL, "", "internal", "_plugin/delta/cache")
			.setPlugin("_plugin/cache", Cache.builder().id("winner").build())
			.addSource(SOURCE);
	}

	String cacheId(Ref ref) {
		return ref.getPlugin("_plugin/cache", Cache.class).getId();
	}

	@Test
	void testInitPluginCreateRaceReturnsWinner() {
		when(refRepository.findOneByUrlAndOrigin(URL, ""))
			.thenReturn(Optional.empty())
			.thenReturn(Optional.of(winner()));
		doThrow(new AlreadyExistsException()).when(ingest).create(eq(""), any(Ref.class));

		var loser = tagger.initPlugin(SOURCE, URL, "", "_plugin/cache", Cache.builder().id("loser").build(), "_plugin/delta/cache");
		var other = tagger.initPlugin(SOURCE, URL, "", "_plugin/cache", Cache.builder().id("other").build(), "_plugin/delta/cache");

		assertThat(cacheId(loser)).isEqualTo("winner");
		assertThat(cacheId(other)).isEqualTo("winner");
		verify(ingest, times(1)).create(eq(""), any(Ref.class));
		verify(ingest, never()).update(eq(""), any(Ref.class));
	}

	@Test
	void testInitPluginUpdateRaceReturnsWinner() {
		when(refRepository.findOneByUrlAndOrigin(URL, ""))
			.thenReturn(Optional.of(from(URL, "")))
			.thenReturn(Optional.of(winner()));
		doThrow(new ModifiedException("Ref")).when(ingest).update(eq(""), any(Ref.class));

		var loser = tagger.initPlugin(SOURCE, URL, "", "_plugin/cache", Cache.builder().id("loser").build(), "_plugin/delta/cache");
		var other = tagger.initPlugin(SOURCE, URL, "", "_plugin/cache", Cache.builder().id("other").build(), "_plugin/delta/cache");

		assertThat(cacheId(loser)).isEqualTo("winner");
		assertThat(cacheId(other)).isEqualTo("winner");
		verify(ingest, times(1)).update(eq(""), any(Ref.class));
		verify(ingest, never()).create(eq(""), any(Ref.class));
	}

	@Test
	void testInitPluginCreateAddsSource() {
		when(refRepository.findOneByUrlAndOrigin(URL, "")).thenReturn(Optional.empty());

		var ref = tagger.initPlugin(SOURCE, URL, "", "_plugin/cache", Cache.builder().id("id").build(), "_plugin/delta/cache");

		assertThat(ref.getSources()).containsExactly(SOURCE);
		verify(ingest).create(eq(""), any(Ref.class));
	}

	@Test
	void testInitPluginExistingReservationAddsSource() {
		when(refRepository.findOneByUrlAndOrigin(URL, ""))
			.thenReturn(Optional.of(from(URL, "", "internal", "_plugin/delta/cache")
				.setPlugin("_plugin/cache", Cache.builder().id("winner").build())));

		var ref = tagger.initPlugin(SOURCE, URL, "", "_plugin/cache", Cache.builder().id("other").build(), "_plugin/delta/cache");

		assertThat(cacheId(ref)).isEqualTo("winner");
		assertThat(ref.getSources()).containsExactly(SOURCE);
		verify(ingest).update(eq(""), any(Ref.class));
	}

	@Test
	void testInitPluginGivesUpAfterUpdateConflicts() {
		when(refRepository.findOneByUrlAndOrigin(URL, "")).thenAnswer(i -> Optional.of(from(URL, "")));
		doThrow(new ModifiedException("Ref")).when(ingest).update(eq(""), any(Ref.class));

		var ref = tagger.initPlugin(SOURCE, URL, "", "_plugin/cache", Cache.builder().id("id").build(), "_plugin/delta/cache");

		assertThat(ref).isNotNull();
		assertThat(ref.hasPlugin("_plugin/cache")).isFalse();
		verify(ingest, times(Tagger.INIT_PLUGIN_RETRIES)).update(eq(""), any(Ref.class));
		verify(refRepository, times(Tagger.INIT_PLUGIN_RETRIES + 1)).findOneByUrlAndOrigin(URL, "");
	}

	@Test
	void testInitPluginGivesUpAfterCreateConflicts() {
		when(refRepository.findOneByUrlAndOrigin(URL, "")).thenReturn(Optional.empty());
		doThrow(new AlreadyExistsException()).when(ingest).create(eq(""), any(Ref.class));

		var ref = tagger.initPlugin(SOURCE, URL, "", "_plugin/cache", Cache.builder().id("id").build(), "_plugin/delta/cache");

		assertThat(ref).isNull();
		verify(ingest, times(Tagger.INIT_PLUGIN_RETRIES)).create(eq(""), any(Ref.class));
	}

	@Test
	void testAttachLogsRedirectsSubOriginToRemoteOrigin() {
		mockRemote("+user/alice");
		var parent = from(URL, "@sub", "_user/bob");

		tagger.attachLogs("@sub", parent, "title", "logs");

		var log = ArgumentCaptor.forClass(Ref.class);
		verify(ingest).create(eq(""), log.capture());
		assertThat(log.getValue().getOrigin()).isEqualTo("");
		assertThat(log.getValue().getSources()).containsExactly(URL);
		assertThat(log.getValue().getTags())
			.contains("+plugin/log", "user/alice")
			.doesNotContain("user/bob", "_user/bob", "public");
		verify(ingest, never()).create(eq("@sub"), any(Ref.class));
	}

	@Test
	void testAttachLogsRedirectedCopiesPrivateOwnerAndPublic() {
		mockRemote("_user/alice");
		var parent = from(URL, "@sub", "public", "+user/bob");

		tagger.attachLogs("@sub", parent, "title", "logs");

		var log = ArgumentCaptor.forClass(Ref.class);
		verify(ingest).create(eq(""), log.capture());
		assertThat(log.getValue().getTags())
			.contains("+plugin/log", "public", "user/alice")
			.doesNotContain("user/bob", "+user/bob");
	}

	@Test
	void testAttachLogsKeepsLocalOriginPublic() {
		var parent = from(URL, "@sub", "public", "+user/bob");

		tagger.attachLogs("@sub", parent, "title", "logs");

		var log = ArgumentCaptor.forClass(Ref.class);
		verify(ingest).create(eq("@sub"), log.capture());
		assertThat(log.getValue().getTags()).contains("+plugin/log", "public", "user/bob");
	}

	void mockRemote(String ...tags) {
		var remote = new RefDto();
		remote.setUrl("https://remote.example.com");
		remote.setOrigin("");
		when(tagger.configs.getRemote("@sub")).thenReturn(remote);
		when(refRepository.findOneByUrlAndOrigin("https://remote.example.com", ""))
			.thenReturn(Optional.of(from("https://remote.example.com", "", "+plugin/origin").addTags(List.of(tags))));
	}

	@Test
	void testAttachLogsKeepsLocalOrigin() {
		var parent = from(URL, "@sub", "_user/bob");

		tagger.attachLogs("@sub", parent, "title", "logs");

		var log = ArgumentCaptor.forClass(Ref.class);
		verify(ingest).create(eq("@sub"), log.capture());
		assertThat(log.getValue().getOrigin()).isEqualTo("@sub");
		assertThat(log.getValue().getTags()).contains("+plugin/log", "user/bob");
	}

	@Test
	void testAttachErrorRedirectsSubOriginToRemoteOrigin() {
		var remote = new RefDto();
		remote.setOrigin("");
		when(tagger.configs.getRemote("@sub")).thenReturn(remote);
		var parent = from(URL, "@sub");

		tagger.attachError("@sub", parent, "title", "logs");

		verify(ingest).create(eq(""), any(Ref.class));
		verify(ingest, never()).create(eq("@sub"), any(Ref.class));
		verify(ingest, never()).update(any(), any(Ref.class));
	}
}
