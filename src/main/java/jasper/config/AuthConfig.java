package jasper.config;

import jasper.management.SecurityMetersService;
import jasper.repository.UserRepository;
import jasper.security.jwt.TokenProvider;
import jasper.security.jwt.TokenProviderImpl;
import jasper.security.jwt.TokenProviderImplAnon;
import jasper.security.jwt.TokenProviderImplDefault;
import jasper.security.jwt.TokenProviderImplJwks;
import jasper.security.jwt.TokenProviderImplNoVerify;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

@Configuration
public class AuthConfig {

	@Bean
	@Profile("!no-ssl")
	RestTemplate restTemplate() {
		return new RestTemplate();
	}

	@Bean
	@Profile("no-ssl")
	RestTemplate restTemplateBypassSSL()
		throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
		TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;
		HostnameVerifier hostnameVerifier = (s, sslSession) -> true;
		SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
		SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
		CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(csf).build();
		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setHttpClient(httpClient);
		return new RestTemplate(requestFactory);
	}

	@Bean
	@Profile({"jwt", "default"})
	TokenProvider tokenProvider(Props props, UserRepository userRepository, SecurityMetersService securityMetersService) {
		return new TokenProviderImpl(props, userRepository, securityMetersService);
	}

	@Bean
	@Profile("jwks")
	TokenProvider jwksTokenProvider(
		Props props,
		UserRepository userRepository,
		SecurityMetersService securityMetersService,
		RestTemplate restTemplate
	) throws URISyntaxException {
		return new TokenProviderImplJwks(props, userRepository, securityMetersService, restTemplate);
	}

	@Bean
	@Profile("jwt-no-verify")
	TokenProvider noVerifyTokenProvider(Props props, UserRepository userRepository, SecurityMetersService securityMetersService) {
		return new TokenProviderImplNoVerify(props, userRepository, securityMetersService);
	}

	@Bean
	@Profile("default-user")
	TokenProvider defaultTokenProvider(Props props, UserRepository userRepository) {
		return new TokenProviderImplDefault(props, userRepository);
	}

	@Bean
	@ConditionalOnMissingBean
	TokenProvider anonTokenProvider(Props props, UserRepository userRepository) {
		return new TokenProviderImplAnon(props, userRepository);
	}
}
