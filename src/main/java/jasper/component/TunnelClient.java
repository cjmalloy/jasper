package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.domain.Ref;
import jasper.errors.InvalidTunnelException;
import jasper.plugin.Origin;
import jasper.plugin.Tunnel;
import jasper.repository.UserRepository;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.auth.keyboard.UserInteraction;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static jasper.domain.proj.HasTags.authors;
import static jasper.domain.proj.Tag.defaultOrigin;
import static jasper.domain.proj.Tag.reverseOrigin;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.sshd.common.NamedResource.ofName;
import static org.apache.sshd.common.util.security.SecurityUtils.loadKeyPairIdentities;

@Component
public class TunnelClient {
	private static final Logger logger = LoggerFactory.getLogger(TunnelClient.class);

	private static final int LOCAL_HTTP_PORT = 38080;

	@Autowired
	UserRepository userRepository;

	@Autowired
	ObjectMapper objectMapper;

	public void proxy(Ref remote, ProxyRequest request) {
		var config = objectMapper.convertValue(remote.getPlugins().get("+plugin/origin"), Origin.class);
		URI url = null;
		try {
			url = new URI(isNotBlank(config.getProxy()) ? config.getProxy() : remote.getUrl());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		var tunnel = objectMapper.convertValue(remote.getPlugins().get("+plugin/origin/tunnel"), Tunnel.class);
		if (tunnel == null) {
			request.go(url);
		} else {
			var users = authors(remote);
			if (users.isEmpty()) {
				throw new InvalidTunnelException("Tunnel requested, but no user signature to lookup private key.");
			}
			var user = userRepository.findOneByQualifiedTag(users.get(0) + remote.getOrigin());
			if (user.isEmpty() || user.get().getKey() == null) {
				throw new InvalidTunnelException("Tunnel requested, but user " + users.get(0) + " does not have a private key set.");
			}
			try (var client = SshClient.setUpDefaultClient()) {
				final int[] httpPort = {38022};
				client.setUserInteraction(new GetBanner() {
					@Override
					public void banner(String banner) {
						logger.info("Received SSH banner: {}", banner);
						try {
							httpPort[0] = Integer.parseInt(banner);
						} catch (Exception e) {
							logger.warn("Could not parse tunnel port from banner. Using default {}", httpPort[0]);
						}
					}
				});
				client.start();
				var host = isNotBlank(tunnel.getSshHost()) ? tunnel.getSshHost() : url.getHost();
				var username = linuxUsername(defaultOrigin(isNotBlank(tunnel.getRemoteUser()) ? tunnel.getRemoteUser() : user.get().getTag(), config.getRemote()));
				try (var session = client.connect(username, host, tunnel.getSshPort()).verify(30, TimeUnit.SECONDS).getSession()) {
					loadKeyPairIdentities(null, ofName(username), new ByteArrayInputStream(user.get().getKey()), null)
						.forEach(session::addPublicKeyIdentity);
					session.auth().verify(30, TimeUnit.SECONDS);
					try (var tracker = session.createLocalPortForwardingTracker(LOCAL_HTTP_PORT, new SshdSocketAddress("localhost", httpPort[0]))) {
						logger.debug("Opened reverse proxy in SSH tunnel.");
						request.go(new URI("http://localhost:" + LOCAL_HTTP_PORT));
					} catch (Exception e) {
						logger.debug("Error creating tunnel tracker", e);
						throw new InvalidTunnelException("Error creating tunnel tracker", e);
					}
				} catch (Exception e) {
					logger.debug("Error creating tunnel SSH session", e);
					throw new InvalidTunnelException("Error creating tunnel SSH session", e);
				} finally {
					client.stop();
				}
			} catch (Exception e) {
				logger.debug("Error creating tunnel SSH client", e);
				throw new InvalidTunnelException("Error creating tunnel SSH client", e);
			}
		}
	}

	public interface ProxyRequest {
		void go(URI url);
	}

	private String linuxUsername(String qualifiedTag) {
		return reverseOrigin(qualifiedTag)
			.replaceAll("[_+]", "")
			.replaceAll("/", "_");
	}

	private static abstract class GetBanner implements UserInteraction {

		public abstract void banner(String banner);

		@Override
		public void welcome(ClientSession session, String banner, String lang) {
			banner(banner.trim());
		}

		@Override
		public String[] interactive(ClientSession session, String name, String instruction, String lang, String[] prompt, boolean[] echo) {
			return new String[0];
		}

		@Override
		public String getUpdatedPassword(ClientSession session, String prompt, String lang) {
			return null;
		}
	}
}
