package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.config.JacksonConfiguration;
import jasper.domain.Ref;
import jasper.plugin.Cache;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FileCacheTest {

	static final String MANIFEST = "#EXTM3U\n#EXTINF:10,\nseg0.ts\n";

	FileCache fileCache;
	Storage storage;
	Tagger tagger;

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
		var res = mock(Fetch.FileRequest.class);
		when(res.getMimeType()).thenReturn("application/x-mpegURL");
		when(res.getInputStream()).thenReturn(new ByteArrayInputStream(MANIFEST.getBytes(UTF_8)));
		when(fileCache.fetch.doScrape("https://example.com/video/index.m3u8", "")).thenReturn(res);
		when(storage.store(eq(""), eq("cache"), any(InputStream.class))).thenReturn("manifest");
		when(storage.get("", "cache", "manifest")).thenReturn(MANIFEST.getBytes(UTF_8));
	}

	String rewrittenManifest() throws IOException {
		fileCache.fetch("https://example.com/video/index.m3u8", "", true);
		var data = ArgumentCaptor.forClass(byte[].class);
		verify(storage).overwrite(eq(""), eq("cache"), eq("manifest"), data.capture());
		return new String(data.getValue(), UTF_8);
	}

	@Test
	void testManifestProxy() throws IOException {
		assertThat(rewrittenManifest())
			.contains("/api/v1/proxy?url=https%3A%2F%2Fexample.com%2Fvideo%2Fseg0.ts");
	}

	@Test
	void testManifestCdn() throws IOException {
		when(storage.getCdnUrl(eq(""), eq("cache"), anyString()))
			.thenAnswer(i -> "https://cdn.example.com/default/cache/" + i.getArgument(2));
		when(tagger.initPlugin(eq("https://example.com/video/index.m3u8"), eq("https://example.com/video/seg0.ts"), eq(""), eq("_plugin/cache"), any(), eq("_plugin/delta/cache")))
			.thenAnswer(i -> segment(i.<Cache>getArgument(4).getId(), "_plugin/delta/cache"));

		var manifest = rewrittenManifest();

		var cache = ArgumentCaptor.forClass(Cache.class);
		verify(tagger).initPlugin(eq("https://example.com/video/index.m3u8"), eq("https://example.com/video/seg0.ts"), eq(""), eq("_plugin/cache"), cache.capture(), eq("_plugin/delta/cache"));
		assertThat(manifest).contains("https://cdn.example.com/default/cache/" + cache.getValue().getId() + "\n");
		assertThat(manifest).doesNotContain("/api/v1/proxy");
	}

	@Test
	void testManifestCdnConcurrentReservation() throws IOException {
		when(storage.getCdnUrl(eq(""), eq("cache"), anyString()))
			.thenAnswer(i -> "https://cdn.example.com/default/cache/" + i.getArgument(2));
		// Another pod reserved the segment first
		when(tagger.initPlugin(eq("https://example.com/video/index.m3u8"), eq("https://example.com/video/seg0.ts"), eq(""), eq("_plugin/cache"), any(), eq("_plugin/delta/cache")))
			.thenReturn(segment("winner", "_plugin/delta/cache"));

		assertThat(rewrittenManifest()).contains("https://cdn.example.com/default/cache/winner\n");
	}

	@Test
	void testManifestCdnExistingCache() throws IOException {
		when(fileCache.refRepository.findOneByUrlAndOrigin("https://example.com/video/seg0.ts", "")).thenReturn(Optional.of(segment("seg")));
		when(storage.exists("", "cache", "seg")).thenReturn(true);
		when(storage.getCdnUrl("", "cache", "seg")).thenReturn("https://cdn.example.com/default/cache/seg");

		assertThat(rewrittenManifest()).contains("https://cdn.example.com/default/cache/seg\n");
	}

	@Test
	void testManifestCdnExistingCacheStoredElsewhere() throws IOException {
		// Cached before the CDN route was configured, so it is not in the CDN bucket
		when(fileCache.refRepository.findOneByUrlAndOrigin("https://example.com/video/seg0.ts", "")).thenReturn(Optional.of(segment("seg")));
		when(storage.getCdnUrl("", "cache", "seg")).thenReturn("https://cdn.example.com/default/cache/seg");

		assertThat(rewrittenManifest())
			.contains("/api/v1/proxy?url=https%3A%2F%2Fexample.com%2Fvideo%2Fseg0.ts")
			.doesNotContain("https://cdn.example.com");
	}

	Ref segment(String id, String ...tags) {
		var ref = new Ref();
		ref.setUrl("https://example.com/video/seg0.ts");
		ref.setPlugin("_plugin/cache", Cache.builder().id(id).build());
		for (var tag : tags) ref.addTag(tag);
		return ref;
	}
}
