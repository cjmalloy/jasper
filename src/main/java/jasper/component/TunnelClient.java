package jasper.component;

import jasper.domain.proj.HasTags;
import jasper.errors.InvalidTunnelException;
import jasper.repository.UserRepository;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.auth.keyboard.UserInteraction;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.forward.PortForwardingTracker;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static jasper.domain.proj.HasTags.authors;
import static jasper.domain.proj.Tag.defaultOrigin;
import static jasper.domain.proj.Tag.reverseOrigin;
import static jasper.plugin.Origin.getOrigin;
import static jasper.plugin.Tunnel.getTunnel;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.sshd.common.NamedResource.ofName;
import static org.apache.sshd.common.util.security.SecurityUtils.loadKeyPairIdentities;

@Component
public class TunnelClient {
	private static final Logger logger = LoggerFactory.getLogger(TunnelClient.class);

	@Autowired
	UserRepository userRepository;

	@Autowired
	Tagger tagger;

	private final Map<String, String> tunnels = new ConcurrentHashMap<>();
	private final Map<String, Set<ProxyRequest>> connections = new ConcurrentHashMap<>();
	private final Map<String, SshClient> sshClients = new ConcurrentHashMap<>();
	private final Map<String, PortForwardingTracker> trackers = new ConcurrentHashMap<>();

	@Scheduled(fixedDelay = 2, initialDelay = 2, timeUnit = TimeUnit.HOURS)
	public void closeInactiveTunnels() {
		connections.forEach((key, requests) -> {
			if (requests.isEmpty()) {
				closeTunnel(key);
			}
		});
	}

	public void proxy(HasTags remote, ProxyRequest request) {
		try {
			var config = getOrigin(remote);
			URI url;
			try {
				url = new URI(isNotBlank(config.getProxy()) ? config.getProxy() : remote.getUrl());
			} catch (URISyntaxException e) {
				throw new InvalidTunnelException("Error parsing tunnel URI", e);
			}
			var tunnel = getTunnel(remote);
			if (tunnel == null) {
				request.go(url);
				return;
			}
			var users = authors(remote);
			if (users.isEmpty()) {
				throw new InvalidTunnelException("Tunnel requested, but no user signature to lookup private key.");
			}
			var user = userRepository.findOneByQualifiedTag(users.get(0) + remote.getOrigin());
			if (user.isEmpty() || user.get().getKey() == null) {
				throw new InvalidTunnelException("Tunnel requested, but user " + users.get(0) + " does not have a private key set.");
			}
			var host = isNotBlank(tunnel.getSshHost()) ? tunnel.getSshHost() : url.getHost();
			var username = linuxUsername(defaultOrigin(isNotBlank(tunnel.getRemoteUser()) ? tunnel.getRemoteUser() : user.get().getTag(), config.getRemote()));
			tunnels.compute(username + host, (key, value) -> {
				connections.get(username + host).add(request);
				if (value != null) return value;
				try {
					var client = SshClient.setUpDefaultClient();
					sshClients.put(username + host, client);
					final int[] httpPort = {38022};
					client.setUserInteraction(new GetBanner() {
						@Override
						public void banner(String banner) {
							logger.debug("Received SSH banner: {}", banner);
							try {
								httpPort[0] = Integer.parseInt(banner);
							} catch (Exception e) {
								logger.warn("{} Could not parse tunnel port from banner. Using default {}", remote.getOrigin(), httpPort[0]);
							}
						}
					});
					client.start();
					try {
						var session = client.connect(username, host, tunnel.getSshPort()).verify(30, TimeUnit.SECONDS).getSession();
						loadKeyPairIdentities(null, ofName(username), new ByteArrayInputStream(user.get().getKey()), null)
							.forEach(session::addPublicKeyIdentity);
						session.auth().verify(30, TimeUnit.SECONDS);
						try {
							var tracker = session.createLocalPortForwardingTracker(0, new SshdSocketAddress("localhost", httpPort[0]));
							trackers.put(username + host, tracker);
							logger.debug("{} Opened reverse proxy in SSH tunnel.", remote.getOrigin());
							var t = "http://localhost:" + tracker.getBoundAddress().getPort();
							tunnels.put(username + host, t);
							Set<ProxyRequest> requests = ConcurrentHashMap.newKeySet();
							requests.add(request);
							connections.put(username + host, requests);
							return t;
						} catch (Exception e) {
							logger.debug("{} Error creating tunnel port forward", remote.getOrigin());
							throw new InvalidTunnelException("Error creating tunnel tracker", e);
						}
					} catch (InvalidTunnelException e) {
						throw e;
					}  catch (Exception e) {
						logger.debug("{} Error creating tunnel SSH session", remote.getOrigin());
						closeTunnel(username + host);
						throw new InvalidTunnelException("Error creating tunnel SSH session", e);
					}
				} catch (InvalidTunnelException e) {
					throw e;
				} catch (Exception e) {
					logger.debug("{} Error creating tunnel SSH client", remote.getOrigin());
					closeTunnel(username + host);
					throw new InvalidTunnelException("Error creating tunnel SSH client", e);
				}
			});
			try {
				request.go(new URI(tunnels.get(username + host)));
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			} finally {
				connections.get(username + host).remove(request);
			}
		} catch (InvalidTunnelException e) {
			tagger.attachError(remote.getUrl(), remote.getOrigin(),
				"Error creating SSH tunnel for %s: %s".formatted(
					remote.getTitle(), remote.getUrl()),
				e.getMessage());
			throw e;
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

	private void closeTunnel(String key) {
		tunnels.compute(key, (k, value) -> {
			if (!connections.get(key).isEmpty()) return value; // Race condition for reusing tunnel
			connections.remove(key);
			try {
				var tracker = trackers.remove(key);
				if (tracker != null) {
					tracker.close();
					logger.debug("Closed tunnel tracker: {}", key);
				}
			} catch (Exception e) {
				logger.warn("Failed to close tracker for key {}: {}", key, e.getMessage());
			}
			try {
				var client = sshClients.remove(key);
				if (client != null) {
					client.stop(); // This stops the client and closes all sessions associated with it
					logger.debug("Stopped SSH client: {}", key);
				}
			} catch (Exception e) {
				logger.warn("Failed to close tunnel for key {}: {}", key, e.getMessage());
			}
			return null;
		});
	}
}
