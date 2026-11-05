package jasper.component.cron;

import jasper.component.ConfigCache;
import jasper.component.channel.Watch;
import jasper.config.Config.ServerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CronTest {

	@InjectMocks
	Cron cron;

	@Mock
	ConfigCache configs;

	@Mock
	Watch watch;

	Consumer<ServerConfig> listener;

	@BeforeEach
	void init() {
		doAnswer(i -> {
			listener = i.getArgument(0);
			update("+plugin/cron@tenant.*");
			return null;
		}).when(configs).rootUpdate(any());
		cron.init();
	}

	void update(String ...selectors) {
		var root = ServerConfig.builder().scriptSelectors(List.of(selectors)).build();
		when(configs.root()).thenReturn(root);
		listener.accept(root);
	}

	@Test
	void testCancelsTasksForRemovedOrigins() {
		var kept = mock(ScheduledFuture.class);
		var removed = mock(ScheduledFuture.class);
		cron.tasks.put("@tenant.kept:https://example.com/kept", kept);
		cron.tasks.put("@tenant.removed:https://example.com/removed", removed);

		update("+plugin/cron@tenant.kept");

		verify(kept, never()).cancel(any(Boolean.class));
		verify(removed).cancel(false);
		assertThat(cron.tasks).containsOnlyKeys("@tenant.kept:https://example.com/kept");
	}

	@Test
	void testKeepsTasksSelectedByWildcard() {
		var task = mock(ScheduledFuture.class);
		cron.tasks.put("@tenant.origin:https://example.com", task);

		update("+plugin/cron@tenant.*", "plugin/script/feed@tenant.origin");

		verify(task, never()).cancel(any(Boolean.class));
		assertThat(cron.tasks).containsKey("@tenant.origin:https://example.com");
	}

	@Test
	void testCancelsDefaultOriginTasks() {
		var task = mock(ScheduledFuture.class);
		cron.tasks.put(":https://example.com", task);

		update("+plugin/cron@tenant.*");

		verify(task).cancel(false);
		assertThat(cron.tasks).isEmpty();
	}

	@Test
	void testRemovesWatchForRemovedSelectorsSoReAddingRescans() {
		verify(watch).addWatch(eq("@tenant.*"), eq("+plugin/cron"), any());

		update("+plugin/cron@tenant.origin");

		verify(watch).removeWatch("@tenant.*", "+plugin/cron");
		verify(watch).addWatch(eq("@tenant.origin"), eq("+plugin/cron"), any());

		update("+plugin/cron@tenant.*");

		verify(watch).removeWatch("@tenant.origin", "+plugin/cron");
		verify(watch, times(2)).addWatch(eq("@tenant.*"), eq("+plugin/cron"), any());
	}

	@Test
	void testAddsReplacementWatchBeforeRemovingOld() {
		update("+plugin/cron@tenant.origin");

		var order = inOrder(watch);
		order.verify(watch).addWatch(eq("@tenant.origin"), eq("+plugin/cron"), any());
		order.verify(watch).removeWatch("@tenant.*", "+plugin/cron");
	}

	@Test
	void testStaleCallbackUsesCurrentConfig() {
		update("+plugin/cron@a");
		update("+plugin/cron@b");
		var current = ServerConfig.builder().scriptSelectors(List.of("+plugin/cron@a")).build();
		when(configs.root()).thenReturn(current);
		listener.accept(current);

		// A delayed callback carrying the stale @b config must not remove @a
		listener.accept(ServerConfig.builder().scriptSelectors(List.of("+plugin/cron@b")).build());

		assertThat(cron.cronOrigins).containsExactly("@a");
		verify(watch, times(1)).removeWatch("@a", "+plugin/cron");
	}

	@Test
	void testDoesNotRemoveWatchForUnchangedSelectors() {
		update("+plugin/cron@tenant.*");

		verify(watch, never()).removeWatch(anyString(), anyString());
	}
}
