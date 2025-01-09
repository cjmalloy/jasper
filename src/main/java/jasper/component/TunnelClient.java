package jasper.component;

import io.vavr.Tuple;
import io.vavr.Tuple3;
import jasper.domain.proj.HasTags;
import jasper.errors.InvalidTunnelException;
import jasper.repository.UserRepository;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.auth.keyboard.UserInteraction;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
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

	@Autowired
	TaskScheduler taskScheduler;

	Map<String, Tuple3<Integer, Integer, SshClient>> tunnels = new ConcurrentHashMap<>();

	@Scheduled(fixedDelay = 5, initialDelay = 5, timeUnit = TimeUnit.MINUTES)
	public void log() {
		var connections = 0;
		for (var t : tunnels.values()) connections += t._2();
		logger.info("SSH Tunnel Pool: {} clients with {} connections", tunnels.size(), connections);
	}

	@Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
	public void healthCheck() {
		for (var remote : tunnels.keySet()) {
			tunnels.compute(remote, (k, v) -> {
				if (v == null) {
					logger.debug("Health check found null entry for {}", remote);
					return null;
				}
				var tunnelPort = v._1();
				var connections = v._2();
				var client = v._3();

				if (!client.isOpen()) {
					logger.warn("Found closed client for {} with {} connections", remote, connections);
					client.stop();
					return null;
				}

				// Test SSH connection is responding
				try {
					client.getVersion();
					logger.debug("Healthy connection for {} with {} connections", remote, connections);
					return v;
				} catch (Exception e) {
					logger.warn("Failed connection test for {} with {} connections: {}", remote, connections, e.getMessage());
					client.stop();
					return null;
				}
			});
		}
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
			} else {
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
				var port = tunnel.getSshPort();
				var tunnelPort = pooledConnection(remote.getOrigin(), host, username, port, user.get().getKey());
				try {
					request.go(new URI("http://localhost:" + tunnelPort));
				} catch (Exception e) {
					killTunnel(host, username, port);
					throw new InvalidTunnelException("Error creating tunnel tracker", e);
				}
				releaseTunnel(tunnelPort, host, username, port);
			}
		} catch (InvalidTunnelException e) {
			tagger.attachError(remote.getUrl(), remote.getOrigin(),
				"Error creating SSH tunnel for %s: %s".formatted(
					remote.getTitle(), remote.getUrl()),
				e.getMessage());
			throw e;
		}
	}

	public URI reserveProxy(HasTags remote) {
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
				return url;
			} else {
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
				var port = tunnel.getSshPort();
				var tunnelPort = pooledConnection(remote.getOrigin(), host, username, port, user.get().getKey());
				try {
					return new URI("http://localhost:" + tunnelPort);
				} catch (URISyntaxException e) {
					killTunnel(host, username, port);
					throw new InvalidTunnelException("Error creating tunnel tracker", e);
				}
			}
		} catch (InvalidTunnelException e) {
			tagger.attachError(remote.getUrl(), remote.getOrigin(),
				"Error creating SSH tunnel for %s: %s".formatted(
					remote.getTitle(), remote.getUrl()),
				e.getMessage());
			throw e;
		}
	}

	public void releaseProxy(HasTags remote) {
		var config = getOrigin(remote);
		URI url;
		try {
			url = new URI(isNotBlank(config.getProxy()) ? config.getProxy() : remote.getUrl());
		} catch (URISyntaxException e) {
			throw new InvalidTunnelException("Error parsing tunnel URI", e);
		}
		var tunnel = getTunnel(remote);
		if (tunnel != null) {
			var users = authors(remote);
			var host = isNotBlank(tunnel.getSshHost()) ? tunnel.getSshHost() : url.getHost();
			var username = linuxUsername(defaultOrigin(isNotBlank(tunnel.getRemoteUser()) ? tunnel.getRemoteUser() : users.get(0), config.getRemote()));
			var port = tunnel.getSshPort();
			releaseTunnel(null, host, username, port);
		}
	}

	public void killProxy(HasTags remote) {
		var config = getOrigin(remote);
		URI url;
		try {
			url = new URI(isNotBlank(config.getProxy()) ? config.getProxy() : remote.getUrl());
		} catch (URISyntaxException e) {
			throw new InvalidTunnelException("Error parsing tunnel URI", e);
		}
		var tunnel = getTunnel(remote);
		if (tunnel != null) {
			var users = authors(remote);
			var host = isNotBlank(tunnel.getSshHost()) ? tunnel.getSshHost() : url.getHost();
			var username = linuxUsername(defaultOrigin(isNotBlank(tunnel.getRemoteUser()) ? tunnel.getRemoteUser() : users.get(0), config.getRemote()));
			var port = tunnel.getSshPort();
			this.killTunnel(host, username, port);
		}
	}

	private int pooledConnection(String origin, String host, String username, int port, byte[] key) {
		var remote = username + "@" + host + ":" + port;
		return tunnels.compute(remote, (k, v) -> {
			if (v != null) {
				var tunnelPort = v._1();
				var connections = v._2();
				var client = v._3();
				if  (client.isOpen()) return Tuple.of(tunnelPort, connections + 1, client);
			}
			var client = SshClient.setUpDefaultClient();
			try {
				final int[] httpPort = {38022};
				client.setUserInteraction(new GetBanner() {
					@Override
					public void banner(String banner) {
						logger.debug("Received SSH banner: {}", banner);
						try {
							httpPort[0] = Integer.parseInt(banner);
						} catch (Exception e) {
							logger.warn("{} Could not parse tunnel port from banner. Using default {}", origin, httpPort[0]);
						}
					}
				});
				client.start();
				var session = client.connect(username, host, port).verify(30, TimeUnit.SECONDS).getSession();
				loadKeyPairIdentities(null, ofName(username), new ByteArrayInputStream(key), null)
					.forEach(session::addPublicKeyIdentity);
				session.auth().verify(30, TimeUnit.SECONDS);
				var tracker = session.createLocalPortForwardingTracker(0, new SshdSocketAddress("localhost", httpPort[0]));
				var tunnelPort = tracker.getBoundAddress().getPort();
				client.addSessionListener(new SessionListener() {
					@Override
					public void sessionClosed(Session session) {
						logger.debug("{} SSH session closed for {}", origin, remote);
						killTunnel(host, username, port);
					}
				});
				return Tuple.of(tunnelPort, 1, client);
			} catch (Exception e) {
				client.stop();
				logger.debug("{} Error creating tunnel SSH client", origin, e);
				throw new InvalidTunnelException("Error creating tunnel SSH client", e);
			}
		})._1();
	}

	private void releaseTunnel(Integer tunnelPort, String host, String username, int port) {
		var remote = username + "@" + host + ":" + port;
		tunnels.compute(remote, (k, v) -> {
			if (v == null) return null;
			if (tunnelPort != null && v._1() != tunnelPort) return v;
			var connections = v._2();
			var client = v._3();
			return Tuple.of(v._1(), connections - 1, client);
		});
		taskScheduler.schedule(() -> cleanupTunnel(tunnelPort, host, username, port), Instant.now().plus(1, ChronoUnit.MINUTES));
	}

	private void cleanupTunnel(Integer tunnelPort, String host, String username, int port) {
		var remote = username + "@" + host + ":" + port;
		tunnels.compute(remote, (k, v) -> {
			if (v == null) return null;
			if (tunnelPort != null && v._1() != tunnelPort) return v;
			var connections = v._2();
			var client = v._3();
			if (connections <= 0) {
				client.stop();
				return null;
			}
			return v;
		});
	}

	private void killTunnel(String host, String username, int port) {
		var remote = username + "@" + host + ":" + port;
		tunnels.compute(remote, (k, v) -> {
			if (v == null) return null;
			var client = v._3();
			client.stop();
			return null;
		});
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
