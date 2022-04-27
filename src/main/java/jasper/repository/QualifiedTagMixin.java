package jasper.repository;

import java.util.Optional;

import jasper.domain.proj.IsTag;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface QualifiedTagMixin<T extends IsTag> extends JpaSpecificationExecutor<T> {
	Optional<T> findOneByQualifiedTag(String tag);
	void deleteByQualifiedTag(String tag);
	boolean existsByQualifiedTag(String tag);
}
