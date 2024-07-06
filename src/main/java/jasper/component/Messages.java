package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static jasper.component.Replicator.deletorTag;
import static jasper.domain.Ref.getHierarchicalTags;
import static jasper.domain.proj.HasOrigin.formatOrigin;
import static jasper.domain.proj.HasOrigin.originHierarchy;
import static jasper.domain.proj.HasTags.formatTag;
import static jasper.domain.proj.Tag.localTag;
import static jasper.domain.proj.Tag.tagOrigin;
import static org.springframework.messaging.support.MessageBuilder.createMessage;

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

	@Autowired
	ObjectMapper objectMapper;

	public void updateRef(Ref ref) {
		// TODO: Debounce
		var update = mapper.domainToDto(ref);
		sendAndRetry(() -> refTxChannel.send(createMessage(update, refHeaders(ref.getOrigin(), update))));
		if (update.getTags() != null) {
			for (var tag : update.getTags()) {
				for (var path : getHierarchicalTags(List.of(tag))) {
					sendAndRetry(() -> tagTxChannel.send(createMessage(tag, tagHeaders(ref.getOrigin(), path))));
				}
			}
		}
		if (ref.getSources() != null) {
			for (var source : ref.getSources()) {
				sendAndRetry(() -> responseTxChannel.send(createMessage(ref.getUrl(), responseHeaders(ref.getOrigin(), source))));
			}
		}
	}

	public void updateExt(Ext ext) {
		var update = mapper.domainToDto(ext);
		var origins = originHierarchy(ext.getOrigin());
		for (var o : origins) {
			sendAndRetry(() -> extTxChannel.send(createMessage(update, tagHeaders(o, ext.getTag()))));
		}
	}

	public void updateUser(User user) {
		var update = mapper.domainToDto(user);
		var origins = originHierarchy(user.getOrigin());
		for (var o : origins) {
			sendAndRetry(() -> userTxChannel.send(createMessage(update, tagHeaders(o, user.getTag()))));
		}
	}

	public void deleteUser(String qualifiedTag) {
		String tag = localTag(qualifiedTag);
		String origin = tagOrigin(qualifiedTag);
		var origins = originHierarchy(origin);
		for (var o : origins) {
			sendAndRetry(() -> userTxChannel.send(createMessage(deleteNotice(tag, origin), tagHeaders(o, tag))));
		}
	}

	public void updatePlugin(Plugin plugin) {
		var update = mapper.domainToDto(plugin);
		var origins = originHierarchy(plugin.getOrigin());
		for (var o : origins) {
			sendAndRetry(() -> pluginTxChannel.send(createMessage(update, tagHeaders(o, plugin.getTag()))));
		}
	}

	public void deletePlugin(String qualifiedTag) {
		String tag = localTag(qualifiedTag);
		String origin = tagOrigin(qualifiedTag);
		var origins = originHierarchy(origin);
		for (var o : origins) {
			sendAndRetry(() -> pluginTxChannel.send(createMessage(deleteNotice(tag, origin), tagHeaders(o, tag))));
		}
	}

	public void updateTemplate(Template template) {
		var update = mapper.domainToDto(template);
		var origins = originHierarchy(template.getOrigin());
		for (var o : origins) {
			sendAndRetry(() -> templateTxChannel.send(createMessage(update, tagHeaders(o, template.getTag()))));
		}
	}

	public void deleteTemplate(String qualifiedTag) {
		String tag = localTag(qualifiedTag);
		String origin = tagOrigin(qualifiedTag);
		var origins = originHierarchy(origin);
		for (var o : origins) {
			sendAndRetry(() -> templateTxChannel.send(createMessage(deleteNotice(tag, origin), tagHeaders(o, tag))));
		}
	}

	private ObjectNode deleteNotice(String tag, String origin) {
		var notice = objectMapper.createObjectNode();
		notice.set("tag", TextNode.valueOf(deletorTag(tag)));
		notice.set("origin", TextNode.valueOf(origin));
		return notice;
	}

	private void sendAndRetry(Runnable fn) {
		try {
			fn.run();
		} catch (MessageDeliveryException e) {
			// TODO: give up after 3 tries
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
