package jasper.component;

import jasper.component.dto.Bundle;
import jasper.domain.Ref;
import jasper.errors.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static jasper.domain.proj.Tag.matchesTag;

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

	public void attachError(Ref parent, String msg, String origin) {
		attachError(parent, "", msg, origin);
	}

	public void attachError(Ref parent, String title, String logs, String origin) {
		var ref = new Ref();
		ref.setOrigin(origin);
		ref.setUrl("error:" + UUID.randomUUID());
		ref.setSources(List.of(parent.getUrl()));
		ref.setComment(title);
		ref.setComment(logs);
		var tags = new ArrayList<>(List.of("internal", "+plugin/log"));
		if (parent.hasTag("public")) tags.add("public");
		tags.addAll(parent.getTags().stream().filter(t -> matchesTag("+user", t) || matchesTag("_user", t)).toList());
		ref.setTags(tags);
		ingestRef.create(ref, false);
		if (!parent.hasTag("+plugin/error")) {
			parent.addTag("+plugin/error");
			ingestRef.update(parent, false);
		}
	}
}
