package jasper.component;

import jasper.config.Props;
import jasper.service.dto.BackupOptionsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BackupTest {

	Backup backup = new Backup();
	Storage storage = mock(Storage.class);
	Storage.Zipped zipped = mock(Storage.Zipped.class);
	ConfigCache configs = mock(ConfigCache.class);

	@BeforeEach
	void init() throws IOException {
		backup.props = new Props();
		backup.storage = Optional.of(storage);
		backup.configs = configs;
		when(storage.streamZip(anyString(), anyString(), anyString())).thenReturn(zipped);
		when(zipped.list(anyString())).thenReturn(Collections.emptyIterator());
	}

	@Test
	void testRestoreFlushesAllCaches() {
		backup.restore("@a", "id", null);

		verify(configs).clearConfigCache();
		verify(configs).clearUserCache("@a");
		verify(configs).clearPluginCache("@a");
		verify(configs).clearTemplateCache("@a");
	}

	@Test
	void testRestoreFlushesOnlyRestoredCaches() {
		var options = new BackupOptionsDto();
		options.setPlugin(true);

		backup.restore("@a", "id", options);

		verify(configs, never()).clearConfigCache();
		verify(configs, never()).clearUserCache(any(String.class));
		verify(configs).clearPluginCache("@a");
		verify(configs, never()).clearTemplateCache(any(String.class));
	}

	@Test
	void testFailedRestoreStillFlushesCaches() throws IOException {
		when(storage.streamZip(anyString(), anyString(), anyString())).thenThrow(new IOException());

		assertThatThrownBy(() -> backup.restore("@a", "id", null)).isInstanceOf(RuntimeException.class);

		verify(configs).clearConfigCache();
		verify(configs).clearUserCache("@a");
		verify(configs).clearPluginCache("@a");
		verify(configs).clearTemplateCache("@a");
	}
}
