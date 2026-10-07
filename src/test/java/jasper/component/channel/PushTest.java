package jasper.component.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.component.ConfigCache;
import jasper.component.Replicator;
import jasper.component.Tagger;
import jasper.config.Config.ServerConfig;
import jasper.config.JacksonConfiguration;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static jasper.component.Messages.originHeaders;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.messaging.support.MessageBuilder.createMessage;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PushTest {

	@InjectMocks
	Push push;

	@Mock
	Props props;

	@Mock
	TaskScheduler taskScheduler;

	@Mock
	ConfigCache configs;

	@Mock
	RefRepository refRepository;

	@Mock
	Replicator replicator;

	@Mock
	Tagger tagger;

	@Mock
	Watch watch;

	Ref remote;

	@BeforeAll
	static void setUpJackson() {
		ReflectionTestUtils.setField(JacksonConfiguration.class, "om", new ObjectMapper());
	}

	@BeforeEach
	void setUp() {
		remote = Ref.from("https://remote.example.com", "", "+plugin/origin", "+plugin/origin/push", "+plugin/cron");
		remote.setTitle("Remote");
		remote.setPlugin("+plugin/origin", Map.of("local", "@local", "remote", "@target"));
		remote.setPlugin("+plugin/origin/push", Map.of("pushOnChange", true));
		when(configs.root()).thenReturn(ServerConfig.builder().scriptSelectors(List.of("", "@local")).build());
		when(refRepository.findOneByUrlAndOrigin(remote.getUrl(), remote.getOrigin())).thenReturn(Optional.of(remote));
		var ran = new AtomicBoolean();
		when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).then(i -> {
			// Only run the push, not the cooldown check
			if (!ran.getAndSet(true)) i.<Runnable>getArgument(0).run();
			return null;
		});
		ReflectionTestUtils.invokeMethod(push, "watch", remote);
	}

	@Test
	void pushErrorIsLoggedOnRemoteOrigin() {
		doThrow(new RuntimeException("Access Denied")).when(replicator).push(remote);

		push.handleCursorUpdate(createMessage(Instant.now(), originHeaders("@local")));

		verify(replicator).push(remote);
		verify(tagger).attachError(eq(""), eq(remote), eq("Error pushing"), anyString());
		verify(tagger, never()).attachError(anyString(), anyString(), anyString(), anyString());
	}
}
