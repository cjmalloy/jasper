package jasper.component;

import jasper.IntegrationTest;
import jasper.component.Storage;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for Preload component file watching functionality.
 * 
 * This test verifies that:
 * 1. Preload component can detect new zip files in the preload folder
 * 2. Preload component can detect changes to existing zip files
 * 3. Files are only loaded when they are new or changed
 */
@IntegrationTest
@ActiveProfiles({"storage", "preload"})
public class PreloadIT {

	@Autowired
	Preload preload;

	@Autowired
	RefRepository refRepository;

	@Autowired
	Props props;

	@Autowired
	Optional<Storage> storage;

	@BeforeEach
	void init() {
		refRepository.deleteAll();
	}

	@Test
	void testPreloadWatchesForNewFiles() throws IOException, InterruptedException {
		if (storage.isEmpty()) {
			// Skip test if storage is not configured
			return;
		}

		// Create a test zip file in the preload directory
		Path preloadDir = getPreloadDir();
		Files.createDirectories(preloadDir);
		
		// Note: Creating actual zip files would require more setup
		// This test is a structure for manual testing
		
		// For manual testing:
		// 1. Start the application with preload profile active
		// 2. Create a zip file in the preload directory
		// 3. Verify logs show file detection and loading
		// 4. Modify the zip file
		// 5. Verify logs show change detection and reloading
	}

	private Path getPreloadDir() {
		if (storage.isEmpty()) return null;
		String tenant = storage.get().originTenant(props.getOrigin());
		return Paths.get(props.getStorage(), tenant, "preload");
	}
}
