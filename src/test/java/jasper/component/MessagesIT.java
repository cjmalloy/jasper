package jasper.component;

import jasper.IntegrationTest;
import jasper.domain.Ref;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@DirtiesContext
public class MessagesIT {

	@Autowired
	Messages messages;

	@Autowired
	MessageChannel responseTxChannel;

	@Autowired
	MessageChannel cursorTxChannel;

	static final String URL = "https://www.example.com/";
	static final String SOURCE_URL = "https://www.example.com/source";

	@Test
	void testUpdateResponse() throws InterruptedException {
		// Create a test ref with sources
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("");
		ref.setSources(List.of(SOURCE_URL));
		ref.setModified(Instant.now());

		// Set up message capture for responseTxChannel
		TestMessageHandler responseHandler = new TestMessageHandler();
		if (responseTxChannel instanceof org.springframework.integration.channel.AbstractMessageChannel) {
			((org.springframework.integration.channel.AbstractMessageChannel) responseTxChannel)
				.subscribe(responseHandler);
		}

		// Set up message capture for cursorTxChannel
		TestMessageHandler cursorHandler = new TestMessageHandler();
		if (cursorTxChannel instanceof org.springframework.integration.channel.AbstractMessageChannel) {
			((org.springframework.integration.channel.AbstractMessageChannel) cursorTxChannel)
				.subscribe(cursorHandler);
		}

		// Call updateResponse
		messages.updateResponse(ref);

		// Wait for async processing
		Thread.sleep(500);

		// Verify responseTxChannel received the correct message
		assertThat(responseHandler.receivedMessage).isNotNull();
		assertThat(responseHandler.receivedMessage.getPayload()).isEqualTo(URL);
		assertThat(responseHandler.receivedMessage.getHeaders().get("origin")).isEqualTo("");
		assertThat(responseHandler.receivedMessage.getHeaders().get("response")).isEqualTo(SOURCE_URL);

		// Verify cursorTxChannel received the correct message
		assertThat(cursorHandler.receivedMessage).isNotNull();
		assertThat(cursorHandler.receivedMessage.getPayload()).isEqualTo(ref.getModified());
		assertThat(cursorHandler.receivedMessage.getHeaders().get("origin")).isEqualTo("");
	}

	@Test
	void testUpdateResponseWithOrigin() throws InterruptedException {
		// Create a test ref with sources and origin
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("example.com");
		ref.setSources(List.of(SOURCE_URL));
		ref.setModified(Instant.now());

		// Set up message capture for responseTxChannel
		TestMessageHandler responseHandler = new TestMessageHandler();
		if (responseTxChannel instanceof org.springframework.integration.channel.AbstractMessageChannel) {
			((org.springframework.integration.channel.AbstractMessageChannel) responseTxChannel)
				.subscribe(responseHandler);
		}

		// Set up message capture for cursorTxChannel
		TestMessageHandler cursorHandler = new TestMessageHandler();
		if (cursorTxChannel instanceof org.springframework.integration.channel.AbstractMessageChannel) {
			((org.springframework.integration.channel.AbstractMessageChannel) cursorTxChannel)
				.subscribe(cursorHandler);
		}

		// Call updateResponse
		messages.updateResponse(ref);

		// Wait for async processing
		Thread.sleep(500);

		// Verify responseTxChannel received the correct message with proper origin
		assertThat(responseHandler.receivedMessage).isNotNull();
		assertThat(responseHandler.receivedMessage.getPayload()).isEqualTo(URL);
		assertThat(responseHandler.receivedMessage.getHeaders().get("origin")).isEqualTo("@example.com");
		assertThat(responseHandler.receivedMessage.getHeaders().get("response")).isEqualTo(SOURCE_URL);

		// Verify cursorTxChannel received the correct message with proper origin
		assertThat(cursorHandler.receivedMessage).isNotNull();
		assertThat(cursorHandler.receivedMessage.getPayload()).isEqualTo(ref.getModified());
		assertThat(cursorHandler.receivedMessage.getHeaders().get("origin")).isEqualTo("@example.com");
	}

	/**
	 * Simple message handler that captures the last received message for testing.
	 */
	private static class TestMessageHandler implements MessageHandler {
		volatile Message<?> receivedMessage;

		@Override
		public void handleMessage(Message<?> message) {
			this.receivedMessage = message;
		}
	}
}
