package jasper.component;

import jasper.config.Props;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class BackupSecondaryStorageTest {

	@TempDir
	Path primaryDir;

	@TempDir
	Path secondaryDir;

	Backup backup;
	Props props;

	@BeforeEach
	void init() {
		props = new Props();
		props.setStorage(primaryDir.toString());
		props.setSecondaryBackupStorage(secondaryDir.toString());
		var storage = new StorageImplLocal();
		storage.props = props;
		backup = new Backup();
		backup.props = props;
		backup.storage = Optional.of(storage);
	}

	void write(Path root, String id, String content) throws IOException {
		var dir = root.resolve("default").resolve("backups");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve(id + ".zip"), content);
	}

	@Test
	void testListIncludesSecondaryBackups() throws IOException {
		write(primaryDir, "primary", "p");
		write(secondaryDir, "secondary", "s");

		assertThat(backup.listBackups(""))
			.extracting(Storage.StorageRef::id)
			.containsExactlyInAnyOrder("primary.zip", "secondary.zip");
	}

	@Test
	void testListPrefersPrimaryBackups() throws IOException {
		write(primaryDir, "both", "primary");
		write(secondaryDir, "both", "secondary backup");

		var backups = backup.listBackups("");
		assertThat(backups).hasSize(1);
		assertThat(backups.getFirst().size()).isEqualTo("primary".length());
	}

	@Test
	void testListWithoutSecondaryStorage() throws IOException {
		props.setSecondaryBackupStorage(null);
		write(primaryDir, "primary", "p");
		write(secondaryDir, "secondary", "s");

		assertThat(backup.listBackups(""))
			.extracting(Storage.StorageRef::id)
			.containsExactly("primary.zip");
		assertThat(backup.exists("", "secondary")).isFalse();
	}

	@Test
	void testGetSecondaryBackup() throws IOException {
		write(secondaryDir, "secondary", "secondary backup");

		assertThat(backup.exists("", "secondary")).isTrue();
		var stream = backup.get("", "secondary");
		assertThat(stream.size()).isEqualTo("secondary backup".length());
		try (var is = stream.inputStream()) {
			assertThat(new String(is.readAllBytes())).isEqualTo("secondary backup");
		}
	}

	@Test
	void testGetPrefersPrimaryBackup() throws IOException {
		write(primaryDir, "both", "primary");
		write(secondaryDir, "both", "secondary backup");

		try (var is = backup.get("", "both").inputStream()) {
			assertThat(new String(is.readAllBytes())).isEqualTo("primary");
		}
	}

	@Test
	void testDeleteDoesNotDeleteSecondaryBackup() throws IOException {
		write(secondaryDir, "secondary", "s");

		backup.delete("", "secondary");

		assertThat(Files.exists(secondaryDir.resolve("default/backups/secondary.zip"))).isTrue();
		assertThat(backup.exists("", "secondary")).isTrue();
	}

	@Test
	void testDeletePrimaryBackupRevealsSecondary() throws IOException {
		write(primaryDir, "both", "primary");
		write(secondaryDir, "both", "secondary backup");

		backup.delete("", "both");

		assertThat(Files.exists(primaryDir.resolve("default/backups/both.zip"))).isFalse();
		assertThat(backup.get("", "both").size()).isEqualTo("secondary backup".length());
	}
}
