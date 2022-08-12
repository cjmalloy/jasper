package jasper.repository;

import jasper.domain.proj.IsTag;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface QualifiedTagMixin<T extends IsTag> extends JpaSpecificationExecutor<T> {
	Optional<T> findOneByQualifiedTag(String tag);
	void deleteByQualifiedTag(String tag);
	boolean existsByQualifiedTag(String tag);
}
