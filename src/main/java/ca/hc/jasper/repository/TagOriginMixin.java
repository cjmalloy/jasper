package ca.hc.jasper.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TagOriginMixin<T> extends JpaSpecificationExecutor<T> {
	Optional<T> findOneByTagAndOrigin(String tag, String origin);
	void deleteByTagAndOrigin(String tag, String origin);
	boolean existsByTagAndOrigin(String tag, String origin);
}
