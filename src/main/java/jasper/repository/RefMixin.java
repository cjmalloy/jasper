package jasper.repository;

import jasper.domain.proj.HasTags;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface RefMixin<T extends HasTags> extends JpaSpecificationExecutor<T> {
	Optional<T> findOneByUrlAndOrigin(String url, String origin);
	void deleteByUrlAndOrigin(String url, String origin);
	boolean existsByUrlAndOrigin(String url, String origin);
	List<T> findAllByUrl(String url);
}
