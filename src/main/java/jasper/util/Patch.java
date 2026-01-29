package jasper.util;

import tools.jackson.databind.JsonNode;

/**
 * Patch interface for Jackson 3 compatibility.
 *
 * This interface is similar to com.github.fge.jsonpatch.Patch but uses Jackson 3's JsonNode
 * instead of Jackson 2's JsonNode, avoiding package conflicts and providing a clean API
 * for patch operations in the application.
 */
public interface Patch {

	/**
	 * Applies this patch to a Jackson 3 JsonNode.
	 *
	 * @param node the node to patch
	 * @return the patched node
	 */
	JsonNode apply(JsonNode node);
}
