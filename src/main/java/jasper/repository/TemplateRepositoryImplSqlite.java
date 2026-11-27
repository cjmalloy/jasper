package jasper.repository;

import jasper.domain.Template;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Profile("sqlite")
public class TemplateRepositoryImplSqlite implements TemplateRepositoryCustom {

	@Autowired
	TemplateRepository templateRepository;

	@Override
	public List<Template> findAllForTagAndOriginWithSchema(String tag, String origin) {
		return templateRepository.findAllForTagAndOriginWithSchema(tag, origin);
	}

	@Override
	public List<Template> findAllForTagAndOriginWithDefaults(String tag, String origin) {
		return templateRepository.findAllForTagAndOriginWithDefaults(tag, origin);
	}
}
