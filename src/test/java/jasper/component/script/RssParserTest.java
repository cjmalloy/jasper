package jasper.component.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import feign.FeignException;
import jasper.client.JasperClient;
import jasper.client.dto.JasperMapper;
import jasper.component.HttpClientFactory;
import jasper.component.Ingest;
import jasper.component.Tagger;
import jasper.config.JacksonConfiguration;
import jasper.domain.Ref;
import jasper.plugin.Feed;
import jasper.repository.RefRepository;
import jasper.security.HostCheck;
import jasper.service.dto.RefReplDto;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static jasper.security.Auth.LOCAL_ORIGIN_HEADER;
import static jasper.security.Auth.USER_TAG_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RssParserTest {

	@InjectMocks
	RssParser rssParser;

	@Mock
	JasperClient jasperClient;

	@Mock
	JasperMapper mapper;

	@Mock
	Tagger tagger;

	@Mock
	HostCheck hostCheck;

	@Mock
	HttpClientFactory httpClientFactory;

	@Mock
	CloseableHttpClient httpClient;

	@Mock
	CloseableHttpResponse httpResponse;

	@Mock
	HttpEntity httpEntity;

	@Mock
	StatusLine statusLine;

	@Mock
	Ingest ingest;

	@Mock
	RefRepository refRepository;

	static final String FEED_URL = "https://example.com/feed.xml";
	static final String ORIGIN = "";

	static final ObjectMapper om = new ObjectMapper();

	static final String RSS_FEED = """
		<?xml version="1.0" encoding="UTF-8"?>
		<rss version="2.0">
		  <channel>
		    <title>Test Feed</title>
		    <link>https://example.com</link>
		    <description>Test</description>
		    <item>
		      <title>Test Entry</title>
		      <link>https://example.com/entry1</link>
		      <pubDate>Mon, 01 Jan 2024 00:00:00 GMT</pubDate>
		    </item>
		  </channel>
		</rss>
		""";

	@BeforeAll
	static void setUpJackson() {
		ReflectionTestUtils.setField(JacksonConfiguration.class, "om", om);
	}

	@BeforeEach
	void setUp() throws Exception {
		MockitoAnnotations.openMocks(this);
		ReflectionTestUtils.setField(rssParser, "api", "http://localhost:8081");
		when(httpClientFactory.getClient()).thenReturn(httpClient);
		when(mapper.domainToDto(any(Ref.class))).thenReturn(new RefReplDto());
		when(tagger.progress(any(), any())).thenReturn(mock(Tagger.Progress.class));
	}

	void setUpValidHttpResponse() throws Exception {
		when(hostCheck.validHost(any(URI.class))).thenReturn(true);
		when(statusLine.getStatusCode()).thenReturn(200);
		when(httpResponse.getStatusLine()).thenReturn(statusLine);
		when(httpResponse.getEntity()).thenReturn(httpEntity);
		when(httpResponse.getFirstHeader(anyString())).thenReturn(null);
		when(httpEntity.getContent()).thenAnswer(inv -> new ByteArrayInputStream(RSS_FEED.getBytes()));
		when(httpClient.execute(any(HttpGet.class))).thenReturn(httpResponse);
		when(refRepository.existsByUrlAndOrigin(anyString(), anyString())).thenReturn(false);
	}

	Ref feedRef(String... tags) {
		var ref = new Ref();
		ref.setUrl(FEED_URL);
		ref.setOrigin(ORIGIN);
		ref.setPublished(Instant.EPOCH);
		ref.setTags(new ArrayList<>(List.of(tags)));
		return ref;
	}

	Ref feedWithAddTag(String addTag, String... authorTags) {
		var ref = feedRef(authorTags);
		var feed = new Feed();
		feed.setAddTags(new ArrayList<>(List.of(addTag)));
		ObjectNode plugins = om.createObjectNode();
		plugins.set("plugin/script/feed", om.convertValue(feed, ObjectNode.class));
		ref.setPlugins(plugins);
		return ref;
	}

	@Test
	void testInvalidHost_NeverCallsJasperClient() {
		when(hostCheck.validHost(any(URI.class))).thenReturn(false);
		var feed = feedWithAddTag("_private/tag", "+user/alice");

		rssParser.runScript(feed, "plugin/script/feed");

		verify(jasperClient, never()).refPush(any(), any(), any(), any());
		verify(tagger, never()).attachError(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void testSuccessfulCreate_NoError() throws Exception {
		setUpValidHttpResponse();
		var feed = feedWithAddTag("science", "+user/alice");

		rssParser.runScript(feed, "plugin/script/feed");

		verify(tagger, never()).attachError(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	@SuppressWarnings("unchecked")
	void testCreateRef_AuthorHeaderIncludesFirstAuthor() throws Exception {
		setUpValidHttpResponse();
		var feed = feedWithAddTag("_private/tag", "+user/alice");

		rssParser.runScript(feed, "plugin/script/feed");

		var captor = ArgumentCaptor.forClass(Map.class);
		verify(jasperClient).refPush(any(URI.class), captor.capture(), anyString(), anyList());
		assertThat(captor.getValue()).containsEntry(USER_TAG_HEADER, "+user/alice");
		assertThat(captor.getValue()).containsEntry(LOCAL_ORIGIN_HEADER, ORIGIN);
	}

	@Test
	@SuppressWarnings("unchecked")
	void testCreateRef_EmptyAuthorHeader_WhenNoAuthor() throws Exception {
		setUpValidHttpResponse();
		var feed = feedWithAddTag("_private/tag" /* no author tags */);

		rssParser.runScript(feed, "plugin/script/feed");

		var captor = ArgumentCaptor.forClass(Map.class);
		verify(jasperClient).refPush(any(URI.class), captor.capture(), anyString(), anyList());
		assertThat(captor.getValue()).containsEntry(USER_TAG_HEADER, "");
	}

	@Test
	void testForbiddenResponse_ErrorAttached() throws Exception {
		setUpValidHttpResponse();
		var feed = feedWithAddTag("_private/tag", "+user/alice");
		var forbiddenEx = mock(FeignException.Forbidden.class);
		when(forbiddenEx.contentUTF8()).thenReturn("Forbidden");
		doThrow(forbiddenEx).when(jasperClient).refPush(any(), any(), any(), any());

		rssParser.runScript(feed, "plugin/script/feed");

		verify(tagger).attachError(eq(FEED_URL), eq(ORIGIN), eq("Author not authorized to add tags"), anyString());
	}

	@Test
	void testAlreadyExists_SilentlySkipped() throws Exception {
		setUpValidHttpResponse();
		var feed = feedWithAddTag("science", "+user/alice");
		when(refRepository.existsByUrlAndOrigin(anyString(), anyString())).thenReturn(true);

		rssParser.runScript(feed, "plugin/script/feed");

		verify(jasperClient, never()).refPush(any(), any(), any(), any());
		verify(tagger, never()).attachError(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void testProgress_ReportsProcessedOfTotalIncludingSkipped() throws Exception {
		setUpValidHttpResponse();
		when(httpEntity.getContent()).thenAnswer(inv -> new ByteArrayInputStream("""
			<?xml version="1.0" encoding="UTF-8"?>
			<rss version="2.0">
			  <channel>
			    <title>Test Feed</title>
			    <link>https://example.com</link>
			    <description>Test</description>
			    <item>
			      <title>Entry 3</title>
			      <link>https://example.com/entry3</link>
			      <pubDate>Wed, 03 Jan 2024 00:00:00 GMT</pubDate>
			    </item>
			    <item>
			      <title>Entry 2</title>
			      <link>https://example.com/entry2</link>
			      <pubDate>Tue, 02 Jan 2024 00:00:00 GMT</pubDate>
			    </item>
			    <item>
			      <title>Entry 1</title>
			      <link>https://example.com/entry1</link>
			      <pubDate>Mon, 01 Jan 2024 00:00:00 GMT</pubDate>
			    </item>
			  </channel>
			</rss>
			""".getBytes()));
		when(refRepository.existsByUrlAndOrigin(eq("https://example.com/entry2"), anyString())).thenReturn(true);
		var progress = mock(Tagger.Progress.class);
		when(tagger.progress(FEED_URL, ORIGIN)).thenReturn(progress);
		var feed = feedWithAddTag("science", "+user/alice");

		rssParser.runScript(feed, "plugin/script/feed");

		var order = inOrder(progress);
		order.verify(progress).update(0, 3);
		order.verify(progress).update(1, 3);
		order.verify(progress).update(2, 3);
		order.verify(progress).close();
		verify(progress, times(3)).update(anyInt(), anyInt());
		verify(jasperClient, times(2)).refPush(any(), any(), any(), any());
	}

	@Test
	void testEtag_StoredInInternalResponse() throws Exception {
		setUpValidHttpResponse();
		var etag = mock(org.apache.http.Header.class);
		when(etag.getValue()).thenReturn("\"abc\"");
		when(httpResponse.getFirstHeader(HttpHeaders.ETAG)).thenReturn(etag);
		var feed = feedWithAddTag("science", "+user/alice");

		rssParser.runScript(feed, "plugin/script/feed");

		verify(tagger).internalResponse("_plugin/script/feed/etag", ORIGIN, FEED_URL, "\"abc\"");
		verify(ingest, never()).update(any(), any());
		assertThat(feed.getPlugin("plugin/script/feed").has("etag")).isFalse();
	}

	@Test
	void testEtag_SentFromInternalResponse() throws Exception {
		setUpValidHttpResponse();
		when(tagger.getInternalResponse("_plugin/script/feed/etag", ORIGIN, FEED_URL)).thenReturn("\"abc\"");
		var etag = mock(org.apache.http.Header.class);
		when(etag.getValue()).thenReturn("\"abc\"");
		when(httpResponse.getFirstHeader(HttpHeaders.ETAG)).thenReturn(etag);
		var feed = feedWithAddTag("science", "+user/alice");

		rssParser.runScript(feed, "plugin/script/feed");

		var captor = ArgumentCaptor.forClass(HttpGet.class);
		verify(httpClient).execute(captor.capture());
		assertThat(captor.getValue().getFirstHeader(HttpHeaders.IF_NONE_MATCH).getValue()).isEqualTo("\"abc\"");
		verify(tagger, never()).internalResponse(any(), any(), any(), any());
	}

	@Test
	void testEtag_ClearedWhenMissing() throws Exception {
		setUpValidHttpResponse();
		when(tagger.getInternalResponse("_plugin/script/feed/etag", ORIGIN, FEED_URL)).thenReturn("\"abc\"");
		var feed = feedWithAddTag("science", "+user/alice");

		rssParser.runScript(feed, "plugin/script/feed");

		verify(tagger).internalResponse("_plugin/script/feed/etag", ORIGIN, FEED_URL, null);
	}

	@Test
	void testEtag_Disabled() throws Exception {
		setUpValidHttpResponse();
		when(tagger.getInternalResponse(any(), any(), any())).thenReturn("\"abc\"");
		var etag = mock(org.apache.http.Header.class);
		when(etag.getValue()).thenReturn("\"def\"");
		when(httpResponse.getFirstHeader(HttpHeaders.ETAG)).thenReturn(etag);
		var feed = feedRef("+user/alice");
		var config = new Feed();
		config.setDisableEtag(true);
		ObjectNode plugins = om.createObjectNode();
		plugins.set("plugin/script/feed", om.convertValue(config, ObjectNode.class));
		feed.setPlugins(plugins);

		rssParser.runScript(feed, "plugin/script/feed");

		var captor = ArgumentCaptor.forClass(HttpGet.class);
		verify(httpClient).execute(captor.capture());
		assertThat(captor.getValue().getFirstHeader(HttpHeaders.IF_NONE_MATCH)).isNull();
		verify(tagger, never()).internalResponse(any(), any(), any(), any());
	}

	static final String YOUTUBE_FEED = """
		<?xml version="1.0" encoding="UTF-8"?>
		<feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns="http://www.w3.org/2005/Atom">
		  <title>Channel</title>
		  <link rel="alternate" href="https://www.youtube.com/channel/UC123"/>
		  <entry>
		    <id>yt:video:short1</id>
		    <yt:videoId>short1</yt:videoId>
		    <title>Short</title>
		    <link rel="alternate" href="https://www.youtube.com/shorts/short1"/>
		    <published>2024-01-02T00:00:00+00:00</published>
		  </entry>
		  <entry>
		    <id>yt:video:video1</id>
		    <yt:videoId>video1</yt:videoId>
		    <title>Video</title>
		    <link rel="alternate" href="https://www.youtube.com/watch?v=video1"/>
		    <published>2024-01-01T00:00:00+00:00</published>
		  </entry>
		</feed>
		""";

	List<Ref> scrapeYoutube(boolean video, boolean shorts) throws Exception {
		setUpValidHttpResponse();
		when(httpEntity.getContent()).thenAnswer(inv -> new ByteArrayInputStream(YOUTUBE_FEED.getBytes()));
		var feed = feedRef("+user/alice");
		var config = new Feed();
		config.setScrapeVideo(video);
		config.setScrapeShorts(shorts);
		ObjectNode plugins = om.createObjectNode();
		plugins.set("plugin/script/feed", om.convertValue(config, ObjectNode.class));
		feed.setPlugins(plugins);
		var captor = ArgumentCaptor.forClass(Ref.class);

		rssParser.runScript(feed, "plugin/script/feed");

		verify(mapper, atLeast(0)).domainToDto(captor.capture());
		return captor.getAllValues();
	}

	@Test
	void testYoutube_VideoOnly_SkipsShorts() throws Exception {
		var refs = scrapeYoutube(true, false);

		assertThat(refs).extracting(Ref::getUrl).containsExactly("https://www.youtube.com/watch?v=video1");
		assertThat(refs.getFirst().hasPlugin("plugin/embed")).isTrue();
	}

	@Test
	void testYoutube_ShortsOnly_SkipsVideos() throws Exception {
		var refs = scrapeYoutube(false, true);

		assertThat(refs).extracting(Ref::getUrl).containsExactly("https://www.youtube.com/shorts/short1");
		assertThat(refs.getFirst().hasPlugin("plugin/embed")).isTrue();
	}

	@Test
	void testYoutube_Both_IncludesAll() throws Exception {
		var refs = scrapeYoutube(true, true);

		assertThat(refs).extracting(Ref::getUrl).containsExactly(
			"https://www.youtube.com/watch?v=video1",
			"https://www.youtube.com/shorts/short1");
		assertThat(refs).allMatch(r -> r.hasPlugin("plugin/embed"));
	}

	@Test
	void testYoutube_Neither_IncludesAllWithoutEmbed() throws Exception {
		var refs = scrapeYoutube(false, false);

		assertThat(refs).hasSize(2);
		assertThat(refs).noneMatch(r -> r.hasPlugin("plugin/embed"));
	}
}
