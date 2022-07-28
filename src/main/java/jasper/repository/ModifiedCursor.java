package jasper.repository;

import java.time.Instant;

public interface ModifiedCursor {
	Instant getCursor(String origin);
}
