package jasper.component;

import bt.Bt;
import bt.data.file.FileSystemStorage;
import bt.dht.DHTConfig;
import bt.dht.DHTModule;
import bt.event.EventSource;
import bt.magnet.MagnetUriParser;
import bt.metainfo.IMetadataService;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.module.BitTorrentProtocol;
import bt.net.ConnectionResult;
import bt.net.DataReceiver;
import bt.net.IConnectionHandlerFactory;
import bt.net.IPeerConnectionFactory;
import bt.net.Peer;
import bt.net.PeerConnectionFactory;
import bt.net.buffer.IBufferManager;
import bt.net.pipeline.IChannelPipelineFactory;
import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;
import bt.runtime.Config;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.torrent.TorrentRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import jakarta.annotation.PreDestroy;
import jasper.config.Props;
import jasper.security.HostCheck;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Profile("proxy")
public class TorrentDownloader {

	@Autowired
	HostCheck hostCheck;

	@Autowired
	Props props;

	private final ReentrantLock downloadLock = new ReentrantLock();
	private BtRuntime runtime;

	public Torrent download(String magnet, Path target) throws IOException {
		if (!downloadLock.tryLock()) throw new IOException("Another torrent download is in progress");
		try {
			return downloadMagnet(magnet, target);
		} finally {
			downloadLock.unlock();
		}
	}

	private Torrent downloadMagnet(String magnet, Path target) throws IOException {
		checkEnabled();
		var magnetUri = MagnetUriParser.lenientParser().parse(magnet);
		checkTrackerHosts(magnetUri.getTrackerUrls());
		var torrent = new AtomicReference<Torrent>();
		var clientReference = new AtomicReference<BtClient>();
		var metadata = new CompletableFuture<Torrent>();
		var rejected = new CompletableFuture<Void>();
		var downloaded = new AtomicBoolean();
		var client = Bt.client(runtime())
			.magnet(magnetUri)
			.storage(new FileSystemStorage(target))
			.afterTorrentFetched(value -> {
				try {
					checkSize(value);
					torrent.set(value);
					metadata.complete(value);
				} catch (IOException e) {
					var runningClient = clientReference.get();
					if (runningClient != null) runningClient.stop();
					metadata.completeExceptionally(e);
					rejected.join();
				}
			})
			.afterDownloaded(value -> downloaded.set(true))
			.stopWhenDownloaded()
			.build();
		clientReference.set(client);
		try {
			run(client, metadata, rejected, downloaded);
			return torrent.get();
		} catch (IOException e) {
			FileUtils.deleteQuietly(target.toFile());
			throw e;
		}
	}

	public Torrent download(InputStream metainfo, Path target) throws IOException {
		if (!downloadLock.tryLock()) throw new IOException("Another torrent download is in progress");
		try {
			return downloadMetainfo(metainfo, target);
		} finally {
			downloadLock.unlock();
		}
	}

	private Torrent downloadMetainfo(InputStream metainfo, Path target) throws IOException {
		checkEnabled();
		var runtime = runtime();
		var torrent = runtime.service(IMetadataService.class).fromInputStream(metainfo);
		checkSize(torrent);
		checkTrackerHosts(torrent.getAnnounceKey()
			.map(key -> key.isMultiKey() ? key.getTrackerUrls().stream().flatMap(Collection::stream).toList() : java.util.List.of(key.getTrackerUrl()))
			.orElseGet(java.util.List::of));
		var downloaded = new AtomicBoolean();
		var client = Bt.client(runtime)
			.torrent(() -> torrent)
			.storage(new FileSystemStorage(target))
			.afterDownloaded(value -> downloaded.set(true))
			.stopWhenDownloaded()
			.build();
		try {
			run(client, props.getTorrent().getDownloadTimeout(), downloaded);
			return torrent;
		} catch (IOException e) {
			FileUtils.deleteQuietly(target.toFile());
			throw e;
		}
	}

	void checkTrackerHosts(Collection<String> trackers) throws IOException {
		for (var tracker : trackers) {
			try {
				var uri = URI.create(tracker);
				var scheme = uri.getScheme();
				if (scheme == null || !java.util.Set.of("http", "https", "udp").contains(scheme.toLowerCase(Locale.ROOT))
					|| !hostCheck.validHost(uri)) {
					throw new IOException("Invalid torrent tracker host");
				}
			} catch (IllegalArgumentException e) {
				throw new IOException("Invalid torrent tracker URI", e);
			}
		}
	}

	private void checkEnabled() throws IOException {
		if (!props.getTorrent().isEnabled()) throw new IOException("Torrent downloads are disabled");
	}

	void checkSize(Torrent torrent) throws IOException {
		if (torrent.getSize() > props.getTorrent().getMaxSizeBytes()) {
			throw new IOException("Torrent exceeds maximum size");
		}
	}

	void run(BtClient client, CompletableFuture<Torrent> metadata, CompletableFuture<Void> rejected,
		AtomicBoolean downloaded) throws IOException {
		var future = client.startAsync(state -> {}, 1000);
		try {
			metadata.get(props.getTorrent().getMetadataTimeout().toMillis(), TimeUnit.MILLISECONDS);
			future.get(props.getTorrent().getDownloadTimeout().toMillis(), TimeUnit.MILLISECONDS);
			checkDownloaded(downloaded);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Torrent download interrupted", e);
		} catch (ExecutionException e) {
			if (e.getCause() instanceof IOException ioException) {
				try {
					shutdown();
				} finally {
					rejected.complete(null);
				}
				throw ioException;
			}
			throw new IOException("Torrent download failed", e.getCause());
		} catch (TimeoutException e) {
			shutdown();
			if (!metadata.isDone()) throw new IOException("Timed out fetching torrent metadata", e);
			throw new IOException("Torrent download timed out", e);
		} finally {
			client.stop();
		}
	}

	void run(BtClient client, Duration timeout, AtomicBoolean downloaded) throws IOException {
		try {
			client.startAsync(state -> {}, 1000).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
			checkDownloaded(downloaded);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Torrent download interrupted", e);
		} catch (ExecutionException e) {
			throw new IOException("Torrent download failed", e.getCause());
		} catch (TimeoutException e) {
			shutdown();
			throw new IOException("Torrent download timed out", e);
		} finally {
			client.stop();
		}
	}

	private void checkDownloaded(AtomicBoolean downloaded) throws IOException {
		if (!downloaded.get()) {
			shutdown();
			throw new IOException("Torrent download stopped before completion");
		}
	}

	private synchronized BtRuntime runtime() {
		if (runtime == null) {
			var config = new Config();
			config.setAcceptorPort(props.getTorrent().getPeerPort());
			var dhtConfig = new DHTConfig();
			dhtConfig.setListeningPort(props.getTorrent().getDhtPort());
			dhtConfig.setShouldUseRouterBootstrap(true);
			runtime = BtRuntime.builder(config)
				.autoLoadModules()
				.module(new DHTModule(dhtConfig))
				.module(new PeerFilterModule())
				.disableAutomaticShutdown()
				.disableLocalServiceDiscovery()
				.build();
			runtime.startup();
		}
		return runtime;
	}

	@PreDestroy
	public synchronized void shutdown() {
		if (runtime != null) {
			runtime.shutdown();
			runtime = null;
		}
	}

	boolean validPeer(Peer peer) {
		try {
			return hostCheck.validHost(new URI("tcp", null, peer.getInetAddress().getHostAddress(), peer.getPort(), null, null, null));
		} catch (URISyntaxException e) {
			return false;
		}
	}

	private class PeerFilterModule extends AbstractModule {
		@Provides
		@Singleton
		IPeerConnectionFactory providePeerConnectionFactory(
			@bt.module.PeerConnectionSelector java.nio.channels.Selector selector,
			IConnectionHandlerFactory connectionHandlerFactory,
			@BitTorrentProtocol MessageHandler<Message> bittorrentProtocol,
			TorrentRegistry torrentRegistry,
			IChannelPipelineFactory channelPipelineFactory,
			IBufferManager bufferManager,
			DataReceiver dataReceiver,
			EventSource eventSource,
			Config config
		) {
			return new PeerFilteringConnectionFactory(new PeerConnectionFactory(selector, connectionHandlerFactory,
				channelPipelineFactory, bittorrentProtocol, torrentRegistry, bufferManager, dataReceiver, eventSource, config));
		}
	}

	private class PeerFilteringConnectionFactory implements IPeerConnectionFactory {
		private final IPeerConnectionFactory delegate;

		private PeerFilteringConnectionFactory(IPeerConnectionFactory delegate) {
			this.delegate = delegate;
		}

		@Override
		public ConnectionResult createOutgoingConnection(Peer peer, TorrentId torrentId) {
			return validPeer(peer) ? delegate.createOutgoingConnection(peer, torrentId) : ConnectionResult.failure("Invalid peer host");
		}

		@Override
		public ConnectionResult createIncomingConnection(Peer peer, SocketChannel channel) {
			return validPeer(peer) ? delegate.createIncomingConnection(peer, channel) : ConnectionResult.failure("Invalid peer host");
		}
	}
}
