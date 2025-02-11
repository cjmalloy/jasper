package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jasper.component.dto.ComponentDtoMapper;
import jasper.config.Config.SecurityConfig;
import jasper.config.Config.ServerConfig;
import jasper.config.Props;
import jasper.domain.Plugin;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.errors.AlreadyExistsException;
import jasper.plugin.config.Index;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import jasper.repository.filter.RefFilter;
import jasper.service.dto.RefDto;
import jasper.service.dto.TemplateDto;
import jasper.service.dto.UserDto;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static jasper.domain.proj.HasOrigin.fromParts;
import static jasper.domain.proj.HasOrigin.parts;
import static jasper.domain.proj.HasOrigin.subOrigin;
import static jasper.plugin.Origin.getOrigin;
import static jasper.repository.spec.QualifiedTag.concat;
import static jasper.util.Crypto.keyPair;
import static jasper.util.Crypto.writeRsaPrivatePem;
import static jasper.util.Crypto.writeSshRsa;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
	IngestTemplate ingestTemplate;

	@Autowired
	IngestUser ingestUser;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	ComponentDtoMapper dtoMapper;

	Set<String> configCacheTags = ConcurrentHashMap.newKeySet();

	@PostConstruct
	public void init() {
		if (templateRepository.findByTemplateAndOrigin(concat("_config/server", props.getWorkerOrigin()), props.getLocalOrigin()).isEmpty()) {
			try {
				ingestTemplate.create(config(isBlank(props.getWorkerOrigin()) ? "Server Config" : props.getOrigin() + " Worker Server Config"));
			} catch (AlreadyExistsException e) {
				// Race to init
			}
		}
		if (templateRepository.findByTemplateAndOrigin("_config/index", "").isEmpty()) {
			try {
				ingestTemplate.create(index("DB Indices"));
			} catch (AlreadyExistsException e) {
				// Race to init
			}
		}
		if (userRepository.findOneByQualifiedTag("+user").isEmpty()) {
			try {
				var user = new User();
				user.setTag("+user");
				var kp = keyPair();
				user.setKey(writeRsaPrivatePem(kp.getPrivate()).getBytes());
				user.setPubKey(writeSshRsa(((RSAPublicKey) kp.getPublic()), KeyUtils.getFingerPrint(kp.getPublic())).getBytes());
				ingestUser.create(user);
			} catch (Exception e) {
				// Could not generate host keys
			}
		}
	}

	@CacheEvict(value = "config-cache", allEntries = true)
	public void clearConfigCache() {
		configCacheTags.clear();
		logger.info("Cleared config cache.");
	}

	@CacheEvict(value = {
		"user-cache",
		"user-dto-cache",
		"user-dto-page-cache"
	}, allEntries = true)
	public void clearUserCache() {
		logger.info("Cleared user cache.");
	}

	@CacheEvict(value = {
		"plugin-cache",
		"plugin-config-cache",
		"plugin-metadata-cache",
		"plugin-dto-cache",
		"plugin-dto-page-cache",
	}, allEntries = true)
	public void clearPluginCache() {
		logger.debug("Cleared plugin cache.");
	}

	@CacheEvict(value = {
		"template-cache",
		"template-config-cache",
		"template-cache-wrapped",
		"template-schemas-cache",
		"template-defaults-cache",
		"template-dto-cache",
		"template-dto-page-cache",
	}, allEntries = true)
	public void clearTemplateCache() {
		logger.debug("Cleared template cache.");
	}

	@Cacheable("user-cache")
	public UserDto getUser(String tag) {
		return userRepository.findOneByQualifiedTag(tag)
			.map(dtoMapper::domainToDto)
			.orElse(null);
	}

	@Cacheable(value = "user-cache", key = "'+user'")
	public User user() {
		return userRepository.findOneByQualifiedTag("+user" + props.getLocalOrigin())
			.orElse(null);
	}

	@Cacheable(value = "config-cache", key = "#tag + #origin + '@' + #url")
	public <T> T getConfig(String url, String origin, String tag, Class<T> toValueType) {
		configCacheTags.add(tag);
		return refRepository.findOneByUrlAndOrigin(url, origin)
			.map(r -> r.getPlugin(tag, toValueType))
			.orElse(objectMapper.convertValue(objectMapper.createObjectNode(), toValueType));
	}

	@Cacheable(value = "config-cache", key = "#tag + #origin")
	public <T> List<T> getAllConfigs(String origin, String tag, Class<T> toValueType) {
		configCacheTags.add(tag);
		return refRepository.findAll(
				RefFilter.builder()
					.origin(origin)
					.query(tag).build().spec()).stream()
			.map(r -> r.getPlugin(tag, toValueType))
			.toList();
	}

	@Cacheable(value = "config-cache", key = "'+plugin/origin' + #local")
	public RefDto getRemote(String local) {
		configCacheTags.add("+plugin/origin");
		String origin = "";
		while (isNotBlank(local)) {
			var finalLocal = local;
			var remote = refRepository.findAll(
					RefFilter.builder()
						.origin(origin)
						.query("+plugin/origin").build().spec())
				.stream()
				.filter(r -> finalLocal.equals(getOrigin(r).getLocal()))
				.findFirst()
				.map(dtoMapper::domainToDto)
				.orElse(null);
			if (remote != null) return remote;
			var p = parts(local);
			origin = fromParts(origin, p[0]);
			p[0] = "";
			local = fromParts(p);
		}
		return null;
	}

	public boolean isConfigTag(String tag) {
		return configCacheTags.contains(tag);
	}

	@Cacheable(value = "plugin-config-cache", key = "#tag + #origin")
	public <T> Optional<T> getPluginConfig(String tag, String origin, Class<T> toValueType) {
		return pluginRepository.findByTagAndOrigin(tag, origin)
			.map(Plugin::getConfig)
			.map(n -> objectMapper.convertValue(n, toValueType));
	}

	@Cacheable(value = "plugin-cache", key = "#tag + #origin")
	public Optional<Plugin> getPlugin(String tag, String origin) {
		return pluginRepository.findByTagAndOrigin(tag, origin);
	}

	@Cacheable("plugin-metadata-cache")
	public List<String> getMetadataPlugins(String origin) {
		return pluginRepository.findAllByGenerateMetadataByOrigin(origin);
	}

	@Cacheable(value = "template-config-cache", key = "#template + #origin")
	public <T> Optional<T> getTemplateConfig(String template, String origin, Class<T> toValueType) {
		return templateRepository.findByTemplateAndOrigin(template, origin)
			.map(Template::getConfig)
			.map(n -> objectMapper.convertValue(n, toValueType));
	}

	@Cacheable(value = "template-cache", key = "#template + #origin")
	public Optional<Template> getTemplate(String template, String origin) {
		return templateRepository.findByTemplateAndOrigin(template, origin);
	}

	@Cacheable(value = "template-cache", key = "'_config/server'")
	public ServerConfig root() {
		return getTemplateConfig(concat("_config/server", props.getWorkerOrigin()), props.getLocalOrigin(), ServerConfig.class)
			.or(() -> getTemplateConfig("_config/server", props.getLocalOrigin(), ServerConfig.class))
			.orElse(ServerConfig.builderFor(props.getOrigin()).build())
			.wrap(props);
	}

	@Cacheable(value = "template-cache", key = "'_config/index'")
	public Index index() {
		return getTemplateConfig(concat("_config/index", props.getWorkerOrigin()), props.getLocalOrigin(), Index.class)
			.or(() -> getTemplateConfig("_config/index", props.getLocalOrigin(), Index.class))
			.orElse(Index.builder().build());
	}

	@Cacheable(value = "template-cache-wrapped", key = "'_config/security' + #origin")
	public SecurityConfig security(String origin) {
		return getTemplateConfig("_config/security", origin, SecurityConfig.class)
			.orElse(new SecurityConfig())
			.wrap(props);
	}

	@Cacheable("template-schemas-cache")
	public List<TemplateDto> getSchemas(String tag, String origin) {
		return templateRepository.findAllForTagAndOriginWithSchema(tag, origin)
			.stream()
			.map(dtoMapper::domainToDto)
			.toList();
	}

	@Cacheable("template-defaults-cache")
	public List<TemplateDto> getDefaults(String tag, String origin) {
		return templateRepository.findAllForTagAndOriginWithDefaults(tag, origin)
			.stream()
			.map(dtoMapper::domainToDto)
			.toList();
	}

	private Template config(String name) {
		var config = ServerConfig.builderFor(subOrigin(props.getLocalOrigin(), props.getWorkerOrigin())).build();
		var template = new Template();
		template.setOrigin(props.getLocalOrigin());
		template.setTag(concat("_config/server", props.getWorkerOrigin()));
		template.setName(name);
		template.setConfig(objectMapper.convertValue(config, ObjectNode.class));
		return template;
	}

	private Template index(String name) {
		var config = Index.builder().build();
		var template = new Template();
		template.setOrigin("");
		template.setTag("_config/index");
		template.setName(name);
		template.setConfig(objectMapper.convertValue(config, ObjectNode.class));
		return template;
	}
}
