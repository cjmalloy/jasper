package jasper.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import jasper.IntegrationTest;
import jasper.component.ConfigCache;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static jasper.management.SecurityMetersService.INVALID_TOKENS_METER_CAUSE_DIMENSION;
import static jasper.management.SecurityMetersService.INVALID_TOKENS_METER_NAME;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins JWKS download through the Spring-managed {@link RestTemplate} in jwks mode.
 * The global test override forces jwt mode, so it is cleared here and the per-origin
 * <code>_config/security</code> Template selects jwks mode, as in production.
 */
@IntegrationTest
@TestPropertySource(properties = {
	"spring.profiles.include=jwt",
	"jasper.override.security.mode=",
})
class JwksRemoteIT {
	static final String CLIENT_ID = "jasper-test-client";

	static HttpServer server;
	static RSAKey key;
	static RSAKey otherKey;
	static final List<String> requests = Collections.synchronizedList(new ArrayList<>());
	static final Map<String, String> jwks = new ConcurrentHashMap<>();

	@Autowired
	TokenProvider tokenProvider;

	@Autowired
	RestTemplate restTemplate;

	@Autowired
	TemplateRepository templateRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	MeterRegistry meterRegistry;

	@Autowired
	ConfigCache configCache;

	@BeforeAll
	static void startServer() throws Exception {
		key = new RSAKeyGenerator(2048).keyID("k1").generate();
		otherKey = new RSAKeyGenerator(2048).keyID("k1").generate();
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/", exchange -> {
			requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
			var body = jwks.get(exchange.getRequestURI().getPath());
			if (body == null) {
				exchange.sendResponseHeaders(404, -1);
				exchange.close();
				return;
			}
			var bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			try (var output = exchange.getResponseBody()) {
				output.write(bytes);
			}
		});
		server.start();
	}

	@AfterAll
	static void stopServer() {
		server.stop(0);
	}

	@AfterEach
	void cleanup() {
		requests.clear();
		jwks.clear();
		templateRepository.findAll().stream()
			.filter(t -> t.getOrigin().startsWith("@jwks"))
			.forEach(templateRepository::delete);
		userRepository.findAll().stream()
			.filter(u -> u.getOrigin().startsWith("@jwks"))
			.forEach(userRepository::delete);
	}

	String configure(String origin) {
		var path = "/" + origin.substring(1) + "/.well-known/jwks.json";
		jwks.put(path, new JWKSet(key.toPublicJWK()).toString());
		var template = new Template();
		template.setTag("_config/security");
		template.setOrigin(origin);
		template.setConfig(objectMapper.createObjectNode()
			.put("mode", "jwks")
			.put("clientId", CLIENT_ID)
			.put("jwksUri", "http://localhost:" + server.getAddress().getPort() + path));
		templateRepository.save(template);
		return path;
	}

	static JWTClaimsSet.Builder claims() {
		return new JWTClaimsSet.Builder()
			.subject("alice")
			.audience(CLIENT_ID)
			.claim("auth", "ROLE_EDITOR")
			.claim("verified_email", true)
			.expirationTime(new Date(System.currentTimeMillis() + 5 * 60 * 1000L));
	}

	static String sign(RSAKey signer, String kid, JWTClaimsSet claims) throws JOSEException {
		var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(), claims);
		jwt.sign(new RSASSASigner(signer));
		return jwt.serialize();
	}

	double invalid(String cause) {
		return meterRegistry.get(INVALID_TOKENS_METER_NAME).tag(INVALID_TOKENS_METER_CAUSE_DIMENSION, cause).counter().count();
	}

	@Test
	void testUsesSpringManagedRestTemplate() {
		assertThat(tokenProvider).isInstanceOf(TokenProviderImpl.class);
		assertThat(ReflectionTestUtils.getField(tokenProvider, "restTemplate")).isSameAs(restTemplate);
	}

	@Test
	void testValidTokenFromRemoteJwks() throws Exception {
		var path = configure("@jwks");
		var user = new User();
		user.setTag("+user/alice");
		user.setOrigin("@jwks");
		user.setRole("ROLE_MOD");
		userRepository.save(user);
		var token = sign(key, "k1", claims().build());

		assertThat(tokenProvider.validateToken(token, "@jwks")).isTrue();
		assertThat(requests).containsExactly("GET " + path);

		var auth = tokenProvider.getAuthentication(token, "@jwks");
		assertThat(auth).isInstanceOf(JwtAuthentication.class);
		assertThat(auth.isAuthenticated()).isTrue();
		assertThat(auth.getPrincipal()).isEqualTo("+user/alice@jwks");
		assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
			.containsExactly("ROLE_USER", "ROLE_ANONYMOUS", "ROLE_EDITOR", "ROLE_MOD");
		assertThat(((JwtAuthentication) auth).getDetails().getQualifiedTag()).isEqualTo("+user/alice@jwks");
		assertThat(((JwtAuthentication) auth).getClaims().getString("sub")).isEqualTo("alice");
		// The key set is cached by the decoder: decoding again does not download it again
		assertThat(requests).containsExactly("GET " + path);

		assertThat(tokenProvider.validateToken(sign(key, "k1", claims().subject("bob").build()), "@jwks")).isTrue();
		assertThat(requests).containsExactly("GET " + path);
	}

	@Test
	void testTokenSignedWithOtherKeyRejected() throws Exception {
		var path = configure("@jwks.other");
		var invalidSignature = invalid("invalid-signature");

		// Same kid, different key
		assertThat(tokenProvider.validateToken(sign(otherKey, "k1", claims().build()), "@jwks.other")).isFalse();
		assertThat(requests).containsExactly("GET " + path);
		assertThat(invalid("invalid-signature")).isEqualTo(invalidSignature + 1);

		// Valid token still accepted from the cached key set
		assertThat(tokenProvider.validateToken(sign(key, "k1", claims().build()), "@jwks.other")).isTrue();
		assertThat(requests).containsExactly("GET " + path);
	}

	@Test
	void testUnknownKidRejected() throws Exception {
		var path = configure("@jwks.kid");
		assertThat(tokenProvider.validateToken(sign(key, "k1", claims().build()), "@jwks.kid")).isTrue();
		assertThat(requests).containsExactly("GET " + path);
		var malformed = invalid("malformed");
		var unsupported = invalid("unsupported");
		var invalidSignature = invalid("invalid-signature");

		var unknown = new RSAKeyGenerator(2048).keyID("k2").generate();
		assertThat(tokenProvider.validateToken(sign(unknown, "k2", claims().build()), "@jwks.kid")).isFalse();
		// A kid miss triggers a refresh of the key set
		assertThat(requests).containsExactly("GET " + path, "GET " + path);
		// Nimbus reports "Another algorithm expected, or no matching key(s) found", counted as unsupported
		assertThat(invalid("unsupported")).isEqualTo(unsupported + 1);
		assertThat(invalid("malformed")).isEqualTo(malformed);
		assertThat(invalid("invalid-signature")).isEqualTo(invalidSignature);
	}

	@Test
	void testProductionClaimChecks() throws Exception {
		configure("@jwks.claims");
		var audience = invalid("invalid-audience");
		var email = invalid("email-not-verified");
		var expired = invalid("expired");

		assertThat(tokenProvider.validateToken(sign(key, "k1", claims().audience("other").build()), "@jwks.claims")).isFalse();
		assertThat(tokenProvider.validateToken(sign(key, "k1", claims().audience(List.of(CLIENT_ID, "other")).build()), "@jwks.claims")).isFalse();
		assertThat(invalid("invalid-audience")).isEqualTo(audience + 2);

		assertThat(tokenProvider.validateToken(sign(key, "k1", claims().claim("verified_email", false).build()), "@jwks.claims")).isFalse();
		assertThat(tokenProvider.validateToken(sign(key, "k1", claims().claim("verified_email", null).build()), "@jwks.claims")).isFalse();
		assertThat(invalid("email-not-verified")).isEqualTo(email + 2);

		assertThat(tokenProvider.validateToken(sign(key, "k1", claims().expirationTime(new Date(System.currentTimeMillis() - 1000L)).build()), "@jwks.claims")).isFalse();
		assertThat(invalid("expired")).isEqualTo(expired + 1);
	}

	@Test
	void testUnreachableJwksRejected() throws IOException, JOSEException {
		var path = configure("@jwks.missing");
		jwks.remove(path);
		assertThat(tokenProvider.validateToken(sign(key, "k1", claims().build()), "@jwks.missing")).isFalse();
		assertThat(requests).containsExactly("GET " + path);
	}

	@Test
	void testChangedSecurityConfigRebuildsDecoder() throws Exception {
		var path = configure("@jwks.rotate");
		assertThat(tokenProvider.validateToken(sign(key, "k1", claims().build()), "@jwks.rotate")).isTrue();
		assertThat(tokenProvider.validateToken(sign(otherKey, "k1", claims().build()), "@jwks.rotate")).isFalse();
		assertThat(requests).containsExactly("GET " + path);

		// Point the origin at a new key set, as an admin editing _config/security would
		var rotated = "/jwks.rotate/rotated.json";
		jwks.put(rotated, new JWKSet(otherKey.toPublicJWK()).toString());
		var template = templateRepository.findByTemplateAndOrigin("_config/security", "@jwks.rotate").orElseThrow();
		((com.fasterxml.jackson.databind.node.ObjectNode) template.getConfig()).put("jwksUri", "http://localhost:" + server.getAddress().getPort() + rotated);
		templateRepository.save(template);
		configCache.clearTemplateCache("@jwks.rotate");

		assertThat(tokenProvider.validateToken(sign(otherKey, "k1", claims().build()), "@jwks.rotate")).isTrue();
		assertThat(tokenProvider.validateToken(sign(key, "k1", claims().build()), "@jwks.rotate")).isFalse();
		assertThat(requests).containsExactly("GET " + path, "GET " + rotated);

		// Unchanged settings reuse the cached decoder and key set
		assertThat(tokenProvider.validateToken(sign(otherKey, "k1", claims().build()), "@jwks.rotate")).isTrue();
		assertThat(requests).containsExactly("GET " + path, "GET " + rotated);
	}
}
