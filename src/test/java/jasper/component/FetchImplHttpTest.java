package jasper.component;

import jasper.security.HostCheck;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FetchImplHttpTest {

	@InjectMocks
	FetchImplHttp fetch;

	@Mock
	HostCheck hostCheck;

	@Mock
	ConfigCache configs;

	@Mock
	HttpClientFactory httpClientFactory;

	@Mock
	Replicator replicator;

	@Mock
	TorrentFetch torrentFetch;

	@Mock
	CloseableHttpClient httpClient;

	@Mock
	CloseableHttpResponse response;

	@Mock
	StatusLine statusLine;

	@Mock
	Header contentType;

	@Mock
	HttpEntity entity;

	private AutoCloseable mocks;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);
	}

	@AfterEach
	void tearDown() throws Exception {
		mocks.close();
	}

	@Test
	void fetchesMagnetWithTorrentClient() throws Exception {
		var request = mock(Fetch.FileRequest.class);
		var magnet = "magnet:?xt=urn:btih:0123456789012345678901234567890123456789";
		when(torrentFetch.fetch(magnet)).thenReturn(request);

		assertThat(fetch.doScrape(magnet, "")).isSameAs(request);
		verifyNoInteractions(httpClientFactory);
	}

	@Test
	void downloadsHttpTorrentPayload() throws Exception {
		var request = mock(Fetch.FileRequest.class);
		when(hostCheck.validHost(any())).thenReturn(true);
		when(httpClientFactory.getClient()).thenReturn(httpClient);
		when(httpClient.execute(any(HttpUriRequest.class))).thenReturn(response);
		when(response.getStatusLine()).thenReturn(statusLine);
		when(statusLine.getStatusCode()).thenReturn(200);
		when(response.getFirstHeader("Content-Type")).thenReturn(contentType);
		when(contentType.getValue()).thenReturn("application/x-bittorrent; charset=binary");
		when(response.getEntity()).thenReturn(entity);
		when(entity.getContent()).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
		when(torrentFetch.fetch(any(ByteArrayInputStream.class))).thenReturn(request);

		assertThat(fetch.doScrape("https://example.com/file", "")).isSameAs(request);
		verify(response).close();
	}
}
