package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.component.dto.ComponentDtoMapper;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.service.dto.ExtDto;
import jasper.service.dto.PluginDto;
import jasper.service.dto.RefDto;
import jasper.service.dto.TemplateDto;
import jasper.service.dto.UserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MessagesTest {

	@Mock
	ExecutorService taskExecutor;

	@Mock
	MessageChannel cursorTxChannel;

	@Mock
	MessageChannel refTxChannel;

	@Mock
	MessageChannel tagTxChannel;

	@Mock
	MessageChannel responseTxChannel;

	@Mock
	MessageChannel userTxChannel;

	@Mock
	MessageChannel extTxChannel;

	@Mock
	MessageChannel pluginTxChannel;

	@Mock
	MessageChannel templateTxChannel;

	@Mock
	ComponentDtoMapper mapper;

	@Mock
	ObjectMapper objectMapper;

	@Captor
	ArgumentCaptor<Message<?>> messageCaptor;

	Messages messages;

	@BeforeEach
	void init() {
		messages = new Messages();
		messages.taskExecutor = taskExecutor;
		messages.cursorTxChannel = cursorTxChannel;
		messages.refTxChannel = refTxChannel;
		messages.tagTxChannel = tagTxChannel;
		messages.responseTxChannel = responseTxChannel;
		messages.userTxChannel = userTxChannel;
		messages.extTxChannel = extTxChannel;
		messages.pluginTxChannel = pluginTxChannel;
		messages.templateTxChannel = templateTxChannel;
		messages.mapper = mapper;
		messages.objectMapper = objectMapper;
		messages.ready = true; // Set ready to true to enable message sending
	}

	@Test
	void testUpdateRef() {
		var ref = new Ref();
		ref.setUrl("https://example.com");
		ref.setOrigin("");
		ref.setTags(new ArrayList<>(List.of("tag1", "tag2")));
		ref.setModified(Instant.now());

		var refDto = new RefDto();
		refDto.setUrl("https://example.com");
		refDto.setOrigin("");
		refDto.setTags(new ArrayList<>(List.of("tag1", "tag2")));

		when(refTxChannel.send(any())).thenReturn(true);
		when(tagTxChannel.send(any())).thenReturn(true);
		when(cursorTxChannel.send(any())).thenReturn(true);
		when(mapper.domainToDto(ref)).thenReturn(refDto);

		messages.updateRef(ref);

		verify(refTxChannel).send(any(Message.class));
		verify(cursorTxChannel).send(any(Message.class));
	}

	@Test
	void testUpdateRefWithSources() {
		var ref = new Ref();
		ref.setUrl("https://example.com");
		ref.setOrigin("");
		ref.setSources(new ArrayList<>(List.of("https://source1.com", "https://source2.com")));
		ref.setTags(new ArrayList<>());
		ref.setModified(Instant.now());

		var refDto = new RefDto();
		refDto.setUrl("https://example.com");
		refDto.setOrigin("");
		refDto.setTags(new ArrayList<>());

		when(refTxChannel.send(any())).thenReturn(true);
		when(responseTxChannel.send(any())).thenReturn(true);
		when(cursorTxChannel.send(any())).thenReturn(true);
		when(mapper.domainToDto(ref)).thenReturn(refDto);

		messages.updateRef(ref);

		verify(refTxChannel).send(any(Message.class));
		verify(responseTxChannel, times(1)).send(any(Message.class)); // Should not send for self-reference
		verify(cursorTxChannel).send(any(Message.class));
	}

	@Test
	void testUpdateResponse() {
		var ref = new Ref();
		ref.setUrl("https://example.com");
		ref.setOrigin("");
		ref.setSources(new ArrayList<>(List.of("https://source.com")));
		ref.setModified(Instant.now());

		when(responseTxChannel.send(any())).thenReturn(true);
		when(cursorTxChannel.send(any())).thenReturn(true);

		messages.updateResponse(ref);

		verify(responseTxChannel).send(any(Message.class));
		verify(cursorTxChannel).send(any(Message.class));
	}

	@Test
	void testUpdateSilentRef() {
		var ref = new Ref();
		ref.setUrl("https://example.com");
		ref.setOrigin("");

		var refDto = new RefDto();
		refDto.setUrl("https://example.com");
		refDto.setOrigin("");

		when(refTxChannel.send(any())).thenReturn(true);
		when(mapper.domainToDto(ref)).thenReturn(refDto);

		messages.updateSilentRef(ref);

		verify(refTxChannel).send(any(Message.class));
		verify(cursorTxChannel, never()).send(any(Message.class));
	}

	@Test
	void testUpdateMetadata() {
		var ref = new Ref();
		ref.setUrl("https://example.com");
		ref.setOrigin("");

		var refDto = new RefDto();
		refDto.setUrl("https://example.com");
		refDto.setOrigin("");

		when(refTxChannel.send(any())).thenReturn(true);
		when(mapper.domainToDto(ref)).thenReturn(refDto);

		messages.updateMetadata(ref);

		verify(refTxChannel).send(any(Message.class));
	}

	@Test
	void testDeleteRef() {
		var ref = new Ref();
		ref.setUrl("https://example.com");
		ref.setOrigin("");
		ref.setTags(new ArrayList<>());

		var refDto = new RefDto();
		refDto.setUrl("https://example.com");
		refDto.setOrigin("");
		refDto.setTags(new ArrayList<>());

		when(refTxChannel.send(any())).thenReturn(true);
		when(cursorTxChannel.send(any())).thenReturn(true);
		when(mapper.domainToDto(any(Ref.class))).thenReturn(refDto);

		messages.deleteRef(ref);

		verify(refTxChannel).send(any(Message.class));
	}

	@Test
	void testUpdateExt() {
		var ext = new Ext();
		ext.setTag("test-tag");
		ext.setOrigin("");
		ext.setModified(Instant.now());

		var extDto = new ExtDto();
		extDto.setTag("test-tag");
		extDto.setOrigin("");

		when(extTxChannel.send(any())).thenReturn(true);
		when(cursorTxChannel.send(any())).thenReturn(true);
		when(mapper.domainToDto(ext)).thenReturn(extDto);

		messages.updateExt(ext);

		verify(extTxChannel).send(any(Message.class));
		verify(cursorTxChannel).send(any(Message.class));
	}

	@Test
	void testUpdateUser() {
		var user = new User();
		user.setTag("+user/test");
		user.setOrigin("");
		user.setModified(Instant.now());

		var userDto = new UserDto();
		userDto.setTag("+user/test");
		userDto.setOrigin("");

		when(userTxChannel.send(any())).thenReturn(true);
		when(cursorTxChannel.send(any())).thenReturn(true);
		when(mapper.domainToDto(user)).thenReturn(userDto);

		messages.updateUser(user);

		verify(userTxChannel).send(any(Message.class));
		verify(cursorTxChannel).send(any(Message.class));
	}

	@Test
	void testDeleteUser() {
		when(userTxChannel.send(any())).thenReturn(true);

		messages.deleteUser("+user/test");

		verify(userTxChannel).send(any(Message.class));
	}

	@Test
	void testUpdatePlugin() {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("");
		plugin.setModified(Instant.now());

		var pluginDto = new PluginDto();
		pluginDto.setTag("plugin/test");
		pluginDto.setOrigin("");

		when(pluginTxChannel.send(any())).thenReturn(true);
		when(cursorTxChannel.send(any())).thenReturn(true);
		when(mapper.domainToDto(plugin)).thenReturn(pluginDto);

		messages.updatePlugin(plugin);

		verify(pluginTxChannel).send(any(Message.class));
		verify(cursorTxChannel).send(any(Message.class));
	}

	@Test
	void testDeletePlugin() {
		when(pluginTxChannel.send(any())).thenReturn(true);

		messages.deletePlugin("plugin/test");

		verify(pluginTxChannel).send(any(Message.class));
	}

	@Test
	void testUpdateTemplate() {
		var template = new Template();
		template.setTag("_template/test");
		template.setOrigin("");
		template.setModified(Instant.now());

		var templateDto = new TemplateDto();
		templateDto.setTag("_template/test");
		templateDto.setOrigin("");

		when(templateTxChannel.send(any())).thenReturn(true);
		when(cursorTxChannel.send(any())).thenReturn(true);
		when(mapper.domainToDto(template)).thenReturn(templateDto);

		messages.updateTemplate(template);

		verify(templateTxChannel).send(any(Message.class));
		verify(cursorTxChannel).send(any(Message.class));
	}

	@Test
	void testDeleteTemplate() {
		when(templateTxChannel.send(any())).thenReturn(true);

		messages.deleteTemplate("_template/test");

		verify(templateTxChannel).send(any(Message.class));
	}

	@Test
	void testMessageRetryOnFailure() {
		var ref = new Ref();
		ref.setUrl("https://example.com");
		ref.setOrigin("");
		ref.setTags(new ArrayList<>());

		var refDto = new RefDto();
		refDto.setUrl("https://example.com");
		refDto.setOrigin("");
		refDto.setTags(new ArrayList<>());

		when(refTxChannel.send(any()))
			.thenThrow(new MessageDeliveryException("Failed"))
			.thenReturn(true);
		when(mapper.domainToDto(ref)).thenReturn(refDto);

		messages.updateSilentRef(ref);

		// Should retry via executor
		verify(taskExecutor).execute(any(Runnable.class));
	}

	@Test
	void testNotReadyDoesNotSendMessages() {
		messages.ready = false;

		var ref = new Ref();
		ref.setUrl("https://example.com");
		ref.setOrigin("");

		messages.updateSilentRef(ref);

		// Should not send when not ready
		verify(refTxChannel, never()).send(any());
	}

	@Test
	void testOriginHeaders() {
		var headers = Messages.originHeaders("tenant1.example.com");

		assertThat(headers).isNotNull();
		assertThat(headers.get("origin")).isEqualTo("tenant1.example.com");
	}

	@Test
	void testTagHeaders() {
		var headers = Messages.tagHeaders("tenant1.example.com", "test-tag");

		assertThat(headers).isNotNull();
		assertThat(headers.get("origin")).isEqualTo("tenant1.example.com");
		assertThat(headers.get("tag")).isEqualTo("test-tag");
	}

	@Test
	void testRefHeadersWithUrl() {
		var ref = new Ref();
		ref.setUrl("https://example.com");

		var headers = Messages.refHeaders("", ref);

		assertThat(headers).isNotNull();
		assertThat(headers.get("url")).isEqualTo("https://example.com");
	}

	@Test
	void testResponseHeaders() {
		var headers = Messages.responseHeaders("", "https://source.com");

		assertThat(headers).isNotNull();
		assertThat(headers.get("response")).isEqualTo("https://source.com");
	}
}
