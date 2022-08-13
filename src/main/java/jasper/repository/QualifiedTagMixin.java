package jasper.repository;

import jasper.domain.proj.Tag;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface QualifiedTagMixin<T extends Tag> extends JpaSpecificationExecutor<T> {
	Optional<T> findOneByQualifiedTag(String tag);
	void deleteByQualifiedTag(String tag);
	boolean existsByQualifiedTag(String tag);
}
