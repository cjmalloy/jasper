package jasper.component;

import jasper.config.Props;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.repository.ExtRepository;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PreloadTest {

	@Mock
	Props props;

	@Mock
	RefRepository refRepository;

	@Mock
	ExtRepository extRepository;

	@Mock
	UserRepository userRepository;

	@Mock
	PluginRepository pluginRepository;

	@Mock
	TemplateRepository templateRepository;

	@Mock
	Storage storage;

	@Mock
	Backup backup;

	Preload preload;

	@BeforeEach
	void init() {
		preload = new Preload();
		preload.props = props;
		preload.refRepository = refRepository;
		preload.extRepository = extRepository;
		preload.userRepository = userRepository;
		preload.pluginRepository = pluginRepository;
		preload.templateRepository = templateRepository;
		preload.storage = Optional.of(storage);
		preload.backup = backup;
		
		when(props.getOrigin()).thenReturn("");
	}

	@Test
	void testLoadJsonFileRef() {
		var json = "{\"url\":\"https://example.com\"}";
		var inputStream = new ByteArrayInputStream(json.getBytes());
		
		when(storage.stream(anyString(), eq("preload"), eq("ref-test.json")))
			.thenReturn(inputStream);

		preload.loadJsonFile("", "ref-test.json");

		verify(backup).restoreRepo(eq(refRepository), eq(""), any(InputStream.class), eq(Ref.class));
	}

	@Test
	void testLoadJsonFileExt() {
		var json = "{\"tag\":\"test\"}";
		var inputStream = new ByteArrayInputStream(json.getBytes());
		
		when(storage.stream(anyString(), eq("preload"), eq("ext-test.json")))
			.thenReturn(inputStream);

		preload.loadJsonFile("", "ext-test.json");

		verify(backup).restoreRepo(eq(extRepository), eq(""), any(InputStream.class), eq(Ext.class));
	}

	@Test
	void testLoadJsonFileUser() {
		var json = "{\"tag\":\"+user/test\"}";
		var inputStream = new ByteArrayInputStream(json.getBytes());
		
		when(storage.stream(anyString(), eq("preload"), eq("user-test.json")))
			.thenReturn(inputStream);

		preload.loadJsonFile("", "user-test.json");

		verify(backup).restoreRepo(eq(userRepository), eq(""), any(InputStream.class), eq(User.class));
	}

	@Test
	void testLoadJsonFilePlugin() {
		var json = "{\"tag\":\"plugin/test\"}";
		var inputStream = new ByteArrayInputStream(json.getBytes());
		
		when(storage.stream(anyString(), eq("preload"), eq("plugin-test.json")))
			.thenReturn(inputStream);

		preload.loadJsonFile("", "plugin-test.json");

		verify(backup).restoreRepo(eq(pluginRepository), eq(""), any(InputStream.class), eq(Plugin.class));
	}

	@Test
	void testLoadJsonFileTemplate() {
		var json = "{\"tag\":\"_template/test\"}";
		var inputStream = new ByteArrayInputStream(json.getBytes());
		
		when(storage.stream(anyString(), eq("preload"), eq("template-test.json")))
			.thenReturn(inputStream);

		preload.loadJsonFile("", "template-test.json");

		verify(backup).restoreRepo(eq(templateRepository), eq(""), any(InputStream.class), eq(Template.class));
	}

	@Test
	void testLoadJsonFileWithOrigin() {
		var json = "{\"url\":\"https://example.com\"}";
		var inputStream = new ByteArrayInputStream(json.getBytes());
		
		when(storage.stream(eq("tenant1.example.com"), eq("preload"), eq("ref-test.json")))
			.thenReturn(inputStream);

		preload.loadJsonFile("tenant1.example.com", "ref-test.json");

		verify(backup).restoreRepo(eq(refRepository), eq("tenant1.example.com"), any(InputStream.class), eq(Ref.class));
	}

	@Test
	void testLoadJsonFileCaseInsensitive() {
		var json = "{\"url\":\"https://example.com\"}";
		var inputStream = new ByteArrayInputStream(json.getBytes());
		
		when(storage.stream(anyString(), eq("preload"), eq("REF-TEST.JSON")))
			.thenReturn(inputStream);

		preload.loadJsonFile("", "REF-TEST.JSON");

		// Should match "ref.*\\.json" pattern even with uppercase
		verify(backup).restoreRepo(eq(refRepository), eq(""), any(InputStream.class), eq(Ref.class));
	}

	@Test
	void testLoadJsonFileUnknownType() {
		var json = "{\"data\":\"test\"}";
		var inputStream = new ByteArrayInputStream(json.getBytes());
		
		when(storage.stream(anyString(), eq("preload"), eq("unknown-test.json")))
			.thenReturn(inputStream);

		preload.loadJsonFile("", "unknown-test.json");

		// Should not call restoreRepo for unknown type
		verify(backup, never()).restoreRepo(any(), anyString(), any(InputStream.class), any());
	}

	@Test
	void testLoadJsonFileWithError() {
		when(storage.stream(anyString(), eq("preload"), eq("ref-test.json")))
			.thenThrow(new RuntimeException("Storage error"));

		// Should not throw exception, just log error
		preload.loadJsonFile("", "ref-test.json");

		verify(storage).stream(anyString(), eq("preload"), eq("ref-test.json"));
	}

	@Test
	void testLoadStaticZipFile() {
		var mockZipped = mock(Storage.Zipped.class);
		when(storage.streamZip(anyString(), eq("preload"), eq("backup.zip")))
			.thenReturn(mockZipped);
		when(mockZipped.list("ref.*\\.json")).thenReturn(List.of());
		when(mockZipped.list("ext.*\\.json")).thenReturn(List.of());
		when(mockZipped.list("user.*\\.json")).thenReturn(List.of());
		when(mockZipped.list("plugin.*\\.json")).thenReturn(List.of());
		when(mockZipped.list("template.*\\.json")).thenReturn(List.of());

		preload.loadStatic("", "backup.zip");

		verify(backup).restoreRepo(eq(refRepository), eq(""), any(List.class), eq(Ref.class));
		verify(backup).restoreRepo(eq(extRepository), eq(""), any(List.class), eq(Ext.class));
		verify(backup).restoreRepo(eq(userRepository), eq(""), any(List.class), eq(User.class));
		verify(backup).restoreRepo(eq(pluginRepository), eq(""), any(List.class), eq(Plugin.class));
		verify(backup).restoreRepo(eq(templateRepository), eq(""), any(List.class), eq(Template.class));
	}

	@Test
	void testLoadStaticWithError() {
		when(storage.streamZip(anyString(), eq("preload"), eq("backup.zip")))
			.thenThrow(new RuntimeException("Zip error"));

		// Should not throw exception, just log error
		preload.loadStatic("", "backup.zip");

		verify(storage).streamZip(anyString(), eq("preload"), eq("backup.zip"));
	}

	@Test
	void testLoadStaticNoStorage() {
		preload.storage = Optional.empty();

		// Should not throw exception
		preload.loadStatic("", "backup.zip");

		verify(storage, never()).streamZip(anyString(), anyString(), anyString());
	}

	@Test
	void testLoadJsonFileNoStorage() {
		preload.storage = Optional.empty();

		// Should not throw exception
		preload.loadJsonFile("", "ref-test.json");

		verify(storage, never()).stream(anyString(), anyString(), anyString());
	}

	@Test
	void testInitWithNoStorage() {
		preload.storage = Optional.empty();

		// Should not throw exception
		preload.init();

		verify(storage, never()).listStorage(anyString(), anyString());
	}

	@Test
	void testInitWithFiles() {
		var file1 = mock(Storage.FileId.class);
		when(file1.id()).thenReturn("ref-data.json");
		
		var file2 = mock(Storage.FileId.class);
		when(file2.id()).thenReturn("backup.zip");
		
		when(storage.listStorage(eq(""), eq("preload")))
			.thenReturn(List.of(file1, file2));

		var json = "{\"url\":\"https://example.com\"}";
		when(storage.stream(anyString(), eq("preload"), eq("ref-data.json")))
			.thenReturn(new ByteArrayInputStream(json.getBytes()));

		var mockZipped = mock(Storage.Zipped.class);
		when(storage.streamZip(anyString(), eq("preload"), eq("backup.zip")))
			.thenReturn(mockZipped);
		when(mockZipped.list(anyString())).thenReturn(List.of());

		preload.init();

		verify(storage).stream(eq(""), eq("preload"), eq("ref-data.json"));
		verify(storage).streamZip(eq(""), eq("preload"), eq("backup.zip"));
	}
}
