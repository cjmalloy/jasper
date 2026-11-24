package jasper.config;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthConfigTest {

	static final char[] PASSWORD = "changeit".toCharArray();

	@TempDir
	Path tmp;

	HttpsServer server;
	String url;

	@BeforeEach
	void startServer() throws Exception {
		// Self-signed and issued for a different host, so both trust and hostname checks would fail
		var keystore = tmp.resolve("server.p12");
		var keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
		var proc = new ProcessBuilder(keytool, "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
			"-dname", "CN=wrong.example", "-validity", "1", "-storetype", "PKCS12",
			"-keystore", keystore.toString(), "-storepass", new String(PASSWORD), "-keypass", new String(PASSWORD))
			.redirectErrorStream(true)
			.start();
		assertThat(proc.waitFor()).as(new String(proc.getInputStream().readAllBytes())).isZero();

		var ks = KeyStore.getInstance("PKCS12");
		try (var in = new FileInputStream(keystore.toFile())) {
			ks.load(in, PASSWORD);
		}
		var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(ks, PASSWORD);
		var ssl = SSLContext.getInstance("TLS");
		ssl.init(kmf.getKeyManagers(), null, null);

		server = HttpsServer.create(new InetSocketAddress(0), 0);
		server.setHttpsConfigurator(new HttpsConfigurator(ssl));
		server.createContext("/", exchange -> {
			var body = "ok".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			try (var out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		server.start();
		url = "https://localhost:" + server.getAddress().getPort() + "/";
	}

	@AfterEach
	void stopServer() {
		if (server != null) server.stop(0);
	}

	@Test
	void testDefaultRestTemplateRejectsUntrustedCertificate() {
		assertThatThrownBy(() -> new AuthConfig().restTemplate().getForObject(url, String.class))
			.isInstanceOf(ResourceAccessException.class);
	}

	@Test
	void testBypassSslAcceptsUntrustedCertificateForWrongHost() throws Exception {
		RestTemplate restTemplate = new AuthConfig().restTemplateBypassSSL();

		assertThat(restTemplate.getForObject(url, String.class)).isEqualTo("ok");
	}
}
