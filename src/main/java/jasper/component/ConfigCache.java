package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.component.dto.ComponentDtoMapper;
import jasper.domain.Template;
import jasper.config.Config.ServerConfig;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import jasper.service.dto.PluginDto;
import jasper.service.dto.TemplateDto;
import jasper.service.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.List;

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

	@Autowired
	IngestTemplate ingest;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	ComponentDtoMapper dtoMapper;

	@PostConstruct
	public void init() {
		if (templateRepository.findByTemplateAndOrigin("_config/server", "").isEmpty()) {
			ingest.push(config());
		}
	}

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
	public UserDto getUser(String tag) {
		return userRepository.findOneByQualifiedTag(tag)
			.map(dtoMapper::domainToDto)
			.orElse(null);
	}

	@Cacheable(value = "config-cache", key = "#tag + #origin + '@' + #url")
	@Transactional(readOnly = true)
	public <T> T getConfig(String url, String origin, String tag, Class<T> toValueType) {
		return refRepository.findOneByUrlAndOrigin(url, origin)
			.map(r -> r.getPlugin(tag, toValueType))
			.orElse(objectMapper.convertValue(objectMapper.createObjectNode(), toValueType));
	}

	@Cacheable(value = "plugin-cache", key = "#tag + #origin")
	@Transactional(readOnly = true)
	public <T> T getPlugin(String tag, String origin, Class<T> toValueType) {
		return pluginRepository.findByTagAndOrigin(tag, origin)
			.map(r -> r.getConfig(toValueType))
			.orElse(objectMapper.convertValue(objectMapper.createObjectNode(), toValueType));
	}

	@Cacheable("metadata-cache")
	@Transactional(readOnly = true)
	public List<String> getMetadataPlugins(String origin) {
		return pluginRepository.findAllByGenerateMetadataByOrigin(origin);
	}

	@Cacheable("all-plugins-cache")
	@Transactional(readOnly = true)
	public List<PluginDto> getAllPlugins(String origin) {
		return pluginRepository.findAllByOrigin(origin)
			.stream()
			.map(dtoMapper::domainToDto)
			.toList();
	}

	@Cacheable(value = "template-cache", key = "#template + #origin")
	@Transactional(readOnly = true)
	public <T> T getTemplate(String template, String origin, Class<T> toValueType) {
		return templateRepository.findByTemplateAndOrigin(template, origin)
			.map(r -> r.getConfig(toValueType))
			.orElse(objectMapper.convertValue(objectMapper.createObjectNode(), toValueType));
	}

	@Cacheable("schemas-cache")
	@Transactional(readOnly = true)
	public List<TemplateDto> getSchemas(String tag, String origin) {
		return templateRepository.findAllForTagAndOriginWithSchema(tag, origin)
			.stream()
			.map(dtoMapper::domainToDto)
			.toList();
	}

	@Cacheable("all-templates-cache")
	@Transactional(readOnly = true)
	public List<TemplateDto> getAllTemplates(String origin) {
		return templateRepository.findAllByOrigin(origin)
			.stream()
			.map(dtoMapper::domainToDto)
			.toList();
	}

	private Template config() {
		var config = objectMapper.convertValue(objectMapper.createObjectNode(),  ServerConfig.class);
		var template = new Template();
		template.setTag("_config/server");
		template.setName("Server Config");
		template.setConfig(objectMapper.convertValue(config, ObjectNode.class));
		return template;
	}
}
