package jasper.component;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplicatorProgressTest {

	static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
	static final Instant END = START.plusSeconds(100);

	Replicator replicator = new Replicator();

	Tagger.Progress progress(boolean enabled) {
		var progress = mock(Tagger.Progress.class);
		when(progress.isEnabled()).thenReturn(enabled);
		return progress;
	}

	@Test
	void testDisabledDoesNotFetchCursor() {
		var progress = progress(false);

		replicator.cursorProgress("", progress, 1, () -> { throw new AssertionError(); }).accept(START);

		verify(progress, never()).update(anyInt(), anyInt());
	}

	@Test
	void testRelativeCursorPosition() {
		var progress = progress(true);
		var onBatch = replicator.cursorProgress("", progress, 2, () -> END);

		onBatch.accept(START);
		onBatch.accept(START.plusSeconds(25));
		onBatch.accept(END);

		verify(progress).update(200, 500);
		verify(progress).update(225, 500);
		verify(progress).update(299, 500);
	}

	@Test
	void testNoStartCursorUsesFirstBatch() {
		var progress = progress(true);
		var onBatch = replicator.cursorProgress("", progress, 0, () -> END);

		onBatch.accept(null);
		onBatch.accept(START);
		onBatch.accept(START.plusSeconds(50));

		verify(progress, times(2)).update(0, 500);
		verify(progress).update(50, 500);
	}

	@Test
	void testMissingEndCursor() {
		var progress = progress(true);
		var onBatch = replicator.cursorProgress("", progress, 3, () -> { throw new RuntimeException(); });

		onBatch.accept(START);

		verify(progress).update(300, 500);
	}
}
