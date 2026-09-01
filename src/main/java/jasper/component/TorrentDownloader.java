package jasper.component;

import bt.Bt;
import bt.data.file.FileSystemStorage;
import bt.dht.DHTConfig;
import bt.dht.DHTModule;
import bt.magnet.MagnetUriParser;
import bt.metainfo.IMetadataService;
import bt.metainfo.Torrent;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import jakarta.annotation.PreDestroy;
import jasper.security.HostCheck;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

@Component
@Profile("proxy")
public class TorrentDownloader {

	@Autowired
	HostCheck hostCheck;

	private BtRuntime runtime;

	public Torrent download(String magnet, Path target) throws IOException {
		var magnetUri = MagnetUriParser.lenientParser().parse(magnet);
		checkTrackerHosts(magnetUri.getTrackerUrls());
		var torrent = new Torrent[1];
		var client = Bt.client(runtime())
			.magnet(magnetUri)
			.storage(new FileSystemStorage(target))
			.afterTorrentFetched(value -> torrent[0] = value)
			.stopWhenDownloaded()
			.build();
		run(client);
		if (torrent[0] == null) throw new IOException("Torrent metadata was not received");
		return torrent[0];
	}

	public Torrent download(InputStream metainfo, Path target) throws IOException {
		var runtime = runtime();
		var torrent = runtime.service(IMetadataService.class).fromInputStream(metainfo);
		checkTrackerHosts(torrent.getAnnounceKey()
			.map(key -> key.isMultiKey() ? key.getTrackerUrls().stream().flatMap(Collection::stream).toList() : java.util.List.of(key.getTrackerUrl()))
			.orElseGet(java.util.List::of));
		var client = Bt.client(runtime)
			.torrent(() -> torrent)
			.storage(new FileSystemStorage(target))
			.stopWhenDownloaded()
			.build();
		run(client);
		return torrent;
	}

	private void checkTrackerHosts(Collection<String> trackers) throws IOException {
		for (var tracker : trackers) {
			var uri = URI.create(tracker);
if (!hostCheck.validHost(uri)) {
				throw new IOException("Invalid torrent tracker host");
			}
		}
	}

	private void run(BtClient client) throws IOException {
		try {
			client.startAsync(state -> {}, 1000).get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Torrent download interrupted", e);
		} catch (ExecutionException e) {
			throw new IOException("Torrent download failed", e.getCause());
		} finally {
			client.stop();
		}
	}

	private synchronized BtRuntime runtime() {
		if (runtime == null) {
			var dhtConfig = new DHTConfig();
			dhtConfig.setShouldUseRouterBootstrap(true);
			runtime = BtRuntime.builder()
				.autoLoadModules()
				.module(new DHTModule(dhtConfig))
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
}
