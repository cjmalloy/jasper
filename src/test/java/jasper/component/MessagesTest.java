package jasper.component;

import jasper.component.dto.ComponentDtoMapper;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.service.dto.RefDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagesTest {

	@InjectMocks
	Messages messages;

	@Mock
	MessageChannel cursorTxChannel;

	@Mock
	MessageChannel refTxChannel;

	@Mock
	ComponentDtoMapper mapper;

	@BeforeEach
	void init() {
		messages.cursorTxChannel = cursorTxChannel;
		messages.refTxChannel = refTxChannel;
		messages.init();
	}

	Ref ref(boolean ignored) {
		var ref = new Ref();
		ref.setUrl("https://www.example.com/");
		ref.setOrigin("@other");
		ref.setModified(Instant.now());
		ref.setMetadata(Metadata.builder().ignored(ignored).build());
		var dto = new RefDto();
		dto.setUrl(ref.getUrl());
		when(mapper.domainToDto(ref)).thenReturn(dto);
		return ref;
	}

	@Test
	void testUpdateRefSendsCursor() {
		var ref = ref(false);

		messages.updateRef(ref);

		var captor = ArgumentCaptor.forClass(Message.class);
		verify(cursorTxChannel).send(captor.capture());
		assertThat(captor.getValue().getPayload()).isEqualTo(ref.getModified());
	}

	@Test
	void testUpdateIgnoredRefSkipsCursor() {
		var ref = ref(true);

		messages.updateRef(ref);

		verify(refTxChannel).send(any());
		verify(cursorTxChannel, never()).send(any());
	}

	@Test
	void testUpdateCursor() {
		var cursor = Instant.now();

		messages.updateCursor("@other", cursor);

		var captor = ArgumentCaptor.forClass(Message.class);
		verify(cursorTxChannel).send(captor.capture());
		assertThat(captor.getValue().getPayload()).isEqualTo(cursor);
		assertThat(captor.getValue().getHeaders().get("origin")).isEqualTo("@other");
	}
}
