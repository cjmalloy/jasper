package jasper.repository;

import jasper.domain.Template;

import java.util.List;

/**
 * Archive-specific Template queries.
 * Implementations are selected via @Profile.
 */
public interface TemplateRepositoryCustom {
	/**
	 * @see TemplateRepository#findAllForTagAndOriginWithSchema(String, String)
	 */
	List<Template> findAllForTagAndOriginWithSchema(String tag, String origin);
	/**
	 * @see TemplateRepository#findAllForTagAndOriginWithDefaults(String, String)
	 */
	List<Template> findAllForTagAndOriginWithDefaults(String tag, String origin);
}
