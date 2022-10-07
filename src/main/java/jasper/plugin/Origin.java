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
import java.util.Map;

import static jasper.domain.Plugin.isPlugin;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class Origin {
	private String origin;
	private String remote;
	private String query;
	private String proxy;
	private Instant lastScrape;
	private Duration scrapeInterval;
	private boolean generateMetadata;
	private List<String> removeTags;
	private Map<String, String> mapTags;
	private Map<String, String> mapOrigins;
	private List<String> addTags;

	// TODO: copy to origin post-processing
	// TODO: conditional tag/origin mapping

	private void migrateEntity(HasOrigin entity) {
		entity.setOrigin(origin);
		if (mapOrigins != null && mapOrigins.containsKey(entity.getOrigin())) {
			entity.setOrigin(mapOrigins.get(entity.getOrigin()));
		}
	}

	private void migrateTag(Tag tag) {
		migrateEntity(tag);
		if (mapTags != null && mapTags.containsKey(tag.getTag())) {
			var replacement = mapTags.get(tag.getTag());
			if (isNotBlank(replacement)) {
				tag.setTag(replacement);
			}
		}
	}

	public boolean skip(String tag) {
		return removeTags != null && removeTags.contains(tag);
	}

	public void migrate(Ref ref) {
		migrateEntity(ref);
		if (ref.getTags() != null && (removeTags != null || mapTags != null)) {
			ref.removePrefixTags();
			migrateTags(ref.getTags(), ref.getPlugins());
		}
		if (addTags != null) {
			ref.addTags(addTags);
		}
		ref.addHierarchicalTags();
	}

	private void migrateTags(List<String> tags, ObjectNode plugins) {
		for (int i = tags.size() - 1; i >= 0; i--) {
			var tag = tags.get(i);
			if (removeTags != null && removeTags.contains(tag)) {
				tags.remove(i);
			} else if (mapTags != null && mapTags.containsKey(tag)) {
				var replacement = mapTags.get(tag);
				if (plugins != null && plugins.has(tag)) {
					var plugin = plugins.get(tag);
					plugins.remove(tag);
					if (isPlugin(replacement)) {
						plugins.set(replacement, plugin);
					}
				}
				if (isBlank(replacement) || tags.contains(replacement)) {
					tags.remove(i);
				} else {
					tags.set(i, replacement);
				}
			}
		}
	}

	public void migrate(Ext ext) {
		migrateTag(ext);
	}

	public void migrate(Plugin plugin) {
		migrateTag(plugin);
	}

	public void migrate(Template template) {
		migrateEntity(template);
		if (mapTags != null && mapTags.containsKey(template.getTag())) {
			var replacement = mapTags.get(template.getTag());
			if (replacement != null) {
				template.setTag(replacement);
			}
		}
	}

	public void migrate(User user) {
		migrateTag(user);
		migrateTags(user.getReadAccess(), null);
		migrateTags(user.getWriteAccess(), null);
		migrateTags(user.getTagReadAccess(), null);
		migrateTags(user.getTagWriteAccess(), null);
	}

}
