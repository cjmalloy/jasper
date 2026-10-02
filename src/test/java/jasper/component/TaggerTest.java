package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.config.JacksonConfiguration;
import jasper.domain.Ref;
import jasper.errors.AlreadyExistsException;
import jasper.errors.ModifiedException;
import jasper.plugin.Cache;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
			.setPlugin("_plugin/cache", Cache.builder().id("winner").build());
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

		var loser = tagger.initPlugin(URL, "", "_plugin/cache", Cache.builder().id("loser").build(), "_plugin/delta/cache");
		var other = tagger.initPlugin(URL, "", "_plugin/cache", Cache.builder().id("other").build(), "_plugin/delta/cache");

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

		var loser = tagger.initPlugin(URL, "", "_plugin/cache", Cache.builder().id("loser").build(), "_plugin/delta/cache");
		var other = tagger.initPlugin(URL, "", "_plugin/cache", Cache.builder().id("other").build(), "_plugin/delta/cache");

		assertThat(cacheId(loser)).isEqualTo("winner");
		assertThat(cacheId(other)).isEqualTo("winner");
		verify(ingest, times(1)).update(eq(""), any(Ref.class));
		verify(ingest, never()).create(eq(""), any(Ref.class));
	}
}
