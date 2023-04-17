package jasper.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import jasper.client.JasperClient;
import jasper.client.dto.JasperMapper;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.errors.OperationForbiddenOnOriginException;
import jasper.plugin.Origin;
import jasper.plugin.Pull;
import jasper.plugin.Push;
import jasper.repository.ExtRepository;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import jasper.repository.filter.RefFilter;
import jasper.repository.filter.TagFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static jasper.domain.proj.HasOrigin.origin;

@Component
public class Replicator {
	private static final Logger logger = LoggerFactory.getLogger(Replicator.class);

	@Autowired
	Props props;

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

	@Timed(value = "jasper.pull", histogram = true)
	public void pull(Ref remote) {
		if (Arrays.stream(props.getReplicateOrigins()).noneMatch(remote.getOrigin()::equals)) {
			logger.debug("Replicate origins: {}", (Object) props.getReplicateOrigins());
			throw new OperationForbiddenOnOriginException(remote.getOrigin());
		}
		var pull = objectMapper.convertValue(remote.getPlugins().get("+plugin/origin/pull"), Pull.class);
		pull.setLastPull(Instant.now());
		remote.getPlugins().set("+plugin/origin/pull", objectMapper.convertValue(pull, JsonNode.class));
		refRepository.save(remote);

		Map<String, Object> options = new HashMap<>();
		var config = objectMapper.convertValue(remote.getPlugins().get("+plugin/origin"), Origin.class);
		var localOrigin = origin(config.getLocal());
		var remoteOrigin = origin(config.getRemote());
		options.put("size", pull.getBatchSize() == 0 ? props.getMaxReplicateBatch() : Math.min(pull.getBatchSize(), props.getMaxReplicateBatch()));
		options.put("origin", config.getRemote());
		tunnel.proxy(remote, url -> {
			try {
				options.put("modifiedAfter", pluginRepository.getCursor(localOrigin));
				for (var plugin : client.pluginPull(url, options)) {
					pull.migrate(plugin, config);
					pluginRepository.save(plugin);
				}
				options.put("modifiedAfter", templateRepository.getCursor(localOrigin));
				for (var template : client.templatePull(url, options)) {
					pull.migrate(template, config);
					templateRepository.save(template);
				}
				options.put("modifiedAfter", refRepository.getCursor(localOrigin));
				for (var ref : client.refPull(url, options)) {
					pull.migrate(ref, config);
					try {
						if (pull.isValidatePlugins()) {
							validate.ref(ref, pull.getValidationOrigin(), pull.isStripInvalidPlugins());
						}
						var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
						refRepository.save(ref);
						if (pull.isGenerateMetadata()) {
							meta.update(ref, maybeExisting.orElse(null), origin(pull.getValidationOrigin()));
						}
					} catch (RuntimeException e) {
						logger.warn("Failed Plugin Validation! Skipping replication of ref {} {}: {}", ref.getOrigin(), ref.getTitle(), ref.getUrl());
					}
				}
				options.put("modifiedAfter", extRepository.getCursor(localOrigin));
				for (var ext : client.extPull(url, options)) {
					pull.migrate(ext, config);
					try {
						if (pull.isValidateTemplates()) {
							validate.ext(ext, pull.getValidationOrigin(), pull.isStripInvalidTemplates());
						}
						extRepository.save(ext);
					} catch (RuntimeException e) {
						logger.warn("Failed Template Validation! Skipping replication of ext {}: {}", ext.getName(), ext.getQualifiedTag());
					}
				}
				options.put("modifiedAfter", userRepository.getCursor(localOrigin));
				for (var user : client.userPull(url, options)) {
					pull.migrate(user, config);
					userRepository.save(user);
				}
			} catch (Exception e) {
				logger.error("Error pulling {} from {}", localOrigin, remoteOrigin, e);
			}
		});
	}

	@Timed(value = "jasper.push", histogram = true)
	public void push(Ref remote) {
		if (Arrays.stream(props.getReplicateOrigins()).noneMatch(remote.getOrigin()::equals)) {
			logger.debug("Replicate origins: {}", (Object) props.getReplicateOrigins());
			throw new OperationForbiddenOnOriginException(remote.getOrigin());
		}
		var push = objectMapper.convertValue(remote.getPlugins().get("+plugin/origin/push"), Push.class);
		push.setLastPush(Instant.now());
		remote.getPlugins().set("+plugin/origin/push", objectMapper.convertValue(push, JsonNode.class));
		refRepository.save(remote);

		var config = objectMapper.convertValue(remote.getPlugins().get("+plugin/origin"), Origin.class);
		var localOrigin = origin(config.getLocal());
		var remoteOrigin = origin(config.getRemote());
		var size = push.getBatchSize() == 0 ? props.getMaxReplicateBatch() : Math.min(push.getBatchSize(), props.getMaxReplicateBatch());
		if (!push.isCheckRemoteCursor() && allPushed(push, localOrigin))  {
			logger.debug("Skipping push, up to date {}", remoteOrigin);
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
						PageRequest.of(0, size, Sort.Direction.ASC, "modified"))
					.getContent();
				logger.debug("Pushing {} users to {}", pluginList.size(), remoteOrigin);
				if (!pluginList.isEmpty()) {
					client.pluginPush(url, remoteOrigin, pluginList);
					push.setLastModifiedPluginWritten(pluginList.get(pluginList.size() - 1).getModified());
				}

				modifiedAfter = push.isCheckRemoteCursor() ? client.templateCursor(url, remoteOrigin) : push.getLastModifiedTemplateWritten();
				var templateList = templateRepository.findAll(
						TagFilter.builder()
							.origin(localOrigin)
							.query(push.getQuery())
							.modifiedAfter(modifiedAfter)
							.build().spec(),
						PageRequest.of(0, size, Sort.Direction.ASC, "modified"))
					.getContent();
				logger.debug("Pushing {} templates to {}", templateList.size(), remoteOrigin);
				if (!templateList.isEmpty()) {
					client.templatePush(url, remoteOrigin, templateList);
					push.setLastModifiedTemplateWritten(templateList.get(templateList.size() - 1).getModified());
				}

				modifiedAfter = push.isCheckRemoteCursor() ? client.refCursor(url, remoteOrigin) : push.getLastModifiedRefWritten();
				var refList = refRepository.findAll(
						RefFilter.builder()
							.origin(localOrigin)
							.query(push.getQuery())
							.modifiedAfter(modifiedAfter)
							.build().spec(),
						PageRequest.of(0, size, Sort.Direction.ASC, "modified"))
					.map(jasperMapper::domainToDto)
					.getContent();
				logger.debug("Pushing {} refs to {}", refList.size(), remoteOrigin);
				if (!refList.isEmpty()) {
					client.refPush(url, remoteOrigin, refList);
					push.setLastModifiedRefWritten(refList.get(refList.size() - 1).getModified());
				}

				modifiedAfter = push.isCheckRemoteCursor() ? client.extCursor(url, remoteOrigin) : push.getLastModifiedExtWritten();
				var extList = extRepository.findAll(
						TagFilter.builder()
							.origin(localOrigin)
							.query(push.getQuery())
							.modifiedAfter(modifiedAfter)
							.build().spec(),
						PageRequest.of(0, size, Sort.Direction.ASC, "modified"))
					.getContent();
				logger.debug("Pushing {} exts to {}", extList.size(), remoteOrigin);
				if (!extList.isEmpty()) {
					client.extPush(url, remoteOrigin, extList);
					push.setLastModifiedExtWritten(extList.get(extList.size() - 1).getModified());
				}

				modifiedAfter = push.isCheckRemoteCursor() ? client.userCursor(url, remoteOrigin) : push.getLastModifiedUserWritten();
				var userList = userRepository.findAll(
						TagFilter.builder()
							.origin(localOrigin)
							.query(push.getQuery())
							.modifiedAfter(modifiedAfter)
							.build().spec(),
						PageRequest.of(0, size, Sort.Direction.ASC, "modified"))
					.map(jasperMapper::domainToDto)
					.getContent();
				logger.debug("Pushing {} users to {}", userList.size(), remoteOrigin);
				if (!userList.isEmpty()) {
					client.userPush(url, remoteOrigin, userList);
					push.setLastModifiedUserWritten(userList.get(userList.size() - 1).getModified());
				}
			} catch (Exception e) {
				logger.error("Error pushing {} to origin {}", localOrigin, remoteOrigin, e);
			}
		});
		remote.getPlugins().set("+plugin/origin/push", objectMapper.convertValue(push, JsonNode.class));
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

}
