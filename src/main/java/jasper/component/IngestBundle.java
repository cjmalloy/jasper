package jasper.component;

import jasper.component.dto.Bundle;
import jasper.errors.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IngestBundle {

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
				ingestRef.update(ref, false);
			} catch (NotFoundException e) {
				ingestRef.create(ref, false);
			}
		}
		if (bundle.getExt() != null) for (var ext : bundle.getExt()) {
			ext.setOrigin(origin);
			try {
				ingestExt.update(ext, false);
			} catch (NotFoundException e) {
				ingestExt.create(ext, false);
			}
		}
		if (bundle.getUser() != null) for (var user : bundle.getUser()) {
			user.setOrigin(origin);
			try {
				ingestUser.update(user);
			} catch (NotFoundException e) {
				ingestUser.create(user);
			}
		}
		if (bundle.getPlugin() != null) for (var plugin : bundle.getPlugin()) {
			plugin.setOrigin(origin);
			try {
				ingestPlugin.update(plugin);
			} catch (NotFoundException e) {
				ingestPlugin.create(plugin);
			}
		}
		if (bundle.getTemplate() != null) for (var template : bundle.getTemplate()) {
			template.setOrigin(origin);
			try {
				ingestTemplate.update(template);
			} catch (NotFoundException e) {
				ingestTemplate.create(template);
			}
		}
	}
}
