package jasper.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import jasper.client.JasperClient;
import jasper.config.Props;
import jasper.domain.Ref;
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

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
	ObjectMapper objectMapper;

	@Timed(value = "jasper.pull", histogram = true)
	public void pull(Ref origin) {
		var pull = objectMapper.convertValue(origin.getPlugins().get("+plugin/origin/pull"), Pull.class);
		pull.setLastPull(Instant.now());
		origin.getPlugins().set("+plugin/origin/pull", objectMapper.convertValue(pull, JsonNode.class));
		refRepository.save(origin);

		Map<String, Object> options = new HashMap<>();
		var config = objectMapper.convertValue(origin.getPlugins().get("+plugin/origin"), Origin.class);
		options.put("size", pull.getBatchSize() == 0 ? props.getMaxReplicateBatch() : Math.min(pull.getBatchSize(), props.getMaxReplicateBatch()));
		options.put("origin", config.getRemote());
		try {
			var url = new URI(isNotBlank(pull.getProxy()) ? pull.getProxy() : origin.getUrl());
			options.put("modifiedAfter", pluginRepository.getCursor(config.getLocal()));
			for (var plugin : client.pluginPull(url, options)) {
				pull.migrate(plugin, config);
				pluginRepository.save(plugin);
			}
			options.put("modifiedAfter", templateRepository.getCursor(config.getLocal()));
			for (var template : client.templatePull(url, options)) {
				pull.migrate(template, config);
				templateRepository.save(template);
			}
			options.put("modifiedAfter", refRepository.getCursor(config.getLocal()));
			for (var ref : client.refPull(url, options)) {
				pull.migrate(ref, config);
				if (pull.isGenerateMetadata()) {
					var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
					meta.update(ref, maybeExisting.orElse(null));
				}
				try {
					if (pull.isValidatePlugins()) {
						validate.ref(ref, pull.getValidationOrigin(), pull.isStripInvalidPlugins());
					}
					refRepository.save(ref);
				} catch (RuntimeException e) {
					logger.warn("Failed Plugin Validation! Skipping replication of ref {} {}: {}", ref.getOrigin(), ref.getTitle(), ref.getUrl());
				}
			}
			options.put("modifiedAfter", extRepository.getCursor(config.getLocal()));
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
			options.put("modifiedAfter", userRepository.getCursor(config.getLocal()));
			for (var user : client.userPull(url, options)) {
				pull.migrate(user, config);
				userRepository.save(user);
			}
		} catch (Exception e) {
			logger.error("Error pulling {} from {}", config.getLocal(), config.getRemote(), e);
		}
	}

	@Timed(value = "jasper.push", histogram = true)
	public void push(Ref origin) {
		var push = objectMapper.convertValue(origin.getPlugins().get("+plugin/origin/push"), Push.class);
		push.setLastPush(Instant.now());
		origin.getPlugins().set("+plugin/origin/push", objectMapper.convertValue(push, JsonNode.class));
		refRepository.save(origin);

		var config = objectMapper.convertValue(origin.getPlugins().get("+plugin/origin"), Origin.class);
		var size = push.getBatchSize() == 0 ? props.getMaxReplicateBatch() : Math.min(push.getBatchSize(), props.getMaxReplicateBatch());
		try {
			var url = new URI(isNotBlank(push.getProxy()) ? push.getProxy() : origin.getUrl());
			Instant modifiedAfter = push.isWriteOnly() ? push.getLastModifiedPluginWritten() : client.pluginCursor(url, config.getRemote());
			var pluginList = pluginRepository.findAll(
					TagFilter.builder()
						.origin(config.getRemote())
						.query(push.getQuery())
						.modifiedAfter(modifiedAfter)
						.build().spec(),
					PageRequest.of(0, size, Sort.Direction.ASC, "modified"))
				.getContent();
			if (!pluginList.isEmpty()) {
				client.pluginPush(url, pluginList);
				push.setLastModifiedPluginWritten(pluginList.get(pluginList.size() - 1).getModified());
			}

			modifiedAfter = push.isWriteOnly() ? push.getLastModifiedTemplateWritten() : client.templateCursor(url, config.getRemote());
			var templateList = templateRepository.findAll(
					TagFilter.builder()
						.origin(config.getRemote())
						.query(push.getQuery())
						.modifiedAfter(modifiedAfter)
						.build().spec(),
					PageRequest.of(0, size, Sort.Direction.ASC, "modified"))
				.getContent();
			if (!templateList.isEmpty()) {
				client.templatePush(url, templateList);
				push.setLastModifiedTemplateWritten(templateList.get(templateList.size() - 1).getModified());
			}

			modifiedAfter = push.isWriteOnly() ? push.getLastModifiedRefWritten() : client.refCursor(url, config.getRemote());
			var refList = refRepository.findAll(
					RefFilter.builder()
						.origin(config.getRemote())
						.query(push.getQuery())
						.modifiedAfter(modifiedAfter)
						.build().spec(),
					PageRequest.of(0, size, Sort.Direction.ASC, "modified"))
				.getContent();
			if (!refList.isEmpty()) {
				client.refPush(url, refList);
				push.setLastModifiedRefWritten(refList.get(refList.size() - 1).getModified());
			}

			modifiedAfter = push.isWriteOnly() ? push.getLastModifiedExtWritten() : client.extCursor(url, config.getRemote());
			var extList = extRepository.findAll(
					TagFilter.builder()
						.origin(config.getRemote())
						.query(push.getQuery())
						.modifiedAfter(modifiedAfter)
						.build().spec(),
					PageRequest.of(0, size, Sort.Direction.ASC, "modified"))
				.getContent();
			if (!extList.isEmpty()) {
				client.extPush(url, extList);
				push.setLastModifiedExtWritten(extList.get(extList.size() - 1).getModified());
			}

			modifiedAfter = push.isWriteOnly() ? push.getLastModifiedUserWritten() : client.userCursor(url, config.getRemote());
			var userList = userRepository.findAll(
					TagFilter.builder()
						.origin(config.getRemote())
						.query(push.getQuery())
						.modifiedAfter(modifiedAfter)
						.build().spec(),
					PageRequest.of(0, size, Sort.Direction.ASC, "modified"))
				.getContent();
			if (!userList.isEmpty()) {
				client.userPush(url, userList);
				push.setLastModifiedUserWritten(userList.get(userList.size() - 1).getModified());
			}
		} catch (Exception e) {
			logger.error("Error pushing {} to origin {}", config.getLocal(), config.getRemote(), e);
		}
	}

}
