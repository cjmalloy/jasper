package jasper.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.domain.proj.HasOrigin;
import jasper.domain.proj.Tag;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;


@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class Pull {
	private String query;
	private Duration pullInterval;
	private Instant lastPull;
	private int batchSize;
	private boolean validatePlugins;
	private boolean stripInvalidPlugins;
	private boolean validateTemplates;
	private boolean stripInvalidTemplates;
	private String validationOrigin;
	private boolean generateMetadata;
	private List<String> addTags;
	private List<String> removeTags;

	// TODO: copy to origin post-processing
	// TODO: conditional tag/origin mapping

	private void migrateEntity(HasOrigin entity, Origin config) {
		entity.setOrigin(config.getLocal());
	}

	private void migrateTag(Tag tag, Origin config) {
		migrateEntity(tag, config);
	}

	public void migrate(Ref ref, Origin config) {
		migrateEntity(ref, config);
		if (isNotBlank(ref.getOrigin()) && ref.getUrl().startsWith("tag:") && !ref.getUrl().endsWith(ref.getOrigin())) {
			ref.setUrl(ref.getUrl() + ref.getOrigin());
		}
		if (ref.getTags() != null && removeTags != null) {
			ref.removePrefixTags();
			migrateTags(ref.getTags(), ref.getPlugins());
		}
		if (addTags != null) {
			ref.addTags(addTags);
		}
		ref.addHierarchicalTags();
	}

	private void migrateTags(List<String> tags, ObjectNode plugins) {
		if (tags == null) return;
		for (int i = tags.size() - 1; i >= 0; i--) {
			var tag = tags.get(i);
			if (removeTags != null && removeTags.contains(tag)) {
				tags.remove(i);
				if (plugins != null) plugins.remove(tag);
			}
		}
	}

	public void migrate(Ext ext, Origin config) {
		migrateTag(ext, config);
	}

	public void migrate(Plugin plugin, Origin config) {
		migrateTag(plugin, config);
	}

	public void migrate(Template template, Origin config) {
		migrateEntity(template, config);
	}

	public void migrate(User user, Origin config) {
		migrateTag(user, config);
		migrateTags(user.getReadAccess(), null);
		migrateTags(user.getWriteAccess(), null);
		migrateTags(user.getTagReadAccess(), null);
		migrateTags(user.getTagWriteAccess(), null);
	}
}
