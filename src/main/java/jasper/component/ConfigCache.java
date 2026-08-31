package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jasper.component.dto.ComponentDtoMapper;
import jasper.config.Config.SecurityConfig;
import jasper.config.Config.ServerConfig;
import jasper.config.Props;
import jasper.domain.External;
import jasper.domain.Plugin;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.domain.proj.HasOrigin;
import jasper.domain.proj.Tag;
import jasper.errors.AlreadyExistsException;
import jasper.plugin.config.Index;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import jasper.repository.filter.RefFilter;
import jasper.service.dto.RefDto;
import jasper.service.dto.TemplateDto;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static jasper.domain.User.merge;
import static jasper.domain.proj.HasOrigin.fromParts;
import static jasper.domain.proj.HasOrigin.parentOrigin;
import static jasper.domain.proj.HasOrigin.parts;
import static jasper.domain.proj.HasOrigin.subOrigin;
import static jasper.plugin.Origin.getOrigin;
import static jasper.repository.spec.QualifiedTag.concat;
import static jasper.util.Crypto.keyPair;
import static jasper.util.Crypto.writeRsaPrivatePem;
import static jasper.util.Crypto.writeSshRsa;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class ConfigCache {
	private static final Logger logger = LoggerFactory.getLogger(ConfigCache.class);

	private static final String[] USER_CACHES = {
		"user-cache",
		"user-dto-cache",
		"user-dto-page-cache",
		"external-user-cache"
	};
	private static final String[] PLUGIN_CACHES = {
		"plugin-cache",
		"plugin-config-cache",
		"plugin-dto-cache",
		"plugin-dto-page-cache"
	};
	private static final String[] TEMPLATE_CACHES = {
		"template-cache",
		"template-config-cache",
		"template-cache-wrapped",
		"template-schemas-cache",
		"template-defaults-cache",
		"template-dto-cache",
		"template-dto-page-cache"
	};

	@Autowired
	Props props;

	@Autowired
	CacheManager cacheManager;

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

	@Autowired
	ConfigCache self;

	Set<String> configCacheTags = ConcurrentHashMap.newKeySet();
	Set<Consumer<ServerConfig>> rootListeners = ConcurrentHashMap.newKeySet();

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
		if (userRepository.findOneByQualifiedTag("+user" + props.getLocalOrigin()).isEmpty()) {
			try {
				var user = new User();
				user.setTag("+user");
				user.setOrigin(props.getLocalOrigin());
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

	public void clearConfigCache(String origin) {
		clearCaches(origin, "config-cache");
		logger.info("{} Cleared config cache.", origin);
	}

	@CacheEvict(value = {
		"user-cache",
		"user-dto-cache",
		"user-dto-page-cache",
		"external-user-cache"
	}, allEntries = true)
	public void clearUserCache() {
		logger.info("Cleared user cache.");
	}

	public void clearUserCache(String origin) {
		clearCaches(origin, USER_CACHES);
		logger.info("{} Cleared user cache.", origin);
	}

	@CacheEvict(value = {
		"plugin-cache",
		"plugin-config-cache",
		"plugin-dto-cache",
		"plugin-dto-page-cache",
	}, allEntries = true)
	public void clearPluginCache() {
		logger.debug("Cleared plugin cache.");
	}

	public void clearPluginCache(String origin) {
		clearCaches(origin, PLUGIN_CACHES);
		logger.debug("{} Cleared plugin cache.", origin);
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

	public void clearTemplateCache(String origin) {
		clearCaches(origin, TEMPLATE_CACHES);
		logger.debug("{} Cleared template cache.", origin);
	}

	public static OriginCacheKey originKey(String origin, Object key) {
		return new OriginCacheKey(origin, key);
	}

	public static OriginCacheKey tagKey(String qualifiedTag) {
		return originKey(Tag.tagOrigin(qualifiedTag), qualifiedTag);
	}

	public OriginCacheKey localOriginKey(Object key) {
		return originKey(props.getLocalOrigin(), key);
	}

	private void clearCaches(String origin, String... cacheNames) {
		for (var cacheName : cacheNames) {
			if (cacheManager.getCache(cacheName) instanceof CaffeineCache cache) {
				cache.getNativeCache().asMap().keySet().removeIf(
					key -> key instanceof OriginCacheKey originKey && originKey.invalidatedBy(origin)
				);
			}
		}
	}

	public record OriginCacheKey(String origin, Object key) {
		public OriginCacheKey {
			origin = HasOrigin.origin(origin);
		}

		boolean invalidatedBy(String origin) {
			origin = HasOrigin.origin(origin);
			if ("@*".equals(this.origin)) return true;
			if (this.origin.endsWith(".*")) {
				var root = this.origin.substring(0, this.origin.length() - 2);
				return HasOrigin.isSubOrigin(origin, root) || HasOrigin.isSubOrigin(root, origin);
			}
			return HasOrigin.isSubOrigin(origin, this.origin);
		}
	}

	@Cacheable(value = "user-cache", key = "T(jasper.component.ConfigCache).tagKey(#qualifiedTag)")
	public User getUser(String qualifiedTag) {
		if (isEmpty(qualifiedTag)) return null;
		return merge(userRepository.findAllByQualifiedSuffix(qualifiedTag.substring(1)))
			.orElse(null);
	}

	@Cacheable(value = "external-user-cache", key = "T(jasper.component.ConfigCache).originKey(#origin, #externalId)")
	public Optional<User> getUserByExternalId(String origin, String externalId) {
		return merge(userRepository.findAllByOriginAndExternalId(origin, externalId));
	}

	public User createUser(String tag, String origin, String externalId) {
		var user = new User();
		user.setTag(tag);
		user.setOrigin(origin);
		user.setExternal(External.builder()
			.ids(List.of(externalId))
			.build());
		ingestUser.create(user);
		return user;
	}

	public void setExternalId(String tag, String origin, String externalId) {
		userRepository.setExternalId(tag, origin, externalId);
	}

	@Cacheable(value = "user-cache", key = "@configCache.localOriginKey('+user')")
	public User user() {
		return userRepository.findOneByQualifiedTag("+user" + props.getLocalOrigin())
			.orElse(null);
	}

	@Cacheable(value = "config-cache", key = "T(jasper.component.ConfigCache).originKey(#origin, #tag + '@' + #url)")
	public <T> T getConfig(String url, String origin, String tag, Class<T> toValueType) {
		configCacheTags.add(tag);
		return refRepository.findOneByUrlAndOrigin(url, origin)
			.map(r -> r.getPlugin(tag, toValueType))
			.orElse(objectMapper.convertValue(objectMapper.createObjectNode(), toValueType));
	}

	@Cacheable(value = "config-cache", key = "T(jasper.component.ConfigCache).originKey(#origin, #tag)")
	public <T> List<T> getAllConfigs(String origin, String tag, Class<T> toValueType) {
		configCacheTags.add(tag);
		return refRepository.findAll(
				RefFilter.builder()
					.origin(origin)
					.query(tag).build().spec()).stream()
			.map(r -> r.getPlugin(tag, toValueType))
			.toList();
	}

	@Cacheable(value = "config-cache", key = "T(jasper.component.ConfigCache).originKey(#local, '+plugin/origin')")
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

	@Cacheable(value = "plugin-config-cache", key = "T(jasper.component.ConfigCache).originKey(#origin, #tag)")
	public <T> Optional<T> getPluginConfig(String tag, String origin, Class<T> toValueType) {
		if (!pluginRepository.existsByQualifiedTag(tag + origin)) return empty();
		return pluginRepository.findByTagAndOrigin(tag, origin)
			.map(Plugin::getConfig)
			.map(n -> objectMapper.convertValue(n, toValueType))
			.or(() -> ofNullable(objectMapper.convertValue(objectMapper.createObjectNode(), toValueType)));
	}

	@Cacheable(value = "plugin-cache", key = "T(jasper.component.ConfigCache).originKey(#origin, #tag)")
	public Optional<Plugin> getPlugin(String tag, String origin) {
		return pluginRepository.findByTagAndOrigin(tag, origin);
	}

	@Cacheable(value = "template-config-cache", key = "T(jasper.component.ConfigCache).originKey(#origin, #template)")
	public <T> Optional<T> getTemplateConfig(String template, String origin, Class<T> toValueType) {
		return templateRepository.findByTemplateAndOrigin(template, origin)
			.map(Template::getConfig)
			.map(n -> objectMapper.convertValue(n, toValueType));
	}

	@Cacheable(value = "template-cache", key = "T(jasper.component.ConfigCache).originKey(#origin, #template)")
	public Optional<Template> getTemplate(String template, String origin) {
		return templateRepository.findByTemplateAndOrigin(template, origin);
	}

	@Cacheable(value = "template-cache", key = "@configCache.localOriginKey('_config/server')")
	public ServerConfig root() {
		return getTemplateConfig(concat("_config/server", props.getWorkerOrigin()), props.getLocalOrigin(), ServerConfig.class)
			.or(() -> getTemplateConfig("_config/server", props.getLocalOrigin(), ServerConfig.class))
			.orElse(ServerConfig.builderFor(props.getOrigin()).build())
			.wrap(props);
	}

	public void rootUpdate(Consumer<ServerConfig> listener) {
		listener.accept(self.root());
		rootListeners.add(listener);
	}

	@ServiceActivator(inputChannel = "templateRxChannel")
	public void handleTemplateUpdate(Message<TemplateDto> message) {
		var template = message.getPayload();
		if (isBlank(template.getTag())) return;
		if (isNotBlank(template.getOrigin())) return;
		if (concat("_config/server", props.getWorkerOrigin()).equals(template.getTag() + template.getOrigin())) {
			logger.debug("Server config template updated, updating listeners");
			rootListeners.forEach(listener -> listener.accept(self.root()));
		}
	}

	@Cacheable(value = "template-cache", key = "T(jasper.component.ConfigCache).originKey('', '_config/index')")
	public Index index() {
		return getTemplateConfig(concat("_config/index", props.getWorkerOrigin()), props.getLocalOrigin(), Index.class)
			.or(() -> getTemplateConfig("_config/index", props.getLocalOrigin(), Index.class))
			.orElse(Index.builder().build());
	}

	@Cacheable(value = "template-cache-wrapped", key = "T(jasper.component.ConfigCache).originKey(#origin, '_config/security')")
	public SecurityConfig security(String origin) {
		do {
			var security = getTemplateConfig("_config/security", origin, SecurityConfig.class);
			if (security.isPresent()) return security.get().wrap(props);
			origin = parentOrigin(origin);
		} while (isNotBlank(origin));
		return new SecurityConfig().wrap(props);
	}

	@Cacheable(value = "template-schemas-cache", key = "T(jasper.component.ConfigCache).originKey(#origin, #tag)")
	public List<TemplateDto> getSchemas(String tag, String origin) {
		return templateRepository.findAllForTagAndOriginWithSchema(tag, origin)
			.stream()
			.map(dtoMapper::domainToDto)
			.toList();
	}

	@Cacheable(value = "template-defaults-cache", key = "T(jasper.component.ConfigCache).originKey(#origin, #tag)")
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
