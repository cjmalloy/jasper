package jasper.component;


import io.micrometer.core.annotation.Timed;
import jasper.config.Props;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static jasper.domain.proj.HasOrigin.formatOrigin;

@Profile("storage")
@Component
public class StorageImplLocal implements Storage {
	private final Logger logger = LoggerFactory.getLogger(StorageImplLocal.class);

	@Autowired
	Props props;

	@Autowired
	RefRepository refRepository;

	@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
	public void clearDeleted() {
		visitTenants(origin -> {
			var deleteLater = new ArrayList<String>();
			visitCache(origin, id -> {
				if (!refRepository.cacheExists(id)) deleteLater.add(id);
			});
			deleteLater.forEach(id -> {
				try {
					delete(origin, id);
				} catch (IOException e) {
					logger.error("Cannot delete file", e);
				}
			});
		});
	}

	@Timed(value = "jasper.cache", histogram = true)
	public byte[] get(String origin, String id) {
		try {
			return Files.readAllBytes(path(origin, id));
		} catch (IOException e) {
			throw new NotFoundException("Cache " + id);
		}
	}

	@Timed(value = "jasper.cache", histogram = true)
	public long size(String origin, String id) {
		return path(origin, id).toFile().length();
	}

	@Timed(value = "jasper.cache", histogram = true)
	public long stream(String origin, String id, OutputStream os) {
		try {
			return StreamUtils.copy(new FileInputStream(path(origin, id).toFile()), os);
		} catch (IOException e) {
			throw new NotFoundException("Cache " + id);
		}
	}

	public List<String> listCache(String origin) {
		var result = new ArrayList<String>();
		visitCache(origin, result::add);
		return result;
	}

	public void visitCache(String origin, PathVisitor v) {
		var dir = dir(origin);
		if (!dir.toFile().exists()) return;
		try (var list = Files.list(dir)) {
			list.forEach(p -> v.visit(p.getFileName().toString()));
		} catch (IOException e) {
			logger.warn("Error reading cache", e);
		}
	}

	public void visitTenants(PathVisitor v) {
		var dir = tenats();
		if (!dir.toFile().exists()) return;
		try (var list = Files.list(dir)) {
			list.forEach(t -> {
				if (!t.toFile().isDirectory()) return;
				var origin = t.getFileName().toString();
				if (origin.equals("default")) {
					v.visit("");
				} else if (!origin.startsWith("@")) {
					v.visit(origin);
				}
			});
		} catch (IOException e) {
			logger.warn("Error reading tenant dir", e);
		}
	}

	private boolean exists(String origin, String id) {
		return path(origin, id).toFile().exists();
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void overwrite(String origin, String id, byte[] cache) throws IOException {
		if (!exists(origin, id)) throw new NotFoundException("Cache " + id);
		var path = path(origin, id);
		Files.write(path, cache, StandardOpenOption.TRUNCATE_EXISTING);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public String store(String origin, byte[] cache) throws IOException {
		var id = "cache_" + UUID.randomUUID();
		var path = path(origin, id);
		Files.createDirectories(path.getParent());
		Files.write(path, cache, StandardOpenOption.CREATE_NEW);
		return id;
	}

	@Timed(value = "jasper.storage", histogram = true)
	public String store(String origin, InputStream is) throws IOException {
		var id = "cache_" + UUID.randomUUID();
		var path = path(origin, id);
		Files.createDirectories(path.getParent());
		StreamUtils.copy(is, new FileOutputStream(path.toFile()));
		return id;
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void delete(String origin, String id) throws IOException {
		Files.delete(path(origin, id));
	}

	Path tenats() {
		return Paths.get(props.getStorage());
	}

	Path dir(String origin) {
		return Paths.get(props.getStorage(), formatOrigin(origin), "cache");
	}

	Path path(String origin, String id) {
		if (id.contains("/") || id.contains("\\")) throw new NotFoundException("Illegal characters");
		return Paths.get(props.getStorage(), formatOrigin(origin), "cache", id);
	}

	public interface PathVisitor {
		void visit(String filename);
	}
}
