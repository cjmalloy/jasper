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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupTest {

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
		when(storage.streamZip("", "backups", "test-backup.zip")).thenReturn(zipped);

		backup.restore("", "test-backup", new BackupOptionsDto());

		verify(configs).clearUserCache();
		verify(configs).clearPluginCache();
		verify(configs).clearTemplateCache();
		verify(configs).clearConfigCache();
	}
}
