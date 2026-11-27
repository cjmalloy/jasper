package jasper.repository;

import jasper.domain.Template;

import java.util.List;

/**
 * Archive-specific Template queries. Templates for a tag, ordered by tag levels.
 * Implementations are selected via @Profile and registered as the "templateRepositoryImpl"
 * bean so Spring Data mixes them into {@link TemplateRepository}.
 */
public interface TemplateRepositoryCustom {
	List<Template> findAllForTagAndOriginWithSchema(String tag, String origin);
	List<Template> findAllForTagAndOriginWithDefaults(String tag, String origin);
}
