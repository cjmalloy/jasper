package jasper.component;

import jasper.domain.Plugin;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static jasper.config.JacksonConfiguration.om;

@Component
public class ConfigCache {
	private static final Logger logger = LoggerFactory.getLogger(ConfigCache.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	TemplateRepository templateRepository;

	@CacheEvict(value = {
		"config-cache",
		"plugin-cache",
		"metadata-cache",
		"all-plugins-cache",
		"template-cache",
		"schemas-cache",
		"all-templates-cache",
	},
		allEntries = true)
	public void clearConfigCache() {
		logger.info("Cleared config cache.");
	}

	@CacheEvict(value = "user-cache", allEntries = true)
	public void clearUserCache() {
		logger.info("Cleared user cache.");
	}

	@Cacheable("user-cache")
	@Transactional(readOnly = true)
	public User getUser(String tag) {
		return userRepository.findOneByQualifiedTag(tag)
			.orElse(null);
	}

	@Cacheable("config-cache")
	@Transactional(readOnly = true)
	public <T> T getConfig(String url, String origin, String tag, Class<T> toValueType) {
		return refRepository.findOneByUrlAndOrigin(url, origin)
			.map(r -> r.getPlugin(tag, toValueType))
			.orElse(om().convertValue(om().createObjectNode(), toValueType));
	}

	@Cacheable("plugin-cache")
	@Transactional(readOnly = true)
	public <T> T getPlugin(String tag, String origin, Class<T> toValueType) {
		return pluginRepository.findByTagAndOrigin(tag, origin)
			.map(r -> r.getConfig(toValueType))
			.orElse(om().convertValue(om().createObjectNode(), toValueType));
	}

	@Cacheable("metadata-cache")
	@Transactional(readOnly = true)
	public List<String> getMetadataPlugins(String origin) {
		return pluginRepository.findAllByGenerateMetadataByOrigin(origin);
	}

	@Cacheable("all-plugins-cache")
	@Transactional(readOnly = true)
	public List<Plugin> getAllPlugins(String origin) {
		return pluginRepository.findAllByOrigin(origin);
	}

	@Cacheable("template-cache")
	@Transactional(readOnly = true)
	public <T> T getTemplate(String template, String origin, Class<T> toValueType) {
		return templateRepository.findByTemplateAndOrigin(template, origin)
			.map(r -> r.getConfig(toValueType))
			.orElse(om().convertValue(om().createObjectNode(), toValueType));
	}

	@Cacheable("schemas-cache")
	@Transactional(readOnly = true)
	public List<Template> getSchemas(String tag, String origin) {
		return templateRepository.findAllForTagAndOriginWithSchema(tag, origin);
	}

	@Cacheable("all-templates-cache")
	@Transactional(readOnly = true)
	public List<Template> getAllTemplates(String origin) {
		return templateRepository.findAllByOrigin(origin);
	}
}
