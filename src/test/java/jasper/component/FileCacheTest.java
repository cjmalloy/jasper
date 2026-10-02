package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.config.JacksonConfiguration;
import jasper.domain.Ref;
import jasper.plugin.Cache;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FileCacheTest {

	static final String MANIFEST = "#EXTM3U\n#EXTINF:10,\nseg0.ts\n";
	static final String MANIFEST_URL = "https://example.com/video/index.m3u8";
	static final String SEGMENT_URL = "https://example.com/video/seg0.ts";

	FileCache fileCache;
	Storage storage;
	Tagger tagger;
	/**
	 * Persisted segment Ref, or null if it does not exist.
	 */
	AtomicReference<Ref> segment = new AtomicReference<>();

	@BeforeEach
	void init() throws IOException {
		ReflectionTestUtils.setField(JacksonConfiguration.class, "om", new ObjectMapper());
		fileCache = new FileCache();
		fileCache.configs = mock(ConfigCache.class);
		fileCache.refRepository = mock(RefRepository.class);
		fileCache.fetch = mock(Fetch.class);
		fileCache.tagger = tagger = mock(Tagger.class);
		fileCache.storage = storage = mock(Storage.class);
		when(fileCache.refRepository.findOneByUrlAndOrigin(anyString(), anyString())).thenReturn(Optional.empty());
		when(fileCache.refRepository.findOneByUrlAndOrigin(SEGMENT_URL, "")).thenAnswer(i -> Optional.ofNullable(segment.get()));
		when(tagger.initPlugin(eq(MANIFEST_URL), eq(SEGMENT_URL), eq(""), eq("_plugin/cache"), any(), eq("_plugin/delta/cache")))
			.thenAnswer(i -> {
				if (segment.get() == null) segment.set(segment(i.<Cache>getArgument(4).getId(), "_plugin/delta/cache"));
				return segment.get();
			});
		var res = mock(Fetch.FileRequest.class);
		when(res.getMimeType()).thenReturn("application/x-mpegURL");
		when(res.getInputStream()).thenReturn(new ByteArrayInputStream(MANIFEST.getBytes(UTF_8)));
		when(fileCache.fetch.doScrape(MANIFEST_URL, "")).thenReturn(res);
		when(storage.store(eq(""), eq("cache"), any(InputStream.class))).thenReturn("manifest");
		when(storage.get("", "cache", "manifest")).thenReturn(MANIFEST.getBytes(UTF_8));
		when(storage.stream("", "cache", "manifest")).thenAnswer(i -> new ByteArrayInputStream(new byte[0]));
	}

	String rewrittenManifest() throws IOException {
		assertThat(fileCache.fetch(MANIFEST_URL, "", true)).isNotNull();
		var data = ArgumentCaptor.forClass(byte[].class);
		verify(storage).overwrite(eq(""), eq("cache"), eq("manifest"), data.capture());
		var cache = ArgumentCaptor.forClass(Cache.class);
		verify(tagger).plugin(eq(MANIFEST_URL), eq(""), eq("_plugin/cache"), cache.capture(), eq("-_plugin/delta/cache"));
		assertThat(cache.getValue().getId()).isEqualTo("manifest");
		verify(tagger, never()).attachError(anyString(), ArgumentMatchers.<Ref>any(), anyString());
		return new String(data.getValue(), UTF_8);
	}

	@Test
	void testManifestProxy() throws IOException {
		var ref = new Ref();
		ref.setUrl(SEGMENT_URL);
		segment.set(ref);

		assertThat(rewrittenManifest())
			.contains("/api/v1/proxy?url=https%3A%2F%2Fexample.com%2Fvideo%2Fseg0.ts");
		verify(tagger).internalTag(SEGMENT_URL, "", "_plugin/delta/cache");
		// Without a CDN route, segments are not reserved or checked in storage
		verify(tagger, never()).initPlugin(any(), any(), any(), any(), any(), any());
		verify(storage, never()).exists(any(), any(), any());
	}

	@Test
	void testManifestCdn() throws IOException {
		when(storage.getCdnUrl(eq(""), eq("cache"), anyString()))
			.thenAnswer(i -> "https://cdn.example.com/default/cache/" + i.getArgument(2));

		var manifest = rewrittenManifest();

		var cache = ArgumentCaptor.forClass(Cache.class);
		verify(tagger).initPlugin(eq(MANIFEST_URL), eq(SEGMENT_URL), eq(""), eq("_plugin/cache"), cache.capture(), eq("_plugin/delta/cache"));
		assertThat(segment.get().getPlugin("_plugin/cache", Cache.class).getId()).isEqualTo(cache.getValue().getId());
		assertThat(manifest).contains("https://cdn.example.com/default/cache/" + cache.getValue().getId() + "\n");
		assertThat(manifest).doesNotContain("/api/v1/proxy");
		verify(tagger, never()).internalTag(SEGMENT_URL, "", "_plugin/delta/cache");
	}

	@Test
	void testManifestCdnConcurrentReservation() throws IOException {
		when(storage.getCdnUrl(eq(""), eq("cache"), anyString()))
			.thenAnswer(i -> "https://cdn.example.com/default/cache/" + i.getArgument(2));
		// Another pod reserved the segment first
		when(tagger.initPlugin(eq(MANIFEST_URL), eq(SEGMENT_URL), eq(""), eq("_plugin/cache"), any(), eq("_plugin/delta/cache")))
			.thenAnswer(i -> {
				segment.set(segment("winner", "_plugin/delta/cache"));
				return segment.get();
			});

		assertThat(rewrittenManifest()).contains("https://cdn.example.com/default/cache/winner\n");
		verify(tagger, never()).internalTag(SEGMENT_URL, "", "_plugin/delta/cache");
	}

	@Test
	void testManifestCdnExistingCache() throws IOException {
		segment.set(segment("seg"));
		when(storage.exists("", "cache", "seg")).thenReturn(true);
		when(storage.getCdnUrl(eq(""), eq("cache"), anyString()))
			.thenAnswer(i -> "https://cdn.example.com/default/cache/" + i.getArgument(2));

		assertThat(rewrittenManifest()).contains("https://cdn.example.com/default/cache/seg\n");
	}

	@Test
	void testManifestCdnExistingCacheStoredElsewhere() throws IOException {
		// Cached before the CDN route was configured, so it is not in the CDN bucket
		segment.set(segment("seg"));
		when(storage.getCdnUrl(eq(""), eq("cache"), anyString()))
			.thenAnswer(i -> "https://cdn.example.com/default/cache/" + i.getArgument(2));

		assertThat(rewrittenManifest())
			.contains("/api/v1/proxy?url=https%3A%2F%2Fexample.com%2Fvideo%2Fseg0.ts")
			.doesNotContain("https://cdn.example.com");
		verify(storage).exists("", "cache", "seg");
	}

	Ref segment(String id, String ...tags) {
		var ref = new Ref();
		ref.setUrl(SEGMENT_URL);
		ref.setPlugin("_plugin/cache", Cache.builder().id(id).build());
		for (var tag : tags) ref.addTag(tag);
		return ref;
	}
}
