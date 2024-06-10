package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.component.dto.ComponentDtoMapper;
import jasper.config.Config.SecurityConfig;
import jasper.config.Config.ServerConfig;
import jasper.config.Props;
import jasper.domain.Plugin;
import jasper.domain.Template;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import jasper.repository.filter.RefFilter;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static jasper.domain.proj.HasTags.pub;

@Component
public class ConfigCache {
	private static final Logger logger = LoggerFactory.getLogger(ConfigCache.class);

	@Autowired
	Props props;

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

	Set<String> configCacheTags = ConcurrentHashMap.newKeySet();

	@PostConstruct
	public void init() {
		if (templateRepository.findByTemplateAndOrigin("_config/server", props.getLocalOrigin()).isEmpty()) {
			ingest.push(config());
		}
	}

	@CacheEvict(value = "config-cache", allEntries = true)
	public void clearConfigCache() {
		configCacheTags.clear();
		logger.info("Cleared config cache.");
	}

	@CacheEvict(value = "user-cache", allEntries = true)
	public void clearUserCache() {
		logger.info("Cleared user cache.");
	}

	@CacheEvict(value = {
		"plugin-cache",
		"plugin-config-cache",
		"plugin-metadata-cache",
		"all-plugins-cache",
	},
		allEntries = true)
	public void clearPluginCache() {
		logger.info("Cleared plugin cache.");
	}

	@CacheEvict(value = {
		"template-cache",
		"template-config-cache",
		"template-cache-wrapped",
		"template-schemas-cache",
		"all-templates-cache",
	},
		allEntries = true)
	public void clearTemplateCache() {
		logger.info("Cleared template cache.");
	}

	@Cacheable("user-cache")
	@Transactional(readOnly = true)
	public UserDto getUser(String userTag) {
		return userRepository.findOneByQualifiedTag(userTag)
			.map(dtoMapper::domainToDto)
			.orElse(null);
	}

	@Cacheable("user-cache")
	@Transactional(readOnly = true)
	public List<UserDto> getUsers(String userTag) {
		return Stream.of(
				userRepository.findOneByQualifiedTag("+" + pub(userTag)).orElse(null),
				userRepository.findOneByQualifiedTag("_" + pub(userTag)).orElse(null))
			.filter(Objects::nonNull)
			.map(dtoMapper::domainToDto)
			.toList();
	}

	@Cacheable(value = "config-cache", key = "#tag + #origin + '@' + #url")
	@Transactional(readOnly = true)
	public <T> T getConfig(String url, String origin, String tag, Class<T> toValueType) {
		configCacheTags.add(tag);
		return refRepository.findOneByUrlAndOrigin(url, origin)
			.map(r -> r.getPlugin(tag, toValueType))
			.orElse(objectMapper.convertValue(objectMapper.createObjectNode(), toValueType));
	}

	@Cacheable(value = "config-cache", key = "#tag + #origin")
	@Transactional(readOnly = true)
	public <T> List<T> getAllConfigs(String origin, String tag, Class<T> toValueType) {
		configCacheTags.add(tag);
		return refRepository.findAll(
				RefFilter.builder()
					.origin(origin)
					.query(tag).build().spec()).stream()
			.map(r -> r.getPlugin(tag, toValueType))
			.toList();
	}

	public boolean isConfigTag(String tag) {
		return configCacheTags.contains(tag);
	}

	@Cacheable(value = "plugin-config-cache", key = "#tag + #origin")
	@Transactional(readOnly = true)
	public <T> T getPluginConfig(String tag, String origin, Class<T> toValueType) {
		return pluginRepository.findByTagAndOrigin(tag, origin)
			.map(Plugin::getConfig)
			.or(() -> Optional.of(objectMapper.createObjectNode()))
			.map(n -> objectMapper.convertValue(n, toValueType))
			.get();
	}

	@Cacheable(value = "plugin-cache", key = "#tag + #origin")
	@Transactional(readOnly = true)
	public Optional<Plugin> getPlugin(String tag, String origin) {
		return pluginRepository.findByTagAndOrigin(tag, origin);
	}

	@Cacheable("plugin-metadata-cache")
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

	@Cacheable(value = "template-config-cache", key = "#template + #origin")
	@Transactional(readOnly = true)
	public <T> T getTemplateConfig(String template, String origin, Class<T> toValueType) {
		return templateRepository.findByTemplateAndOrigin(template, origin)
			.map(Template::getConfig)
			.or(() -> Optional.of(objectMapper.createObjectNode()))
			.map(n -> objectMapper.convertValue(n, toValueType))
			.get();
	}

	@Cacheable(value = "template-cache", key = "#template + #origin")
	@Transactional(readOnly = true)
	public Optional<Template> getTemplate(String template, String origin) {
		return templateRepository.findByTemplateAndOrigin(template, origin);
	}

	@Cacheable(value = "template-cache", key = "'_config/server'")
	@Transactional(readOnly = true)
	public ServerConfig root() {
		return getTemplateConfig("_config/server", props.getLocalOrigin(),  ServerConfig.class);
	}

	@Cacheable(value = "template-cache-wrapped", key = "'_config/security' + #origin")
	@Transactional(readOnly = true)
	public SecurityConfig security(String origin) {
		// TODO: crawl origin hierarchy until found
		return getTemplateConfig("_config/security", origin, SecurityConfig.class)
			.wrap(props);
	}

	@Cacheable("template-schemas-cache")
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
		var config = ServerConfig.builderFor(props.getLocalOrigin()).build();
		var template = new Template();
		template.setOrigin(props.getLocalOrigin());
		template.setTag("_config/server");
		template.setName("Server Config");
		template.setConfig(objectMapper.convertValue(config, ObjectNode.class));
		return template;
	}
}
