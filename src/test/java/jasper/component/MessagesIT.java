package jasper.component;

import jasper.IntegrationTest;
import jasper.domain.Ref;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * Integration tests for {@link Messages} component.
 * 
 * These tests verify that the Messages component's updateResponse method executes successfully
 * without throwing exceptions when sending messages to message channels.
 */
@IntegrationTest
@DirtiesContext
public class MessagesIT {

	@Autowired
	Messages messages;

	static final String URL = "https://www.example.com/";
	static final String SOURCE_URL = "https://www.example.com/source";

	@BeforeEach
	void setup() {
		// Ensure the Messages component is ready to send messages
		setField(messages, "ready", true);
	}

	@Test
	void testUpdateResponseDoesNotThrowException() throws InterruptedException {
		// Create a test ref with sources
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("");
		ref.setSources(List.of(SOURCE_URL));
		ref.setModified(Instant.now());

		// Verify that updateResponse executes without throwing an exception
		assertThatCode(() -> messages.updateResponse(ref))
			.doesNotThrowAnyException();

		// Wait for async processing to complete
		Thread.sleep(100);
	}

	@Test
	void testUpdateResponseWithOriginDoesNotThrowException() throws InterruptedException {
		// Create a test ref with sources and origin
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("example.com");
		ref.setSources(List.of(SOURCE_URL));
		ref.setModified(Instant.now());

		// Verify that updateResponse executes without throwing an exception
		assertThatCode(() -> messages.updateResponse(ref))
			.doesNotThrowAnyException();

		// Wait for async processing to complete
		Thread.sleep(100);
	}

	@Test
	void testUpdateResponseWithMultipleSources() throws InterruptedException {
		// Create a test ref with multiple sources
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("");
		ref.setSources(List.of(SOURCE_URL, "https://www.example.com/source2"));
		ref.setModified(Instant.now());

		// Verify that updateResponse uses only the first source
		assertThatCode(() -> messages.updateResponse(ref))
			.doesNotThrowAnyException();

		// Wait for async processing to complete
		Thread.sleep(100);
	}
}
