package jasper.component.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.component.ConfigCache;
import jasper.component.HttpClientFactory;
import jasper.component.Tagger;
import jasper.config.JacksonConfiguration;
import jasper.domain.Ref;
import jasper.domain.User;
import jasper.plugin.Feed;
import jasper.security.HostCheck;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static jasper.security.AuthoritiesConstants.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RssParserTest {

	@InjectMocks
	RssParser rssParser;

	@Mock
	ConfigCache configCache;

	@Mock
	Tagger tagger;

	@Mock
	HostCheck hostCheck;

	@Mock
	HttpClientFactory httpClientFactory;

	@Mock
	CloseableHttpClient httpClient;

	static final String FEED_URL = "https://example.com/feed.xml";
	static final String ORIGIN = "";

	static final ObjectMapper om = new ObjectMapper();

	@BeforeAll
	static void setUpJackson() {
		ReflectionTestUtils.setField(JacksonConfiguration.class, "om", om);
	}

	@BeforeEach
	void setUp() throws Exception {
		MockitoAnnotations.openMocks(this);
		when(httpClientFactory.getClient()).thenReturn(httpClient);
		when(hostCheck.validHost(any(URI.class))).thenReturn(false);
	}

	Ref feedRef(String ...tags) {
		var ref = new Ref();
		ref.setUrl(FEED_URL);
		ref.setOrigin(ORIGIN);
		ref.setTags(new ArrayList<>(List.of(tags)));
		return ref;
	}

	User user(String role) {
		var u = new User();
		u.setRole(role);
		return u;
	}

	User userWithTagReadAccess(String role, String ...tagReadAccess) {
		var u = user(role);
		u.setTagReadAccess(new ArrayList<>(List.of(tagReadAccess)));
		return u;
	}

	Ref feedWithAddTag(String addTag, String ...authorTags) {
		var ref = feedRef(authorTags);
		var feed = new Feed();
		feed.setAddTags(new ArrayList<>(List.of(addTag)));
		ObjectNode plugins = om.createObjectNode();
		plugins.set("plugin/script/feed", om.convertValue(feed, ObjectNode.class));
		ref.setPlugins(plugins);
		return ref;
	}

	@Test
	void testPublicTagsOnly_Allowed() {
		// Feed with only public addTags requires no author check
		var feed = feedWithAddTag("science");

		rssParser.runScript(feed, "plugin/script/feed");

		verify(tagger, never()).attachError(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void testNonPublicTag_NoAuthor_ErrorAttached() {
		// Feed has private addTag but no author tags on the ref
		var feed = feedWithAddTag("_private/tag" /* no author tags */);

		rssParser.runScript(feed, "plugin/script/feed");

		verify(tagger).attachError(eq(FEED_URL), eq(ORIGIN), eq("Non-public tags require author permission"), anyString());
	}

	@Test
	void testNonPublicTag_WithAuthor_BannedUser_ErrorAttached() {
		// Banned author should not be able to authorize non-public tags
		var feed = feedWithAddTag("_private/tag", "+user/alice");
		when(configCache.getUser("+user/alice")).thenReturn(user(BANNED));

		rssParser.runScript(feed, "plugin/script/feed");

		verify(tagger).attachError(eq(FEED_URL), eq(ORIGIN), eq("Author not authorized to add tag"), eq("_private/tag"));
	}

	@Test
	void testNonPublicTag_WithModAuthor_Allowed() {
		// MOD author can add any tag
		var feed = feedWithAddTag("_private/tag", "+user/alice");
		when(configCache.getUser("+user/alice")).thenReturn(user(MOD));

		rssParser.runScript(feed, "plugin/script/feed");

		verify(tagger, never()).attachError(eq(FEED_URL), eq(ORIGIN), eq("Author not authorized to add tag"), anyString());
		verify(tagger, never()).attachError(eq(FEED_URL), eq(ORIGIN), eq("Non-public tags require author permission"), anyString());
	}

	@Test
	void testNonPublicTag_WithAdminAuthor_Allowed() {
		// ADMIN author can add any tag
		var feed = feedWithAddTag("+protected/tag", "+user/alice");
		when(configCache.getUser("+user/alice")).thenReturn(user(ADMIN));

		rssParser.runScript(feed, "plugin/script/feed");

		verify(tagger, never()).attachError(eq(FEED_URL), eq(ORIGIN), eq("Author not authorized to add tag"), anyString());
		verify(tagger, never()).attachError(eq(FEED_URL), eq(ORIGIN), eq("Non-public tags require author permission"), anyString());
	}

	@Test
	void testNonPublicTag_WithTagReadAccess_Allowed() {
		// Author has the private tag in tagReadAccess
		var feed = feedWithAddTag("_private/tag", "_user/alice");
		when(configCache.getUser("_user/alice")).thenReturn(userWithTagReadAccess(USER, "_private/tag"));

		rssParser.runScript(feed, "plugin/script/feed");

		verify(tagger, never()).attachError(eq(FEED_URL), eq(ORIGIN), eq("Author not authorized to add tag"), anyString());
		verify(tagger, never()).attachError(eq(FEED_URL), eq(ORIGIN), eq("Non-public tags require author permission"), anyString());
	}

	@Test
	void testNonPublicTag_NoTagReadAccess_ErrorAttached() {
		// Author has no tagReadAccess for the private tag
		var feed = feedWithAddTag("_private/tag", "+user/alice");
		when(configCache.getUser("+user/alice")).thenReturn(user(USER));

		rssParser.runScript(feed, "plugin/script/feed");

		verify(tagger).attachError(eq(FEED_URL), eq(ORIGIN), eq("Author not authorized to add tag"), eq("_private/tag"));
	}

	@Test
	void testNonPublicTag_AuthorOwnsTag_Allowed() {
		// Tag matches the author's own user tag (_user/alice can add _user/alice)
		var feed = feedWithAddTag("_user/alice", "_user/alice");
		when(configCache.getUser("_user/alice")).thenReturn(user(USER));

		rssParser.runScript(feed, "plugin/script/feed");

		verify(tagger, never()).attachError(eq(FEED_URL), eq(ORIGIN), eq("Author not authorized to add tag"), anyString());
	}

	@Test
	void testNullAuthorUser_ErrorAttached() {
		// Author tag present on feed but no User entity exists for it
		var feed = feedWithAddTag("_private/tag", "+user/unknown");
		when(configCache.getUser("+user/unknown")).thenReturn(null);

		rssParser.runScript(feed, "plugin/script/feed");

		verify(tagger).attachError(eq(FEED_URL), eq(ORIGIN), eq("Author not authorized to add tag"), eq("_private/tag"));
	}
}
