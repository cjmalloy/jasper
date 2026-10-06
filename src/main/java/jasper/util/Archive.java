package jasper.util;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.User;

import java.util.Collection;

import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * In archive mode a blank version is a tombstone: every non-key content field is empty.
 * The key (url or tag, origin) and date fields (modified, published, created) are not considered,
 * since they may still be set on a deleted Ref.
 */
public final class Archive {

	private Archive() {}

	public static boolean isBlank(Ref ref) {
		if (ref.getTags() != null && ref.getTags().contains("plugin/delete")) return true;
		return isEmpty(ref.getTitle())
			&& isEmpty(ref.getComment())
			&& empty(ref.getTags())
			&& empty(ref.getSources())
			&& empty(ref.getAlternateUrls())
			&& empty(ref.getPlugins());
	}

	public static boolean isBlank(Ext ext) {
		return isEmpty(ext.getName())
			&& empty(ext.getConfig());
	}

	public static boolean isBlank(Plugin plugin) {
		return isEmpty(plugin.getName())
			&& empty(plugin.getConfig())
			&& empty(plugin.getSchema())
			&& empty(plugin.getDefaults());
	}

	public static boolean isBlank(Template template) {
		return isEmpty(template.getName())
			&& empty(template.getConfig())
			&& empty(template.getSchema())
			&& empty(template.getDefaults());
	}

	public static boolean isBlank(User user) {
		return isEmpty(user.getName())
			&& isEmpty(user.getRole())
			&& empty(user.getReadAccess())
			&& empty(user.getWriteAccess())
			&& empty(user.getTagReadAccess())
			&& empty(user.getTagWriteAccess())
			&& (user.getKey() == null || user.getKey().length == 0)
			&& (user.getPubKey() == null || user.getPubKey().length == 0)
			&& isEmpty(user.getAuthorizedKeys())
			&& user.getExternal() == null;
	}

	/**
	 * Lock one logical item (url or tag, and origin) until the current transaction ends.
	 * Appending a version never conflicts with the version it replaces, so updates must hold
	 * this lock while checking the cursor and inserting. Postgres only, like the archive profile.
	 */
	public static void lock(EntityManager em, String table, String key, String origin) {
		em.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(:key, 0))")
			.setParameter("key", table + "\n" + origin + "\n" + key)
			.getSingleResult();
	}

	private static boolean empty(Collection<?> c) {
		return c == null || c.isEmpty();
	}

	private static boolean empty(JsonNode node) {
		return node == null || node.isNull() || node.isMissingNode() || node.isContainerNode() && node.isEmpty();
	}
}
