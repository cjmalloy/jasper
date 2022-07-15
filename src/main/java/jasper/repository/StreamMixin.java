package jasper.repository;

import org.springframework.data.jpa.repository.QueryHints;

import javax.persistence.QueryHint;
import java.util.stream.Stream;

import static org.hibernate.jpa.QueryHints.*;

public interface StreamMixin<T> {

	@QueryHints(value = {
		@QueryHint(name = HINT_FETCH_SIZE, value = "500"),
		@QueryHint(name = HINT_CACHEABLE, value = "false"),
		@QueryHint(name = HINT_READONLY, value = "true"),
		@QueryHint(name = HINT_PASS_DISTINCT_THROUGH, value = "false")
	})
	Stream<T> streamAllByOrderByModifiedDesc();
}
