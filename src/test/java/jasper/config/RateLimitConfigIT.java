package jasper.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jasper.IntegrationTest;
import jasper.domain.Template;
import jasper.repository.TemplateRepository;
import jasper.service.dto.TemplateDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.messaging.MessageChannel;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static jasper.component.Messages.tagHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WithMockUser(value = "+user/tester", roles = {"ADMIN"})
@AutoConfigureMockMvc
@IntegrationTest
class RateLimitConfigIT {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TemplateRepository templateRepository;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	MessageChannel templateRxChannel;

	@Autowired
	FilterRegistrationBean<Filter> rateLimitInterceptor;

	Template serverConfigBackup;
	boolean serverConfigChanged;

	@AfterEach
	void cleanup() {
		templateRepository.findByTemplateAndOrigin("_config/security", "@ratelimited").ifPresent(t -> {
			templateRepository.delete(t);
			notifyTemplate("_config/security", "@ratelimited");
		});
		if (serverConfigChanged) {
			if (serverConfigBackup != null) {
				templateRepository.save(serverConfigBackup);
			} else {
				templateRepository.findByTemplateAndOrigin("_config/server", "").ifPresent(templateRepository::delete);
			}
			notifyTemplate("_config/server", "");
			serverConfigBackup = null;
			serverConfigChanged = false;
		}
	}

	void notifyTemplate(String tag, String origin) {
		var dto = new TemplateDto();
		dto.setTag(tag);
		dto.setOrigin(origin);
		templateRxChannel.send(new GenericMessage<>(dto, tagHeaders(origin, tag)));
	}

	List<MvcResult> burst(String origin, int count) throws Exception {
		var results = new ArrayList<MvcResult>();
		for (var i = 0; i < count; i++) {
			results.add(mockMvc
				.perform(get("/api/v1/ref/page")
					.header("Local-Origin", origin))
				.andReturn());
		}
		return results;
	}

	static long rateLimited(List<MvcResult> results) {
		return results.stream().filter(r -> r.getResponse().getStatus() == 429).count();
	}

	@Test
	void testRunsBeforeSecurity() {
		assertThat(rateLimitInterceptor.getOrder()).isLessThan(SecurityFilterProperties.DEFAULT_FILTER_ORDER);
	}

	@Test
	void testDefaultSettingsNeverRateLimit() throws Exception {
		var results = burst("@unlimited", 500);

		assertThat(rateLimited(results)).isZero();
	}

	@Test
	void testOriginRateLimit() throws Exception {
		// Warm up, and create the origin rate limiter with default settings before changing the config
		assertThat(rateLimited(burst("@ratelimited", 20))).isZero();

		var template = new Template();
		template.setTag("_config/security");
		template.setOrigin("@ratelimited");
		template.setConfig(objectMapper.createObjectNode()
			.put("maxRequests", 2)
			.put("maxRequestsPeriod", "PT1S"));
		templateRepository.save(template);
		notifyTemplate("_config/security", "@ratelimited");

		var results = burst("@ratelimited", 10);
		var other = burst("@other", 20);

		assertThat(rateLimited(results)).isGreaterThanOrEqualTo(1);
		assertThat(results.getFirst().getResponse().getStatus()).isNotEqualTo(429);
		results.stream()
			.filter(r -> r.getResponse().getStatus() == 429)
			.forEach(r -> assertThat(r.getResponse().getHeader("X-RateLimit-Limit")).isEqualTo("2"));
		assertThat(rateLimited(other)).isZero();
	}

	@Test
	void testGlobalRateLimit() throws Exception {
		// Warm up
		assertThat(rateLimited(burst("@global1", 20))).isZero();
		// Warm up
		assertThat(rateLimited(burst("@global1", 20))).isZero();
		var existing = templateRepository.findByTemplateAndOrigin("_config/server", "");
		serverConfigBackup = existing.orElse(null);
		serverConfigChanged = true;
		var server = new Template();
		server.setTag("_config/server");
		server.setOrigin("");
		server.setConfig(existing.map(Template::getConfig).map(ObjectNode::deepCopy).orElse(objectMapper.createObjectNode())
			.put("maxRequests", 2)
			.put("maxRequestsPeriod", "PT1S"));
		templateRepository.save(server);
		notifyTemplate("_config/server", "");

		var results = burst("@global1", 5);
		results.addAll(burst("@global2", 5));

		assertThat(rateLimited(results)).isGreaterThanOrEqualTo(1);
		assertThat(results.getFirst().getResponse().getStatus()).isNotEqualTo(429);
		results.stream()
			.filter(r -> r.getResponse().getStatus() == 429)
			.forEach(r -> assertThat(r.getResponse().getHeader("X-RateLimit-Limit")).isNull());
	}

	int status(String origin, FilterChain chain) throws Exception {
		var request = new MockHttpServletRequest("GET", "/api/v1/ref/page");
		request.addHeader("Local-Origin", origin);
		var response = new MockHttpServletResponse();
		rateLimitInterceptor.getFilter().doFilter(request, response, chain);
		return response.getStatus();
	}

	void saveConcurrencyLimit(int limit) {
		var template = templateRepository.findByTemplateAndOrigin("_config/security", "@ratelimited").orElseGet(() -> {
			var t = new Template();
			t.setTag("_config/security");
			t.setOrigin("@ratelimited");
			return t;
		});
		template.setConfig(objectMapper.createObjectNode().put("maxConcurrentRequests", limit));
		templateRepository.save(template);
		notifyTemplate("_config/security", "@ratelimited");
	}

	@Test
	void testOriginConcurrencyLimitUpdate() throws Exception {
		saveConcurrencyLimit(1);
		var started = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var pool = Executors.newSingleThreadExecutor();
		try {
			var held = pool.submit(() -> status("@ratelimited.tenant", (req, res) -> {
				started.countDown();
				try {
					release.await();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}));
			assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

			// Inherited by the sub-origin, which has its own limit
			assertThat(status("@ratelimited.tenant", new MockFilterChain())).isEqualTo(429);
			assertThat(status("@ratelimited", new MockFilterChain())).isEqualTo(200);

			saveConcurrencyLimit(2);
			assertThat(status("@ratelimited.tenant", new MockFilterChain())).isEqualTo(200);

			saveConcurrencyLimit(0);
			release.countDown();
			assertThat(held.get(5, TimeUnit.SECONDS)).isEqualTo(200);
		} finally {
			release.countDown();
			pool.shutdownNow();
		}
	}
}
