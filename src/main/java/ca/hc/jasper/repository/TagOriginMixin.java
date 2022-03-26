package ca.hc.jasper.repository;

import java.util.Optional;

public interface TagOriginMixin<T> {
	Optional<T> findOneByTagAndOrigin(String tag, String origin);
	void deleteByTagAndOrigin(String tag, String origin);
	boolean existsByTagAndOrigin(String tag, String origin);
}
