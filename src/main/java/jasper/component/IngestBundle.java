package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.component.dto.Bundle;
import jasper.errors.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class IngestBundle {
	private static final Logger logger = LoggerFactory.getLogger(IngestBundle.class);

	@Autowired
	Ingest ingestRef;

	@Autowired
	IngestExt ingestExt;

	@Autowired
	IngestUser ingestUser;

	@Autowired
	IngestPlugin ingestPlugin;

	@Autowired
	IngestTemplate ingestTemplate;

	@Async
	@Timed(value = "jasper.bundle", histogram = true)
	public void createOrUpdate(Bundle bundle, String origin) {
		if (bundle.getRef() != null) for (var ref : bundle.getRef()) {
			ref.setOrigin(origin);
			try {
				try {
					ingestRef.update(ref, false);
				} catch (NotFoundException e) {
					ingestRef.create(ref, false);
				}
			} catch (Exception e) {
				logger.error("Error ingesting Ref {}", ref.getUrl(), e);
			}
		}
		if (bundle.getExt() != null) for (var ext : bundle.getExt()) {
			ext.setOrigin(origin);
			try {
				try {
					ingestExt.update(ext, false);
				} catch (NotFoundException e) {
					ingestExt.create(ext, false);
				}
			} catch (Exception e) {
				logger.error("Error ingesting Ext {}", ext.getTag(), e);
			}
		}
		if (bundle.getUser() != null) for (var user : bundle.getUser()) {
			user.setOrigin(origin);
			try {
				try {
					ingestUser.update(user);
				} catch (NotFoundException e) {
					ingestUser.create(user);
				}
			} catch (Exception e) {
				logger.error("Error ingesting User {}", user.getTag(), e);
			}
		}
		if (bundle.getPlugin() != null) for (var plugin : bundle.getPlugin()) {
			plugin.setOrigin(origin);
			try {
				try {
					ingestPlugin.update(plugin);
				} catch (NotFoundException e) {
					ingestPlugin.create(plugin);
				}
			} catch (Exception e) {
				logger.error("Error ingesting Plugin {}", plugin.getTag(), e);
			}
		}
		if (bundle.getTemplate() != null) for (var template : bundle.getTemplate()) {
			template.setOrigin(origin);
			try {
				try {
					ingestTemplate.update(template);
				} catch (NotFoundException e) {
					ingestTemplate.create(template);
				}
			} catch (Exception e) {
				logger.error("Error ingesting Template {}", template.getTag(), e);
			}
		}
	}
}
