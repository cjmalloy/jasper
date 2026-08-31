package jasper.component;

import jasper.component.Storage.Zipped;
import jasper.service.dto.BackupOptionsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupTest {
	private static final String ORIGIN = "@tenant";

	@Mock
	Storage storage;

	@Mock
	Zipped zipped;

	@Mock
	ConfigCache configs;

	Backup backup;

	@BeforeEach
	void setup() {
		backup = new Backup();
		backup.storage = Optional.of(storage);
		backup.configs = configs;
	}

	@Test
	void restoreClearsCaches() throws IOException {
		when(storage.streamZip(ORIGIN, "backups", "test-backup.zip")).thenReturn(zipped);

		backup.restore(ORIGIN, "test-backup", new BackupOptionsDto());

		verifyCachesCleared();
	}

	@Test
	void restoreFailureClearsCaches() throws IOException {
		when(storage.streamZip(ORIGIN, "backups", "test-backup.zip")).thenThrow(new IOException("restore failed"));

		assertThatThrownBy(() -> backup.restore(ORIGIN, "test-backup", new BackupOptionsDto()))
			.isInstanceOf(RuntimeException.class);

		verifyCachesCleared();
	}

	private void verifyCachesCleared() {
		verify(configs).clearUserCache(ORIGIN);
		verify(configs).clearPluginCache(ORIGIN);
		verify(configs).clearTemplateCache(ORIGIN);
		verify(configs).clearConfigCache(ORIGIN);
	}
}
