package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.domain.Ref;
import jasper.repository.ExtRepository;
import jasper.repository.FeedRepository;
import jasper.repository.OriginRepository;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.StreamMixin;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import jasper.service.dto.BackupOptionsDto;
import jasper.util.JsonArrayStreamDataSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Profile("storage")
@Component
public class Backup {

	@Value("${application.storage}")
	String storagePath;
	@Autowired
	RefRepository refRepository;
	@Autowired
	ExtRepository extRepository;
	@Autowired
	UserRepository userRepository;
	@Autowired
	FeedRepository feedRepository;
	@Autowired
	OriginRepository originRepository;
	@Autowired
	PluginRepository pluginRepository;
	@Autowired
	TemplateRepository templateRepository;
	@Autowired
	ObjectMapper objectMapper;

	@Async
	@Transactional
	public void createBackup(String id, BackupOptionsDto options) throws IOException {
		Files.createDirectories(Paths.get(storagePath, "backups"));
		URI uri = URI.create("jar:file:" + storagePath + "/backups/_" + id + ".zip");
		try (FileSystem zipfs = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
			if (options == null || options.isRef()) {
				backupRepo(refRepository, zipfs.getPath("/ref.json"));
			}
			if (options == null || options.isExt()) {
				backupRepo(extRepository, zipfs.getPath("/ext.json"));
			}
			if (options == null || options.isUser()) {
				backupRepo(userRepository, zipfs.getPath("/user.json"));
			}
			if (options == null || options.isFeed()) {
				backupRepo(feedRepository, zipfs.getPath("/feed.json"));
			}
			if (options == null || options.isOrigin()) {
				backupRepo(originRepository, zipfs.getPath("/origin.json"));
			}
			if (options == null || options.isPlugin()) {
				backupRepo(pluginRepository, zipfs.getPath("/plugin.json"));
			}
			if (options == null || options.isTemplate()) {
				backupRepo(templateRepository, zipfs.getPath("/template.json"));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		// Remove underscore to indicate writing has finished
		Files.move(
			Paths.get(storagePath, "backups", "_" + id + ".zip"),
			Paths.get(storagePath, "backups",       id + ".zip"));
	}

	private void backupRepo(StreamMixin<?> repo, Path path) throws IOException {
		var firstElementProcessed = new AtomicBoolean(false);
		Files.write(path, "[".getBytes(), StandardOpenOption.CREATE);
		repo.streamAllBy().forEach(entity -> {
			try {
				if (firstElementProcessed.getAndSet(true)) {
					Files.write(path, ",\n".getBytes(), StandardOpenOption.APPEND);
				}
				Files.write(path, objectMapper.writeValueAsBytes(entity), StandardOpenOption.APPEND);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
		Files.write(path, "]\n".getBytes(), StandardOpenOption.APPEND);
	}

	public byte[] get(String id) throws IOException {
		return Files.readAllBytes(Paths.get(storagePath, "backups", id + ".zip"));
	}

	public List<String> listBackups() {
		try (var list = Files.list(Paths.get(storagePath, "backups"))) {
			return list
				.map(f -> f.getFileName().toString())
				.collect(Collectors.toList());
		} catch (IOException e) {
			return Collections.emptyList();
		}
	}

	@Async
	public void restore(String id, BackupOptionsDto options) {
		var zipPath = Paths.get(storagePath, "backups", id + ".zip");
		try (FileSystem zipfs = FileSystems.newFileSystem(zipPath)) {
			if (options == null || options.isRef()) {
				var path = zipfs.getPath("/ref.json");
				new JsonArrayStreamDataSupplier<Ref>(Files.newInputStream(path), Ref.class, objectMapper)
					.forEachRemaining(ref -> {
						refRepository.save(ref);
					});
			}
//			if (options == null || options.isRef()) {
//				restoreRepo(refRepository, zipfs.getPath("/ref.json"));
//			}
//			if (options == null || options.isExt()) {
//				restoreRepo(extRepository, zipfs.getPath("/ext.json"));
//			}
//			if (options == null || options.isUser()) {
//				restoreRepo(userRepository, zipfs.getPath("/user.json"));
//			}
//			if (options == null || options.isFeed()) {
//				restoreRepo(feedRepository, zipfs.getPath("/feed.json"));
//			}
//			if (options == null || options.isOrigin()) {
//				restoreRepo(originRepository, zipfs.getPath("/origin.json"));
//			}
//			if (options == null || options.isPlugin()) {
//				restoreRepo(pluginRepository, zipfs.getPath("/plugin.json"));
//			}
//			if (options == null || options.isTemplate()) {
//				restoreRepo(templateRepository, zipfs.getPath("/template.json"));
//			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void store(String id, byte[] zipFile) throws IOException {
		var path = Paths.get(storagePath, "backups", id + ".zip");
		Files.createDirectories(path.getParent());
		Files.write(path, zipFile, StandardOpenOption.CREATE);
	}

	public void delete(String id) throws IOException {
		Files.delete(Paths.get(storagePath, "backups", id + ".zip"));
	}
}
