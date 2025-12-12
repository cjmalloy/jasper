package jasper.repository.spec;

public class JsonSpec {

	/**
	 * Checks if a sort property targets a JSONB field.
	 * JSONB properties include patterns like:
	 * - "metadata->plugins->{tag}" or "metadata->{field}" (Ref)
	 * - "plugins->{tag}->{field}" (Ref)
	 * - "external->{field}" (User)
	 * - "config->{field}" (Ext)
	 *
	 * @param property the sort property name
	 * @return true if this is a JSONB sort property
	 */
	public static boolean isJsonbSortProperty(String property) {
		if (property == null) return false;
		return property.startsWith("metadata->") ||
			property.startsWith("plugins->") ||
			property.startsWith("external->") ||
			property.startsWith("config->");
	}
}
