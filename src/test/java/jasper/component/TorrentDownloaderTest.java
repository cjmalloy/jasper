package jasper.component;

import bt.net.InetPeer;
import bt.metainfo.Torrent;
import bt.runtime.BtClient;
import jasper.config.Props;
import jasper.security.HostCheck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class TorrentDownloaderTest {

	TorrentDownloader downloader;
	Props props;

	@BeforeEach
	void setUp() {
		downloader = new TorrentDownloader();
		props = new Props();
		downloader.props = props;
		downloader.hostCheck = mock(HostCheck.class);
	}

	@Test
	void stopsClientWhenDownloadTimesOut() {
		var client = mock(BtClient.class);
		when(client.startAsync(any(), anyLong())).thenReturn(new CompletableFuture<>());

		assertThatThrownBy(() -> downloader.run(client, Duration.ofMillis(1)))
			.isInstanceOf(IOException.class)
			.hasMessage("Torrent download timed out");
		verify(client).stop();
	}

	@Test
	void rejectsTorrentOverMaximumSize() {
		var torrent = mock(Torrent.class);
		props.getTorrent().setMaxSizeBytes(10);
		when(torrent.getSize()).thenReturn(11L);

		assertThatThrownBy(() -> downloader.checkSize(torrent))
			.isInstanceOf(IOException.class)
			.hasMessage("Torrent exceeds maximum size");
	}

	@Test
	void acceptsUdpTrackerAndRejectsUnsupportedScheme() throws Exception {
		when(downloader.hostCheck.validHost(URI.create("udp://tracker.example:80"))).thenReturn(true);

		downloader.checkTrackerHosts(List.of("udp://tracker.example:80"));
		assertThatThrownBy(() -> downloader.checkTrackerHosts(List.of("ftp://tracker.example/file")))
			.isInstanceOf(IOException.class)
			.hasMessage("Invalid torrent tracker host");
	}

	@Test
	void rejectsPrivatePeerAddress() throws Exception {
		var peer = InetPeer.builder(InetAddress.getByName("127.0.0.1"), 6881).build();
		when(downloader.hostCheck.validHost(any())).thenReturn(false);

		assertThat(downloader.validPeer(peer)).isFalse();
		verify(downloader.hostCheck).validHost(URI.create("tcp://127.0.0.1:6881"));
	}
}
