package jasper.component;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TorrentFetchTest {

	@InjectMocks
	TorrentFetch fetch;

	@Mock
	TorrentDownloader downloader;

	@Mock
	Torrent torrent;

	private AutoCloseable mocks;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);
	}

	@AfterEach
	void tearDown() throws Exception {
		mocks.close();
	}

	@Test
	void returnsSingleFileAndDeletesTemporaryDownload() throws Exception {
		var target = new AtomicReference<Path>();
		when(torrent.getFiles()).thenReturn(List.of(mock(TorrentFile.class)));
		when(downloader.download(eq("magnet:test"), any())).thenAnswer(invocation -> {
			target.set(invocation.getArgument(1));
			Files.writeString(target.get().resolve("file.txt"), "contents");
			return torrent;
		});

		var request = fetch.fetch("magnet:test");
		try (var input = request.getInputStream()) {
			assertThat(new String(input.readAllBytes())).isEqualTo("contents");
		}

		assertThat(target.get()).doesNotExist();
	}

	@Test
	void zipsMultiFileTorrent() throws Exception {
		var target = new AtomicReference<Path>();
		when(torrent.getFiles()).thenReturn(List.of(mock(TorrentFile.class), mock(TorrentFile.class)));
		when(downloader.download(eq("magnet:test"), any())).thenAnswer(invocation -> {
			target.set(invocation.getArgument(1));
			Files.createDirectories(target.get().resolve("bundle"));
			Files.writeString(target.get().resolve("bundle/a.txt"), "a");
			Files.writeString(target.get().resolve("bundle/b.txt"), "b");
			return torrent;
		});

		try (var request = fetch.fetch("magnet:test");
			 var input = new ZipInputStream(request.getInputStream())) {
			var entries = new ArrayList<String>();
			for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
				entries.add(entry.getName() + "=" + new String(input.readAllBytes()));
			}
			assertThat(request.getMimeType()).isEqualTo("application/zip");
			assertThat(entries).containsExactly("bundle/a.txt=a", "bundle/b.txt=b");
		}

		assertThat(target.get()).doesNotExist();
	}
}
