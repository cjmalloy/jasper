package jasper.repository;

import java.util.stream.Stream;

public interface StreamMixin<T> {
	Stream<T> streamAllBy();
}
