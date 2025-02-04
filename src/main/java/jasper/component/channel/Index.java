package jasper.component.channel;

import jasper.component.ConfigCache;
import jasper.repository.RefRepository;
import jasper.service.dto.TemplateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import static jasper.domain.proj.HasOrigin.origin;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Component
public class Index {
	private static final Logger logger = LoggerFactory.getLogger(Index.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	ConfigCache configs;

	@ServiceActivator(inputChannel = "templateRxChannel")
	public void handleTemplateUpdate(Message<TemplateDto> message) {
		if (!configs.root().script("_config/index", "")) return;
		if (isBlank(origin(message.getHeaders().get("origin").toString())) && "_config/index".equals(message.getPayload().getTag())) {
			updateIndex();
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		if (!configs.root().script("_config/index", "")) return;
		updateIndex();
	}

	public void updateIndex() {
		logger.info("Updating DB indices");
		var index = configs.index();
		if (index.isTags()) {
			try {
				refRepository.buildTags();
			} catch (Exception ignored) {}
		} else {
			refRepository.dropTags();
		}
		if (index.isSources()) {
			try {
				refRepository.buildSources();
			} catch (Exception ignored) {}
		} else {
			refRepository.dropSources();
		}
		if (index.isAlts()) {
			try {
				refRepository.buildAlts();
			} catch (Exception ignored) {}
		} else {
			refRepository.dropAlts();
		}
		if (index.isFulltext()) {
			try {
				refRepository.buildFulltext();
			} catch (Exception ignored) {}
		} else {
			refRepository.dropFulltext();
		}
		if (index.isPublished()) {
			try {
				refRepository.buildPublished();
			} catch (Exception ignored) {}
		} else {
			refRepository.dropPublished();
		}
		if (index.isModified()) {
			try {
				refRepository.buildModified();
			} catch (Exception ignored) {}
		} else {
			refRepository.dropModified();
		}
	}

}
