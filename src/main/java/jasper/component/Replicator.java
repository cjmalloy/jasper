package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import jasper.client.JasperClient;
import jasper.client.dto.JasperMapper;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.errors.OperationForbiddenOnOriginException;
import jasper.plugin.Push;
import jasper.repository.ExtRepository;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import jasper.repository.filter.RefFilter;
import jasper.repository.filter.TagFilter;
import jasper.security.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static jasper.domain.proj.HasOrigin.origin;
import static jasper.domain.proj.HasOrigin.subOrigin;
import static jasper.domain.proj.Tag.localTag;
import static jasper.domain.proj.Tag.tagOrigin;
import static jasper.plugin.Origin.getOrigin;
import static jasper.plugin.Pull.getPull;
import static jasper.plugin.Push.getPush;
import static java.util.Optional.empty;
import static org.springframework.data.domain.Sort.by;

@Component
public class Replicator {
	private static final Logger logger = LoggerFactory.getLogger(Replicator.class);

	@Autowired
	Auth auth;

	@Autowired
	RefRepository refRepository;
	@Autowired
	ExtRepository extRepository;
	@Autowired
	UserRepository userRepository;
	@Autowired
	PluginRepository pluginRepository;
	@Autowired
	TemplateRepository templateRepository;

	@Autowired
	JasperClient client;

	@Autowired
	Meta meta;

	@Autowired
	Validate validate;

	@Autowired
	JasperMapper jasperMapper;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	TunnelClient tunnel;

	@Autowired
	Messages messages;

	@Autowired
	ConfigCache configs;

	@Autowired
	Tagger tagger;

	@Timed(value = "jasper.repl", histogram = true)
	public void pull(Ref remote) {
		var root = configs.root();
		if (!root.getPullOrigins().contains(remote.getOrigin())) throw new OperationForbiddenOnOriginException(remote.getOrigin());
		var pull = getPull(remote);
		var config = getOrigin(remote);
		var localOrigin = subOrigin(remote.getOrigin(), config.getLocal());
		var remoteOrigin = origin(config.getRemote());
		Map<String, Object> options = new HashMap<>();
		options.put("size", pull.getBatchSize() == 0 ? root.getMaxPullEntityBatch() : Math.min(pull.getBatchSize(), root.getMaxPullEntityBatch()));
		options.put("origin", config.getRemote());
		tunnel.proxy(remote, url -> {
			try {
				options.put("modifiedAfter", pluginRepository.getCursor(localOrigin));
				for (var plugin : client.pluginPull(url, options)) {
					plugin.setOrigin(localOrigin);
					pluginRepository.save(plugin);
					if (isDeletorTag(plugin.getTag())) {
						pluginRepository.deleteByQualifiedTag(deletedTag(plugin.getTag()) + plugin.getOrigin());
					}
				}
				options.put("modifiedAfter", templateRepository.getCursor(localOrigin));
				for (var template : client.templatePull(url, options)) {
					template.setOrigin(localOrigin);
					templateRepository.save(template);
					if (isDeletorTag(template.getTag())) {
						pluginRepository.deleteByQualifiedTag(deletedTag(template.getTag()) + template.getOrigin());
					}
				}
				var metadataPlugins = configs.getMetadataPlugins(origin(pull.getValidationOrigin()));
				options.put("modifiedAfter", refRepository.getCursor(localOrigin));
				for (var ref : client.refPull(url, options)) {
					ref.setOrigin(localOrigin);
					pull.migrate(ref, config);
					Optional<Ref> maybeExisting = empty();
					if (pull.isGenerateMetadata()) {
						maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
						meta.ref(ref, metadataPlugins);
					}
					try {
						if (pull.isValidatePlugins()) {
							validate.ref(ref, pull.getValidationOrigin(), pull.isStripInvalidPlugins());
						}
						refRepository.save(ref);
						if (pull.isGenerateMetadata()) {
							meta.sources(ref, maybeExisting.orElse(null), metadataPlugins);
							messages.updateRef(ref);
						}
					} catch (RuntimeException e) {
						logger.warn("{} Failed Plugin Validation! Skipping replication of ref ({}) {}: {}",
							remote.getOrigin(), ref.getOrigin(), ref.getTitle(), ref.getUrl(), e);
						tagger.attachLogs(remote.getOrigin(), remote,
							"Failed Plugin Validation! Skipping replication of ref %s %s: %s".formatted(
								ref.getOrigin(), ref.getTitle(), ref.getUrl()),
							e.getMessage());
					}
				}
				options.put("modifiedAfter", extRepository.getCursor(localOrigin));
				for (var ext : client.extPull(url, options)) {
					ext.setOrigin(localOrigin);
					try {
						if (pull.isValidateTemplates()) {
							validate.ext(ext, pull.getValidationOrigin(), pull.isStripInvalidTemplates());
						}
						extRepository.save(ext);
						if (isDeletorTag(ext.getTag())) {
							pluginRepository.deleteByQualifiedTag(deletedTag(ext.getTag()) + ext.getOrigin());
						}
					} catch (RuntimeException e) {
						logger.warn("{} Failed Template Validation! Skipping replication of ext ({}) {}: {}",
							remote.getOrigin(), ext.getOrigin(), ext.getName(), ext.getQualifiedTag(), e);
						tagger.attachLogs(remote.getOrigin(), remote,
							"Failed Template Validation! Skipping replication of ext %s: %s".formatted(
								ext.getName(), ext.getQualifiedTag()),
							e.getMessage());
					}
				}
				options.put("modifiedAfter", userRepository.getCursor(localOrigin));
				for (var user : client.userPull(url, options)) {
					user.setOrigin(localOrigin);
					pull.migrate(user, config);
					var maybeExisting = userRepository.findOneByQualifiedTag(user.getQualifiedTag());
					if (maybeExisting.isPresent() && user.getKey() == null) {
						user.setKey(maybeExisting.get().getKey());
					}
					userRepository.save(user);
					if (isDeletorTag(user.getTag())) {
						pluginRepository.deleteByQualifiedTag(deletedTag(user.getTag()) + user.getOrigin());
					}
				}
			} catch (Exception e) {
				logger.error("{} Error pulling {} from origin {} {}: {}",
					remote.getOrigin(), localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl(), e);
				tagger.attachError(remote.getOrigin(), remote,
					"Error pulling %s from origin (%s) %s: %s".formatted(
						localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl()),
					e.getMessage());
			}
		});
	}

	@Timed(value = "jasper.repl", histogram = true)
	public void push(Ref remote) {
		var root = configs.root();
		if (!root.getPushOrigins().contains(remote.getOrigin())) throw new OperationForbiddenOnOriginException(remote.getOrigin());
		var push = getPush(remote);
		var config = getOrigin(remote);
		var localOrigin = origin(config.getLocal());
		var remoteOrigin = origin(config.getRemote());
		var size = push.getBatchSize() == 0 ? root.getMaxPushEntityBatch() : Math.min(push.getBatchSize(), root.getMaxPushEntityBatch());
		if (!push.isCheckRemoteCursor() && allPushed(push, localOrigin))  {
			logger.debug("{} Skipping push, up to date {}",
				remote.getOrigin(), remoteOrigin);
			return;
		}
		tunnel.proxy(remote, url -> {
			try {
				Instant modifiedAfter = push.isCheckRemoteCursor() ? client.pluginCursor(url, remoteOrigin) : push.getLastModifiedPluginWritten();
				var pluginList = pluginRepository.findAll(
						TagFilter.builder()
							.origin(localOrigin)
							.query(push.getQuery())
							.modifiedAfter(modifiedAfter)
							.build().spec(),
						PageRequest.of(0, size, by(Ref_.MODIFIED)))
					.getContent();
				logger.debug("{}. Pushing {} plugins to {}",
					remote.getOrigin(), pluginList.size(), remoteOrigin);
				if (!pluginList.isEmpty()) {
					client.pluginPush(url, remoteOrigin, pluginList);
					push.setLastModifiedPluginWritten(pluginList.getLast().getModified());
				}

				modifiedAfter = push.isCheckRemoteCursor() ? client.templateCursor(url, remoteOrigin) : push.getLastModifiedTemplateWritten();
				var templateList = templateRepository.findAll(
						TagFilter.builder()
							.origin(localOrigin)
							.query(push.getQuery())
							.modifiedAfter(modifiedAfter)
							.build().spec(),
						PageRequest.of(0, size, by(Ref_.MODIFIED)))
					.getContent();
				logger.debug("{} Pushing {} templates to {}",
					remote.getOrigin(), templateList.size(), remoteOrigin);
				if (!templateList.isEmpty()) {
					client.templatePush(url, remoteOrigin, templateList);
					push.setLastModifiedTemplateWritten(templateList.getLast().getModified());
				}

				modifiedAfter = push.isCheckRemoteCursor() ? client.refCursor(url, remoteOrigin) : push.getLastModifiedRefWritten();
				var refList = refRepository.findAll(
						RefFilter.builder()
							.origin(localOrigin)
							.query(push.getQuery())
							.modifiedAfter(modifiedAfter)
							.build().spec(),
						PageRequest.of(0, size, by(Ref_.MODIFIED)))
					.map(jasperMapper::domainToDto)
					.getContent();
				logger.debug("{} Pushing {} refs to {}",
					remote.getOrigin(), refList.size(), remoteOrigin);
				if (!refList.isEmpty()) {
					client.refPush(url, remoteOrigin, refList);
					push.setLastModifiedRefWritten(refList.getLast().getModified());
				}

				modifiedAfter = push.isCheckRemoteCursor() ? client.extCursor(url, remoteOrigin) : push.getLastModifiedExtWritten();
				var extList = extRepository.findAll(
						TagFilter.builder()
							.origin(localOrigin)
							.query(push.getQuery())
							.modifiedAfter(modifiedAfter)
							.build().spec(),
						PageRequest.of(0, size, by(Ref_.MODIFIED)))
					.getContent();
				logger.debug("{} Pushing {} exts to {}",
					remote.getOrigin(), extList.size(), remoteOrigin);
				if (!extList.isEmpty()) {
					client.extPush(url, remoteOrigin, extList);
					push.setLastModifiedExtWritten(extList.getLast().getModified());
				}

				modifiedAfter = push.isCheckRemoteCursor() ? client.userCursor(url, remoteOrigin) : push.getLastModifiedUserWritten();
				var userList = userRepository.findAll(
						TagFilter.builder()
							.origin(localOrigin)
							.query(push.getQuery())
							.modifiedAfter(modifiedAfter)
							.build().spec(),
						PageRequest.of(0, size, by(Ref_.MODIFIED)))
					.map(jasperMapper::domainToDto)
					.getContent();
				logger.debug("{} Pushing {} users to {}",
					remote.getOrigin(), userList.size(), remoteOrigin);
				if (!userList.isEmpty()) {
					client.userPush(url, remoteOrigin, userList);
					push.setLastModifiedUserWritten(userList.getLast().getModified());
				}
			} catch (Exception e) {
				logger.error("{} Error pushing {} to origin ({}) {}: {}",
					remote.getOrigin(), localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl(), e);
				tagger.attachError(remote.getOrigin(), remote,
					"Error pushing %s to origin (%s) %s: %s".formatted(
						localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl()),
					e.getMessage());
			}
		});
		remote.setPlugin("+plugin/origin/push", push);
		refRepository.save(remote);
	}

	private boolean allPushed(Push push, String localOrigin) {
		if (refRepository.count(
				RefFilter.builder()
					.origin(localOrigin)
					.query(push.getQuery())
					.modifiedAfter(push.getLastModifiedRefWritten())
					.build().spec()) > 0) {
			return false;
		}
		if (extRepository.count(
				TagFilter.builder()
					.origin(localOrigin)
					.query(push.getQuery())
					.modifiedAfter(push.getLastModifiedExtWritten())
					.build().spec()) > 0) {
			return false;
		}
		if (userRepository.count(
				TagFilter.builder()
					.origin(localOrigin)
					.query(push.getQuery())
					.modifiedAfter(push.getLastModifiedUserWritten())
					.build().spec()) > 0) {
			return false;
		}
		if (pluginRepository.count(
			TagFilter.builder()
				.origin(localOrigin)
				.query(push.getQuery())
				.modifiedAfter(push.getLastModifiedPluginWritten())
				.build().spec()) > 0) {
			return false;
		}
		if (templateRepository.count(
			TagFilter.builder()
				.origin(localOrigin)
				.query(push.getQuery())
				.modifiedAfter(push.getLastModifiedTemplateWritten())
				.build().spec()) > 0) {
			return false;
		}
		return true;
	}

	public static boolean isDeletorTag(String tag) {
		return localTag(tag).endsWith("/deleted");
	}

	public static String deletorTag(String tag) {
		return localTag(tag) + "/deleted" + tagOrigin(tag);
	}

	public static String deletedTag(String deletor) {
		return localTag(deletor).substring("/deleted".length()) + tagOrigin(deletor);
	}

}
