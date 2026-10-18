package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jasper.config.JacksonConfiguration;
import jasper.domain.Ref;
import jasper.plugin.Cache;
import jasper.plugin.Scrape;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ScraperTest {

	static final String URL = "https://www.youtube.com/watch?v=test";
	static final String PAGE = "<html><head><title>Video</title></head><body></body></html>";

	Scraper scraper;

	@BeforeEach
	void init() {
		ReflectionTestUtils.setField(JacksonConfiguration.class, "om", new ObjectMapper());
		scraper = new Scraper();
		scraper.configs = mock(ConfigCache.class);
		scraper.tagger = mock(Tagger.class);
		scraper.proxy = mock(Proxy.class);
		scraper.refRepository = mock(RefRepository.class);
		scraper.em = mock(EntityManager.class);
		when(scraper.configs.getAllConfigs(anyString(), eq("+plugin/scrape"), eq(Scrape.class))).thenReturn(List.of());
		when(scraper.configs.getConfig(eq("config:scrape-catchall"), anyString(), eq("+plugin/scrape"), eq(Scrape.class)))
			.thenReturn(new Scrape());
		when(scraper.proxy.fetchString(eq(URL), eq(""), any(Boolean.class))).thenReturn(PAGE);
	}

	Ref ref() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("");
		ref.setTags(new ArrayList<>(List.of("public")));
		return ref;
	}

	@Test
	void testScrapeNewRefDoesNotCache() throws Exception {
		when(scraper.refRepository.findOneByUrlAndOrigin(URL, "")).thenReturn(Optional.empty());

		var result = scraper.web(URL, "");

		assertThat(result.getTitle()).isEqualTo("Video");
		verify(scraper.proxy).fetchString(URL, "", false);
	}

	@Test
	void testScrapeExistingRefDoesNotCache() throws Exception {
		when(scraper.refRepository.findOneByUrlAndOrigin(URL, "")).thenReturn(Optional.of(ref()));

		var result = scraper.web(URL, "");

		assertThat(result.getTitle()).isEqualTo("Video");
		verify(scraper.proxy).fetchString(URL, "", false);
	}

	@Test
	void testScrapeCachedRefUsesCache() throws Exception {
		var ref = ref();
		ref.setPlugin("_plugin/cache", Cache.builder().id("id").build());
		when(scraper.refRepository.findOneByUrlAndOrigin(URL, "")).thenReturn(Optional.of(ref));

		var result = scraper.web(URL, "");

		assertThat(result.getTitle()).isEqualTo("Video");
		verify(scraper.proxy).fetchString(URL, "", true);
	}
}
