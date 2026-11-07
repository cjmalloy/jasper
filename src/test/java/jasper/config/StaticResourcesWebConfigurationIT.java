package jasper.config;

import jasper.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Serves {@link StaticResourcesWebConfiguration#RESOURCE_PATHS} through the real MVC stack.
 * The configuration is only active in the "prod" profile, so it is registered here through a subclass.
 */
@AutoConfigureMockMvc
@IntegrationTest
@Import(StaticResourcesWebConfigurationIT.ProdStaticResources.class)
class StaticResourcesWebConfigurationIT {

	@TestConfiguration
	static class ProdStaticResources extends StaticResourcesWebConfiguration {
		ProdStaticResources(Props props) {
			super(props);
		}
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	WebApplicationContext context;

	@Autowired
	Props props;

	MockMvc mvcOnly;

	@BeforeEach
	void setup() {
		// Without the security filter chain, to test resource handler routing
		mvcOnly = MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void testServesRootResourceWithCacheControl() throws Exception {
		mvcOnly
			.perform(get("/static-resource-test.js"))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "max-age=" + props.getHttp().getCache().getTimeToLiveInDays() * 86400L + ", public"))
			.andExpect(content().string("console.log(\"jasper static resource test\");\n"));
	}

	@Test
	void testMissingRootResourceNotFound() throws Exception {
		mvcOnly
			.perform(get("/missing.js"))
			.andExpect(status().isNotFound());
	}

	@Test
	void testMissingResourcesNotFound() throws Exception {
		for (var path : new String[]{ "/missing.css", "/missing.svg", "/missing.png", "/favicon.ico", "/i18n/missing.json", "/content/missing/file.txt" }) {
			mvcOnly
				.perform(get(path))
				.andExpect(status().isNotFound());
		}
	}

	@Test
	void testStaticResourcesRequireAuthorization() throws Exception {
		// Only /api/**, /pub/api/** and /management/** are permitted by SecurityConfiguration
		for (var path : new String[]{ "/static-resource-test.js", "/missing.js" }) {
			mockMvc
				.perform(get(path))
				.andExpect(status().isUnauthorized());
		}
	}
}
