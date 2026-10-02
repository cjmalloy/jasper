package jasper.component;

import jasper.security.HostCheck;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.net.URI;

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

	@Test
	void downloadsTorrentPathWithoutContentType() throws Exception {
		var request = mock(Fetch.FileRequest.class);
		prepareResponse(response, statusLine, new byte[] {1, 2, 3});
		when(torrentFetch.fetch(any(ByteArrayInputStream.class))).thenReturn(request);

		assertThat(fetch.doScrape("https://example.com/file.torrent", "")).isSameAs(request);
		verify(response).close();
	}

	@Test
	void returnsNonTorrentHttpResponseNormally() throws Exception {
		prepareResponse(response, statusLine, new byte[] {1, 2, 3});

		try (var request = fetch.doScrape("https://example.com/file.bin", "");
			 var input = request.getInputStream()) {
			assertThat(input.readAllBytes()).containsExactly(1, 2, 3);
		}

		verifyNoInteractions(torrentFetch);
	}

	@Test
	void detectsTorrentAfterRedirect() throws Exception {
		var redirect = mock(CloseableHttpResponse.class);
		var redirectStatus = mock(StatusLine.class);
		var location = mock(Header.class);
		var locationElement = mock(HeaderElement.class);
		var request = mock(Fetch.FileRequest.class);
		when(hostCheck.validHost(any())).thenReturn(true);
		when(httpClientFactory.getClient()).thenReturn(httpClient);
		when(httpClient.execute(any(HttpUriRequest.class))).thenReturn(redirect, response);
		when(redirect.getStatusLine()).thenReturn(redirectStatus);
		when(redirectStatus.getStatusCode()).thenReturn(301);
		when(redirect.getFirstHeader("Location")).thenReturn(location);
		when(location.getElements()).thenReturn(new HeaderElement[] {locationElement});
		when(locationElement.getValue()).thenReturn("/downloads/file.torrent");
		when(response.getStatusLine()).thenReturn(statusLine);
		when(statusLine.getStatusCode()).thenReturn(200);
		when(response.getEntity()).thenReturn(entity);
		when(entity.getContent()).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
		when(torrentFetch.fetch(any(ByteArrayInputStream.class))).thenReturn(request);

		assertThat(fetch.doScrape("https://example.com/start", "")).isSameAs(request);
		var requests = ArgumentCaptor.forClass(HttpUriRequest.class);
		verify(httpClient, times(2)).execute(requests.capture());
		assertThat(requests.getAllValues()).extracting(HttpUriRequest::getURI)
			.containsExactly(URI.create("https://example.com/start"), URI.create("https://example.com/downloads/file.torrent"));
		verify(redirect).close();
		verify(response).close();
	}

	private void prepareResponse(CloseableHttpResponse response, StatusLine statusLine, byte[] data) throws Exception {
		when(hostCheck.validHost(any())).thenReturn(true);
		when(httpClientFactory.getClient()).thenReturn(httpClient);
		when(httpClient.execute(any(HttpUriRequest.class))).thenReturn(response);
		when(response.getStatusLine()).thenReturn(statusLine);
		when(statusLine.getStatusCode()).thenReturn(200);
		when(response.getEntity()).thenReturn(entity);
		when(entity.getContent()).thenReturn(new ByteArrayInputStream(data));
	}
}
