package jasper.component;

import jasper.domain.Ref;
import jasper.domain.proj.HasTags;
import jasper.service.dto.DtoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

import static jasper.domain.proj.HasOrigin.originHierarchy;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class Messages {
	private final Logger logger = LoggerFactory.getLogger(Messages.class);

	@Autowired
	private MessageChannel refTxChannel;

	@Autowired
	private MessageChannel tagTxChannel;

	@Autowired
	private MessageChannel responseTxChannel;

	@Autowired
	DtoMapper mapper;

	public void updateRef(Ref ref) {
		// TODO: Debounce
		var update = mapper.domainToDto(ref);
		var origins = originHierarchy(ref.getOrigin());
		for (var o : origins) {
			refTxChannel.send(MessageBuilder.createMessage(update, refHeaders(o, update)));
		}
		if (ref.getTags() != null) {
			ref.addHierarchicalTags();
			for (var tag : ref.getTags()) {
				for (var o : origins) {
					tagTxChannel.send(MessageBuilder.createMessage(tag, tagHeaders(o, tag)));
				}
			}
		}
		if (ref.getSources() != null) {
			for (var source : ref.getSources()) {
				for (var o : origins) {
					responseTxChannel.send(MessageBuilder.createMessage(ref.getUrl(), responseHeaders(o, source)));
				}
			}
		}
	}

	public static MessageHeaders tagHeaders(String origin, String tag) {
		return new MessageHeaders(Map.of(
			"origin", isNotBlank(origin) ? origin : "default",
			"tag", tag
		));
	}

	public static MessageHeaders refHeaders(String origin, HasTags ref) {
		return new MessageHeaders(Map.of(
			"origin", isNotBlank(origin) ? origin : "default",
			"url", ref.getUrl()
		));
	}

	public static MessageHeaders responseHeaders(String origin, String source) {
		return new MessageHeaders(Map.of(
			"origin", isNotBlank(origin) ? origin : "default",
			"response", source
		));
	}
}
