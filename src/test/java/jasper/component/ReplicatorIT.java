package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import feign.Response;
import jasper.IntegrationTest;
import jasper.client.JasperClient;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.plugin.Origin;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link Replicator} fetch method.
 * Tests the proxying logic for cache and non-cache URLs with and without FileCache.
 */
@IntegrationTest
@ActiveProfiles({"file-cache", "storage", "test"})
class ReplicatorIT {

	@Autowired
	private Replicator replicator;

	@Autowired
	private RefRepository refRepository;

	@Autowired
	private PluginRepository pluginRepository;

	@Autowired
	private Optional<FileCache> fileCache;

	@MockBean
	private JasperClient jasperClient;

	@MockBean
	private TunnelClient tunnelClient;

	private static final String TEST_ORIGIN = "@test";
	private static final String CACHE_URL = "cache:test-cache-id";
	private static final String NON_CACHE_URL = "https://example.com/test.txt";
	private static final byte[] TEST_CONTENT = "Test file content for replication testing".getBytes(StandardCharsets.UTF_8);
	private static final String TEST_MIME_TYPE = "text/plain";

	@BeforeEach
	void setup() throws Exception {
		refRepository.deleteAll();
		pluginRepository.deleteAll();

		// Create the _plugin/cache plugin required by FileCache
		var cachePlugin = new Plugin();
		cachePlugin.setTag("_plugin/cache");
		var mapper = new ObjectMapper();
		cachePlugin.setSchema((ObjectNode) mapper.readTree("""
		{
		    "optionalProperties": {
		      "id": { "type": "string" },
		      "mimeType": { "type": "string" },
		      "contentLength": { "type": "uint32" },
		      "ban": { "type": "boolean" },
		      "noStore": { "type": "boolean" },
		      "thumbnail": { "type": "boolean" }
		    }
		}"""));
		pluginRepository.save(cachePlugin);

		// Create the +plugin/origin/pull plugin required by Replicator
		var pullPlugin = new Plugin();
		pullPlugin.setTag("+plugin/origin/pull");
		pullPlugin.setSchema((ObjectNode) mapper.readTree("""
		{
		    "optionalProperties": {
		      "remote": { "type": "string" },
		      "local": { "type": "string" },
		      "proxy": { "type": "string" }
		    }
		}"""));
		pluginRepository.save(pullPlugin);

		// Create a remote origin ref with proper plugins
		var remoteRef = new Ref();
		remoteRef.setUrl("https://example.com");
		remoteRef.setOrigin(TEST_ORIGIN);
		remoteRef.addPlugin("+plugin/origin/pull", Origin.builder()
			.remote("")
			.local("")
			.build());
		refRepository.save(remoteRef);

		// Mock TunnelClient to simply execute the request without tunneling
		when(tunnelClient.proxy(any(), any())).thenAnswer(invocation -> {
			TunnelClient.ProxyRequest request = invocation.getArgument(1);
			request.go(new URI("http://localhost:8080"));
			return null;
		});
	}

	@Test
	void testFetchCacheUrlWithFileCache() throws Exception {
		// Given: A mock response for a cache URL
		Response mockResponse = createMockResponse(TEST_CONTENT, TEST_MIME_TYPE);
		when(jasperClient.fetch(any(URI.class), eq(CACHE_URL), anyString()))
			.thenReturn(mockResponse);

		// When: Fetching a cache URL
		var remoteRef = refRepository.findOneByUrlAndOrigin("https://example.com", TEST_ORIGIN).get();
		var result = replicator.fetch(CACHE_URL, remoteRef);

		// Then: The result should contain the fetched content
		assertThat(result).isNotNull();
		assertThat(result.getMimeType()).isEqualTo(TEST_MIME_TYPE);
		
		// Verify the content is accessible via FileCache
		byte[] content = result.getInputStream().readAllBytes();
		assertThat(content).isEqualTo(TEST_CONTENT);
		
		// Verify the cache was stored in FileCache
		assertThat(fileCache).isPresent();
		assertThat(refRepository.existsByUrlAndOrigin(CACHE_URL, "")).isTrue();
	}

	@Test
	void testFetchNonCacheUrlWithFileCache() throws Exception {
		// Given: A mock response for a non-cache URL (the bugfix scenario)
		Response mockResponse = createMockResponse(TEST_CONTENT, TEST_MIME_TYPE);
		when(jasperClient.fetch(any(URI.class), eq(NON_CACHE_URL), anyString()))
			.thenReturn(mockResponse);

		// When: Fetching a non-cache URL (triggers the overwrite path)
		var remoteRef = refRepository.findOneByUrlAndOrigin("https://example.com", TEST_ORIGIN).get();
		var result = replicator.fetch(NON_CACHE_URL, remoteRef);

		// Then: The result should contain the fetched content
		assertThat(result).isNotNull();
		assertThat(result.getMimeType()).isEqualTo(TEST_MIME_TYPE);
		
		// Verify the content is accessible
		byte[] content = result.getInputStream().readAllBytes();
		assertThat(content).isEqualTo(TEST_CONTENT);
		
		// Verify the cache was created via overwrite method
		assertThat(fileCache).isPresent();
		// The non-cache URL should have a cache entry created via overwrite
		assertThat(refRepository.existsByUrlAndOrigin(NON_CACHE_URL, "")).isTrue();
		
		// Verify the cache ref has the _plugin/cache plugin
		var cacheRef = refRepository.findOneByUrlAndOrigin(NON_CACHE_URL, "").get();
		assertThat(cacheRef.hasPlugin("_plugin/cache")).isTrue();
	}

	@Test
	void testFetchWithEmptyResponseAndFileCache() throws Exception {
		// Given: A mock response with null body
		Response mockResponse = createMockResponseWithNullBody(TEST_MIME_TYPE);
		when(jasperClient.fetch(any(URI.class), eq(CACHE_URL), anyString()))
			.thenReturn(mockResponse);

		// When: Fetching with empty response
		var remoteRef = refRepository.findOneByUrlAndOrigin("https://example.com", TEST_ORIGIN).get();
		var result = replicator.fetch(CACHE_URL, remoteRef);

		// Then: The result should contain an empty input stream
		assertThat(result).isNotNull();
		assertThat(result.getMimeType()).isEqualTo(TEST_MIME_TYPE);
		
		// Verify empty stream
		byte[] content = result.getInputStream().readAllBytes();
		assertThat(content).isEmpty();
	}

	@Test
	void testOverwriteAndFetchInteraction() throws Exception {
		// Given: A non-cache URL that will trigger overwrite
		Response mockResponse = createMockResponse(TEST_CONTENT, TEST_MIME_TYPE);
		when(jasperClient.fetch(any(URI.class), eq(NON_CACHE_URL), anyString()))
			.thenReturn(mockResponse);

		// When: Fetching triggers overwrite, which creates a cache entry
		var remoteRef = refRepository.findOneByUrlAndOrigin("https://example.com", TEST_ORIGIN).get();
		var result = replicator.fetch(NON_CACHE_URL, remoteRef);

		// Then: Verify the overwrite created a cache entry
		assertThat(result).isNotNull();
		var cacheRef = refRepository.findOneByUrlAndOrigin(NON_CACHE_URL, "").get();
		assertThat(cacheRef.hasPlugin("_plugin/cache")).isTrue();
		
		// Verify subsequent fetch returns the cached content
		var cacheId = cacheRef.getPlugin("_plugin/cache", jasper.plugin.Cache.class).getId();
		assertThat(cacheId).isNotBlank();
		
		// The result should fetch from "cache:" + id
		byte[] content = result.getInputStream().readAllBytes();
		assertThat(content).isEqualTo(TEST_CONTENT);
		
		// Verify we can fetch the cache directly
		try (var cachedStream = fileCache.get().fetch("cache:" + cacheId, "")) {
			byte[] cachedContent = cachedStream.readAllBytes();
			assertThat(cachedContent).isEqualTo(TEST_CONTENT);
		}
	}

	private Response createMockResponse(byte[] content, String mimeType) throws IOException {
		Response mockResponse = mock(Response.class);
		Response.Body mockBody = mock(Response.Body.class);
		
		when(mockBody.asInputStream()).thenReturn(new ByteArrayInputStream(content));
		when(mockBody.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(content));
		
		when(mockResponse.getBody()).thenReturn(mockBody);
		
		// Mock headers with content type
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType(mimeType));
		
		when(mockResponse.getHeaders()).thenAnswer(invocation -> {
			var feignHeaders = mock(feign.Response.Headers.class);
			when(feignHeaders.getContentType()).thenReturn(org.springframework.http.MediaType.parseMediaType(mimeType));
			return feignHeaders;
		});
		
		return mockResponse;
	}

	private Response createMockResponseWithNullBody(String mimeType) {
		Response mockResponse = mock(Response.class);
		when(mockResponse.getBody()).thenReturn(null);
		
		when(mockResponse.getHeaders()).thenAnswer(invocation -> {
			var feignHeaders = mock(feign.Response.Headers.class);
			when(feignHeaders.getContentType()).thenReturn(org.springframework.http.MediaType.parseMediaType(mimeType));
			return feignHeaders;
		});
		
		return mockResponse;
	}
}
