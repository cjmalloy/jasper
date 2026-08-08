package jasper.component.cron;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.setLastModifiedTime;
import static java.nio.file.Files.writeString;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;

class ExpireScriptDepsTest {

	private String tmpDir;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		tmpDir = System.getProperty("java.io.tmpdir");
		System.setProperty("java.io.tmpdir", tempDir.toString());
	}

	@AfterEach
	void tearDown() {
		System.setProperty("java.io.tmpdir", tmpDir);
	}

	@Test
	void removesOnlyExpiredScriptDependencies() throws IOException {
		var expired = createDirectories(tempDir.resolve("jasper-node-expired"));
		var expiredPackage = writeString(expired.resolve("package.json"), "{}");
		var expiredModule = writeString(createDirectories(expired.resolve("node_modules")).resolve("module.js"), "");
		setLastModifiedTime(expiredPackage, FileTime.from(now().minus(25, HOURS)));

		var active = createDirectories(tempDir.resolve("jasper-node-active"));
		var activePackage = writeString(active.resolve("package.json"), "{}");

		var unrelated = createDirectories(tempDir.resolve("project"));
		var unrelatedPackage = writeString(unrelated.resolve("package.json"), "{}");
		setLastModifiedTime(unrelatedPackage, FileTime.from(now().minus(25, HOURS)));

		new ExpireScriptDeps().removeExpiredScriptDeps();

		assertThat(expiredPackage).doesNotExist();
		assertThat(expiredModule).doesNotExist();
		assertThat(activePackage).exists();
		assertThat(unrelatedPackage).exists();
	}
}
