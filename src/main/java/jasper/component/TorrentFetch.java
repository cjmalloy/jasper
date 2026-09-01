package jasper.component;

import bt.metainfo.Torrent;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
@Profile("proxy")
public class TorrentFetch {

	@Autowired
	TorrentDownloader downloader;

	public Fetch.FileRequest fetch(String magnet) throws IOException {
		return fetch(target -> downloader.download(magnet, target));
	}

	public Fetch.FileRequest fetch(InputStream metainfo) throws IOException {
		return fetch(target -> downloader.download(metainfo, target));
	}

	private Fetch.FileRequest fetch(Download download) throws IOException {
		var target = Files.createTempDirectory("jasper-torrent-");
		var complete = false;
		try {
			var torrent = download.into(target);
			var request = request(target, torrent);
			complete = true;
			return request;
		} finally {
			if (!complete) FileUtils.deleteQuietly(target.toFile());
		}
	}

	private Fetch.FileRequest request(Path target, Torrent torrent) throws IOException {
		List<Path> files;
		try (var paths = Files.walk(target)) {
			files = paths
				.filter(Files::isRegularFile)
				.sorted(Comparator.naturalOrder())
				.toList();
		}
		if (files.isEmpty()) throw new IOException("Torrent contained no files");
		if (torrent.getFiles().size() == 1 && files.size() == 1) {
			var mimeType = Files.probeContentType(files.getFirst());
			return new TemporaryFileRequest(target, files.getFirst(), mimeType == null ? "application/octet-stream" : mimeType);
		}
		return new TemporaryFileRequest(target, zip(target, files), "application/zip");
	}

	private Path zip(Path target, List<Path> files) throws IOException {
		var archive = Files.createTempFile(target, "torrent-", ".zip");
		try (var out = new ZipOutputStream(Files.newOutputStream(archive))) {
			for (var file : files) {
				out.putNextEntry(new ZipEntry(target.relativize(file).toString().replace(File.separatorChar, '/')));
				Files.copy(file, out);
				out.closeEntry();
			}
		} catch (IOException e) {
			Files.deleteIfExists(archive);
			throw e;
		}
		return archive;
	}

	@FunctionalInterface
	private interface Download {
		Torrent into(Path target) throws IOException;
	}

	private static class TemporaryFileRequest implements Fetch.FileRequest {
		private final Path target;
		private final InputStream source;
		private final InputStream inputStream;
		private final String mimeType;
		private final AtomicBoolean closed = new AtomicBoolean();

		private TemporaryFileRequest(Path target, Path file, String mimeType) throws IOException {
			this.target = target;
			this.source = Files.newInputStream(file);
			this.inputStream = new FilterInputStream(source) {
				@Override
				public void close() throws IOException {
					TemporaryFileRequest.this.close();
				}
			};
			this.mimeType = mimeType;
		}

		@Override
		public String getMimeType() {
			return mimeType;
		}

		@Override
		public InputStream getInputStream() {
			return inputStream;
		}

		@Override
		public void close() throws IOException {
			if (closed.compareAndSet(false, true)) {
				try {
					source.close();
				} finally {
					FileUtils.deleteQuietly(target.toFile());
				}
			}
		}
	}
}
