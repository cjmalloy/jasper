package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.IntegrationTest;
import jasper.plugin.Oembed;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@WithMockUser("+user/tester")
@IntegrationTest
public class OembedProvidersIT {

	@Autowired
	OembedProviders oembedProviders;

	@Autowired
	RefRepository refRepository;

	@Autowired
	ObjectMapper objectMapper;

	@BeforeEach
	void init() {
		refRepository.deleteAll();
	}

	@Test
	void testCreateProviders() {
		var provider = new Oembed();
		provider.setProvider_name("Test Provider");
		provider.setProvider_url("https://example.com");
		
		var endpoint = new Oembed.Endpoints();
		endpoint.setUrl("https://example.com/oembed");
		endpoint.setSchemes(List.of("https://example.com/*"));
		provider.setEndpoints(List.of(endpoint));

		oembedProviders.create("", List.of(provider));

		var refs = refRepository.findAll();
		assertThat(refs).hasSize(1);
		assertThat(refs.get(0).getUrl()).isEqualTo("https://example.com");
		assertThat(refs.get(0).getTitle()).isEqualTo("Test Provider");
		assertThat(refs.get(0).getTags()).contains("+plugin/oembed");
	}

	@Test
	void testGetProviderWithExactMatch() {
		var provider = new Oembed();
		provider.setProvider_name("Example");
		provider.setProvider_url("https://example.com");
		
		var endpoint = new Oembed.Endpoints();
		endpoint.setUrl("https://example.com/oembed");
		endpoint.setSchemes(List.of("https://example.com/*"));
		provider.setEndpoints(List.of(endpoint));

		oembedProviders.create("", List.of(provider));

		var result = oembedProviders.getProvider("", "https://example.com/video/123");

		assertThat(result).isNotNull();
		assertThat(result.getUrl()).isEqualTo("https://example.com/oembed");
	}

	@Test
	void testGetProviderWithWildcard() {
		var provider = new Oembed();
		provider.setProvider_name("Wildcard Provider");
		provider.setProvider_url("https://wildcard.com");
		
		var endpoint = new Oembed.Endpoints();
		endpoint.setUrl("https://wildcard.com/oembed");
		endpoint.setSchemes(List.of("https://wildcard.com/videos/*"));
		provider.setEndpoints(List.of(endpoint));

		oembedProviders.create("", List.of(provider));

		var result = oembedProviders.getProvider("", "https://wildcard.com/videos/abc123");

		assertThat(result).isNotNull();
		assertThat(result.getUrl()).isEqualTo("https://wildcard.com/oembed");
	}

	@Test
	void testGetProviderNoMatch() {
		var provider = new Oembed();
		provider.setProvider_name("Example");
		provider.setProvider_url("https://example.com");
		
		var endpoint = new Oembed.Endpoints();
		endpoint.setUrl("https://example.com/oembed");
		endpoint.setSchemes(List.of("https://example.com/*"));
		provider.setEndpoints(List.of(endpoint));

		oembedProviders.create("", List.of(provider));

		var result = oembedProviders.getProvider("", "https://different.com/video/123");

		assertThat(result).isNull();
	}

	@Test
	void testGetProviderWithoutSchemes() {
		var provider = new Oembed();
		provider.setProvider_name("No Scheme Provider");
		provider.setProvider_url("https://noscheme.com");
		
		var endpoint = new Oembed.Endpoints();
		endpoint.setUrl("https://noscheme.com/oembed");
		endpoint.setSchemes(new ArrayList<>()); // Empty schemes
		provider.setEndpoints(List.of(endpoint));

		oembedProviders.create("", List.of(provider));

		// Should match based on provider_url prefix
		var result = oembedProviders.getProvider("", "https://noscheme.com/video/123");

		assertThat(result).isNotNull();
		assertThat(result.getUrl()).isEqualTo("https://noscheme.com/oembed");
	}

	@Test
	void testGetProviderWithMultipleSchemes() {
		var provider = new Oembed();
		provider.setProvider_name("Multi Scheme Provider");
		provider.setProvider_url("https://multi.com");
		
		var endpoint = new Oembed.Endpoints();
		endpoint.setUrl("https://multi.com/oembed");
		endpoint.setSchemes(List.of(
			"https://multi.com/videos/*",
			"https://multi.com/photos/*"
		));
		provider.setEndpoints(List.of(endpoint));

		oembedProviders.create("", List.of(provider));

		var videoResult = oembedProviders.getProvider("", "https://multi.com/videos/123");
		var photoResult = oembedProviders.getProvider("", "https://multi.com/photos/456");

		assertThat(videoResult).isNotNull();
		assertThat(photoResult).isNotNull();
		assertThat(videoResult.getUrl()).isEqualTo("https://multi.com/oembed");
		assertThat(photoResult.getUrl()).isEqualTo("https://multi.com/oembed");
	}

	@Test
	void testGetProviderWithMultipleEndpoints() {
		var provider = new Oembed();
		provider.setProvider_name("Multi Endpoint Provider");
		provider.setProvider_url("https://multi-endpoint.com");
		
		var endpoint1 = new Oembed.Endpoints();
		endpoint1.setUrl("https://multi-endpoint.com/oembed/videos");
		endpoint1.setSchemes(List.of("https://multi-endpoint.com/videos/*"));
		
		var endpoint2 = new Oembed.Endpoints();
		endpoint2.setUrl("https://multi-endpoint.com/oembed/photos");
		endpoint2.setSchemes(List.of("https://multi-endpoint.com/photos/*"));
		
		provider.setEndpoints(List.of(endpoint1, endpoint2));

		oembedProviders.create("", List.of(provider));

		var videoResult = oembedProviders.getProvider("", "https://multi-endpoint.com/videos/123");
		var photoResult = oembedProviders.getProvider("", "https://multi-endpoint.com/photos/456");

		assertThat(videoResult).isNotNull();
		assertThat(photoResult).isNotNull();
		assertThat(videoResult.getUrl()).isEqualTo("https://multi-endpoint.com/oembed/videos");
		assertThat(photoResult.getUrl()).isEqualTo("https://multi-endpoint.com/oembed/photos");
	}

	@Test
	void testCreateMultipleProviders() {
		var provider1 = new Oembed();
		provider1.setProvider_name("Provider 1");
		provider1.setProvider_url("https://provider1.com");
		var endpoint1 = new Oembed.Endpoints();
		endpoint1.setUrl("https://provider1.com/oembed");
		provider1.setEndpoints(List.of(endpoint1));

		var provider2 = new Oembed();
		provider2.setProvider_name("Provider 2");
		provider2.setProvider_url("https://provider2.com");
		var endpoint2 = new Oembed.Endpoints();
		endpoint2.setUrl("https://provider2.com/oembed");
		provider2.setEndpoints(List.of(endpoint2));

		oembedProviders.create("", List.of(provider1, provider2));

		var refs = refRepository.findAll();
		assertThat(refs).hasSize(2);
	}

	@Test
	void testGetProviderWithComplexWildcard() {
		var provider = new Oembed();
		provider.setProvider_name("Complex Wildcard");
		provider.setProvider_url("https://complex.com");
		
		var endpoint = new Oembed.Endpoints();
		endpoint.setUrl("https://complex.com/oembed");
		endpoint.setSchemes(List.of("https://complex.com/*/videos/*"));
		provider.setEndpoints(List.of(endpoint));

		oembedProviders.create("", List.of(provider));

		var result = oembedProviders.getProvider("", "https://complex.com/user123/videos/video456");

		assertThat(result).isNotNull();
		assertThat(result.getUrl()).isEqualTo("https://complex.com/oembed");
	}

	@Test
	void testGetProviderWithOrigin() {
		var provider = new Oembed();
		provider.setProvider_name("Origin Provider");
		provider.setProvider_url("https://origin.com");
		
		var endpoint = new Oembed.Endpoints();
		endpoint.setUrl("https://origin.com/oembed");
		endpoint.setSchemes(List.of("https://origin.com/*"));
		provider.setEndpoints(List.of(endpoint));

		oembedProviders.create("tenant1.example.com", List.of(provider));

		var result = oembedProviders.getProvider("tenant1.example.com", "https://origin.com/video/123");

		assertThat(result).isNotNull();
	}
}
