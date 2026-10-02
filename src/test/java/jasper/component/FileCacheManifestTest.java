package jasper.component;

import jasper.domain.Ref;
import jasper.plugin.Cache;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FileCacheManifestTest {
	static final String MANIFEST_URL = "https://example.com/video/index.m3u8";
	static final String MANIFEST = "#EXTM3U\n#EXTINF:10,\nseg0.ts\n#EXTINF:10,\nhttps://other.example.com/seg1.ts\n";

	FileCache fileCache;

	@TempDir
	Path tmpDir;

	@BeforeEach
	void init() throws IOException {
		fileCache = new FileCache();
		fileCache.configs = mock(ConfigCache.class);
		fileCache.refRepository = mock(RefRepository.class);
		fileCache.tagger = mock(Tagger.class);
		fileCache.fetch = mock(Fetch.class);
		when(fileCache.refRepository.findOneByUrlAndOrigin(anyString(), anyString())).thenAnswer(i -> {
			String url = i.getArgument(0);
			if (url.equals(MANIFEST_URL)) return Optional.empty();
			// Segments have been tagged by the time they are scheduled for caching
			return Optional.of(Ref.from(url, i.getArgument(1), "_plugin/cache"));
		});
		var res = mock(Fetch.FileRequest.class);
		when(res.getMimeType()).thenReturn("application/x-mpegURL");
		when(res.getInputStream()).thenReturn(new ByteArrayInputStream(MANIFEST.getBytes(UTF_8)));
		when(fileCache.fetch.doScrape(MANIFEST_URL, "")).thenReturn(res);
	}

	String fetchManifest() throws IOException {
		try (var is = fileCache.fetch(MANIFEST_URL, "")) {
			return new String(is.readAllBytes(), UTF_8);
		}
	}

	@Test
	void testManifestLinksToCdn() throws IOException {
		fileCache.storage = new StorageImplS3(new StorageImplS3Test.FakeS3(), "public", "private", "https://cdn.example.com", tmpDir);

		var lines = fetchManifest().split("\n");

		assertThat(lines).hasSize(5);
		assertThat(lines[2]).startsWith("https://cdn.example.com/default/cache/");
		assertThat(lines[4]).startsWith("https://cdn.example.com/default/cache/");
		assertThat(lines[2]).isNotEqualTo(lines[4]);
		var cache = ArgumentCaptor.forClass(Object.class);
		verify(fileCache.tagger).plugin(eq("https://example.com/video/seg0.ts"), eq(""), eq("_plugin/cache"), cache.capture(), eq("_plugin/delta/cache"));
		assertThat(lines[2]).endsWith("/" + ((Cache) cache.getValue()).getId());
		verify(fileCache.tagger).plugin(eq("https://other.example.com/seg1.ts"), eq(""), eq("_plugin/cache"), any(Cache.class), eq("_plugin/delta/cache"));
	}

	@Test
	void testManifestWithoutCdnUsesProxy() throws IOException {
		fileCache.storage = new StorageImplS3(new StorageImplS3Test.FakeS3(), "public", "private", "", tmpDir);

		var lines = fetchManifest().split("\n");

		assertThat(lines[2]).isEqualTo("/api/v1/proxy?url=https%3A%2F%2Fexample.com%2Fvideo%2Fseg0.ts");
		assertThat(lines[4]).isEqualTo("/api/v1/proxy?url=https%3A%2F%2Fother.example.com%2Fseg1.ts");
		verify(fileCache.tagger, never()).plugin(eq("https://example.com/video/seg0.ts"), anyString(), anyString(), any(), any(String[].class));
	}
}
