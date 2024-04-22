package jasper.component;

import jasper.component.dto.ComponentDtoMapper;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.domain.proj.HasTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static jasper.domain.Ref.getHierarchicalTags;
import static jasper.domain.proj.HasOrigin.formatOrigin;
import static jasper.domain.proj.HasOrigin.originHierarchy;
import static jasper.domain.proj.HasTags.formatTag;

@Component
public class Messages {
	private final Logger logger = LoggerFactory.getLogger(Messages.class);

	@Qualifier("taskScheduler")
	@Autowired
	TaskExecutor taskExecutor;

	@Autowired
	MessageChannel refTxChannel;

	@Autowired
	MessageChannel tagTxChannel;

	@Autowired
	MessageChannel responseTxChannel;

	@Autowired
	MessageChannel userTxChannel;

	@Autowired
	MessageChannel extTxChannel;

	@Autowired
	MessageChannel pluginTxChannel;

	@Autowired
	MessageChannel templateTxChannel;

	@Autowired
	ComponentDtoMapper mapper;

	public void updateRef(Ref ref) {
		// TODO: Debounce
		var update = mapper.domainToDto(ref);
		sendAndRetry(() -> refTxChannel.send(MessageBuilder.createMessage(update, refHeaders(ref.getOrigin(), update))));
		if (update.getTags() != null) {
			for (var tag : update.getTags()) {
				for (var path : getHierarchicalTags(List.of(tag))) {
					sendAndRetry(() -> tagTxChannel.send(MessageBuilder.createMessage(tag, tagHeaders(ref.getOrigin(), path))));
				}
			}
		}
		if (ref.getSources() != null) {
			for (var source : ref.getSources()) {
				sendAndRetry(() -> responseTxChannel.send(MessageBuilder.createMessage(ref.getUrl(), responseHeaders(ref.getOrigin(), source))));
			}
		}
	}

	public void updateUser(User user) {
		var update = mapper.domainToDto(user);
		var origins = originHierarchy(user.getOrigin());
		for (var o : origins) {
			sendAndRetry(() -> userTxChannel.send(MessageBuilder.createMessage(update, tagHeaders(o, user.getTag()))));
		}
	}

	public void updateExt(Ext ext) {
		var update = mapper.domainToDto(ext);
		var origins = originHierarchy(ext.getOrigin());
		for (var o : origins) {
			sendAndRetry(() -> extTxChannel.send(MessageBuilder.createMessage(update, tagHeaders(o, ext.getTag()))));
		}
	}

	public void updatePlugin(Plugin plugin) {
		var update = mapper.domainToDto(plugin);
		var origins = originHierarchy(plugin.getOrigin());
		for (var o : origins) {
			sendAndRetry(() -> pluginTxChannel.send(MessageBuilder.createMessage(update, tagHeaders(o, plugin.getTag()))));
		}
	}

	public void updateTemplate(Template template) {
		var update = mapper.domainToDto(template);
		var origins = originHierarchy(template.getOrigin());
		for (var o : origins) {
			sendAndRetry(() -> templateTxChannel.send(MessageBuilder.createMessage(update, tagHeaders(o, template.getTag()))));
		}
	}

	private void sendAndRetry(Runnable fn) {
		try {
			fn.run();
		} catch (MessageDeliveryException e) {
			taskExecutor.execute(() -> sendAndRetry(fn));
		}
	}

	public static MessageHeaders originHeaders(String origin) {
		return new MessageHeaders(Map.of(
			"origin", formatOrigin(origin)
		));
	}

	public static MessageHeaders tagHeaders(String origin, String tag) {
		return new MessageHeaders(Map.of(
			"origin", formatOrigin(origin),
			"tag", formatTag(tag)
		));
	}

	public static MessageHeaders refHeaders(String origin, HasTags ref) {
		return new MessageHeaders(Map.of(
			"origin", formatOrigin(origin),
			"url", ref.getUrl()
		));
	}

	public static MessageHeaders responseHeaders(String origin, String source) {
		return new MessageHeaders(Map.of(
			"origin", formatOrigin(origin),
			"response", source
		));
	}
}
