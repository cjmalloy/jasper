package jasper.web.rest;

import jasper.IntegrationTest;
import jasper.component.FileCache;
import jasper.domain.Ref;
import jasper.plugin.Cache;
import jasper.repository.RefRepository;
import jasper.service.ProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link ProxyController}.
 * Tests the range request functionality and edge cases.
 */
@WithMockUser("+user/tester")
@AutoConfigureMockMvc
@IntegrationTest
class ProxyControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private ProxyService proxyService;

	@Autowired
	private RefRepository refRepository;

	private static final String TEST_URL = "https://example.com/test.txt";
	private static final String TEST_ORIGIN = "";
	private static final byte[] TEST_CONTENT = "This is a test file with some content for range request testing. It has enough data to test various range scenarios.".getBytes();

	@BeforeEach
	void setup() {
		refRepository.deleteAll();
		
		// Create a test Ref for the URL
		var ref = new Ref();
		ref.setUrl(TEST_URL);
		ref.setOrigin(TEST_ORIGIN);
		ref.setTitle("test.txt");
		refRepository.save(ref);

		// Mock ProxyService responses
		when(proxyService.fetch(eq(TEST_URL), eq(TEST_ORIGIN), eq(false)))
			.thenAnswer(invocation -> new ByteArrayInputStream(TEST_CONTENT));

		when(proxyService.stat(eq(TEST_URL), eq(TEST_ORIGIN), eq(false)))
			.thenReturn(null);

		var cache = new Cache();
		cache.setContentLength((long) TEST_CONTENT.length);
		cache.setMimeType("text/plain");
		when(proxyService.cache(eq(TEST_URL), eq(TEST_ORIGIN), eq(false)))
			.thenReturn(cache);
	}

	@Test
	void testFetchWithoutRangeHeader() throws Exception {
		mockMvc
			.perform(get("/api/v1/proxy")
				.param("url", TEST_URL)
				.param("origin", TEST_ORIGIN))
			.andExpect(status().isOk())
			.andExpect(header().string("Accept-Ranges", "bytes"))
			.andExpect(header().exists("Content-Disposition"))
			.andExpect(content().contentType(MediaType.TEXT_PLAIN))
			.andExpect(content().bytes(TEST_CONTENT));
	}

	@Test
	void testValidRangeRequestStartAndEnd() throws Exception {
		// Test: bytes=0-99 (first 100 bytes)
		mockMvc
			.perform(get("/api/v1/proxy")
				.param("url", TEST_URL)
				.param("origin", TEST_ORIGIN)
				.header("Range", "bytes=0-99"))
			.andExpect(status().isPartialContent())
			.andExpect(header().string("Accept-Ranges", "bytes"))
			.andExpect(header().string("Content-Range", "bytes 0-99/" + TEST_CONTENT.length))
			.andExpect(header().string("Content-Length", "100"))
			.andExpect(content().contentType(MediaType.TEXT_PLAIN));
	}

	@Test
	void testValidRangeRequestOnlyStart() throws Exception {
		// Test: bytes=100- (from byte 100 to end)
		int start = 100;
		long expectedLength = TEST_CONTENT.length - start;

		mockMvc
			.perform(get("/api/v1/proxy")
				.param("url", TEST_URL)
				.param("origin", TEST_ORIGIN)
				.header("Range", "bytes=" + start + "-"))
			.andExpect(status().isPartialContent())
			.andExpect(header().string("Accept-Ranges", "bytes"))
			.andExpect(header().string("Content-Range", "bytes " + start + "-" + (TEST_CONTENT.length - 1) + "/" + TEST_CONTENT.length))
			.andExpect(header().string("Content-Length", String.valueOf(expectedLength)))
			.andExpect(content().contentType(MediaType.TEXT_PLAIN));
	}

	@Test
	void testValidRangeRequestSingleByte() throws Exception {
		// Test: bytes=0-0 (first byte only)
		mockMvc
			.perform(get("/api/v1/proxy")
				.param("url", TEST_URL)
				.param("origin", TEST_ORIGIN)
				.header("Range", "bytes=0-0"))
			.andExpect(status().isPartialContent())
			.andExpect(header().string("Accept-Ranges", "bytes"))
			.andExpect(header().string("Content-Range", "bytes 0-0/" + TEST_CONTENT.length))
			.andExpect(header().string("Content-Length", "1"))
			.andExpect(content().contentType(MediaType.TEXT_PLAIN));
	}

	@Test
	void testValidRangeRequestAtBoundary() throws Exception {
		// Test: Range at the end of file
		int start = TEST_CONTENT.length - 10;
		int end = TEST_CONTENT.length - 1;

		mockMvc
			.perform(get("/api/v1/proxy")
				.param("url", TEST_URL)
				.param("origin", TEST_ORIGIN)
				.header("Range", "bytes=" + start + "-" + end))
			.andExpect(status().isPartialContent())
			.andExpect(header().string("Accept-Ranges", "bytes"))
			.andExpect(header().string("Content-Range", "bytes " + start + "-" + end + "/" + TEST_CONTENT.length))
			.andExpect(header().string("Content-Length", "10"))
			.andExpect(content().contentType(MediaType.TEXT_PLAIN));
	}

	@Test
	void testInvalidRangeStartGreaterThanEnd() throws Exception {
		// Test: start > end should return 416
		mockMvc
			.perform(get("/api/v1/proxy")
				.param("url", TEST_URL)
				.param("origin", TEST_ORIGIN)
				.header("Range", "bytes=100-50"))
			.andExpect(status().isRequestedRangeNotSatisfiable())
			.andExpect(header().string("Content-Range", "bytes */" + TEST_CONTENT.length));
	}

	@Test
	void testInvalidRangeStartEqualsContentLength() throws Exception {
		// Test: start >= contentLength should return 416
		mockMvc
			.perform(get("/api/v1/proxy")
				.param("url", TEST_URL)
				.param("origin", TEST_ORIGIN)
				.header("Range", "bytes=" + TEST_CONTENT.length + "-"))
			.andExpect(status().isRequestedRangeNotSatisfiable())
			.andExpect(header().string("Content-Range", "bytes */" + TEST_CONTENT.length));
	}

	@Test
	void testInvalidRangeEndGreaterThanContentLength() throws Exception {
		// Test: end >= contentLength should return 416
		mockMvc
			.perform(get("/api/v1/proxy")
				.param("url", TEST_URL)
				.param("origin", TEST_ORIGIN)
				.header("Range", "bytes=0-" + TEST_CONTENT.length))
			.andExpect(status().isRequestedRangeNotSatisfiable())
			.andExpect(header().string("Content-Range", "bytes */" + TEST_CONTENT.length));
	}

	@Test
	void testInvalidRangeStartBeyondEnd() throws Exception {
		// Test: start > contentLength should return 416
		mockMvc
			.perform(get("/api/v1/proxy")
				.param("url", TEST_URL)
				.param("origin", TEST_ORIGIN)
				.header("Range", "bytes=" + (TEST_CONTENT.length + 100) + "-"))
			.andExpect(status().isRequestedRangeNotSatisfiable())
			.andExpect(header().string("Content-Range", "bytes */" + TEST_CONTENT.length));
	}

	@Test
	void testMalformedRangeHeaderNoBytes() throws Exception {
		// Test: malformed header without "bytes=" prefix should be ignored
		mockMvc
			.perform(get("/api/v1/proxy")
				.param("url", TEST_URL)
				.param("origin", TEST_ORIGIN)
				.header("Range", "0-99"))
			.andExpect(status().isOk())
			.andExpect(header().string("Accept-Ranges", "bytes"))
			.andExpect(content().bytes(TEST_CONTENT));
	}

	@Test
	void testMalformedRangeHeaderInvalidFormat() throws Exception {
		// Test: malformed range format should cause error or be handled gracefully
		// Note: This will throw NumberFormatException, which Spring will handle
		mockMvc
			.perform(get("/api/v1/proxy")
				.param("url", TEST_URL)
				.param("origin", TEST_ORIGIN)
				.header("Range", "bytes=invalid"))
			.andExpect(status().is5xxServerError());
	}

	@Test
	void testRangeRequestWithoutContentLength() throws Exception {
		// Test: Range request when content length is not available should fall back to full content
		when(proxyService.cache(eq(TEST_URL), eq(TEST_ORIGIN), eq(false)))
			.thenReturn(null);

		mockMvc
			.perform(get("/api/v1/proxy")
				.param("url", TEST_URL)
				.param("origin", TEST_ORIGIN)
				.header("Range", "bytes=0-99"))
			.andExpect(status().isOk())
			.andExpect(header().string("Accept-Ranges", "bytes"))
			.andExpect(content().bytes(TEST_CONTENT));
	}

	@Test
	void testRangeRequestWithEmptyEnd() throws Exception {
		// Test: bytes=10- (trailing dash with no end value)
		int start = 10;
		long expectedLength = TEST_CONTENT.length - start;

		mockMvc
			.perform(get("/api/v1/proxy")
				.param("url", TEST_URL)
				.param("origin", TEST_ORIGIN)
				.header("Range", "bytes=" + start + "-"))
			.andExpect(status().isPartialContent())
			.andExpect(header().string("Content-Range", "bytes " + start + "-" + (TEST_CONTENT.length - 1) + "/" + TEST_CONTENT.length))
			.andExpect(header().string("Content-Length", String.valueOf(expectedLength)));
	}

	@Test
	void testFetchNotFound() throws Exception {
		// Test: URL that doesn't exist
		String nonExistentUrl = "https://example.com/nonexistent.txt";
		when(proxyService.fetch(eq(nonExistentUrl), eq(TEST_ORIGIN), eq(false)))
			.thenReturn(null);

		mockMvc
			.perform(get("/api/v1/proxy")
				.param("url", nonExistentUrl)
				.param("origin", TEST_ORIGIN))
			.andExpect(status().isNotFound());
	}
}
