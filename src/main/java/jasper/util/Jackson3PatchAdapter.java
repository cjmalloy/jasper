package jasper.util;

import com.github.fge.jsonpatch.JsonPatchException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adapter that implements jasper.util.Patch interface to provide Jackson 3 compatibility.
 * 
 * This adapter bridges between Jackson 2 (used by json-patch library) and Jackson 3 (used by the application).
 * It implements our custom Patch interface (jasper.util.Patch) so it can be used in service methods.
 */
public class Jackson3PatchAdapter implements Patch {
	
	private final com.github.fge.jsonpatch.Patch jackson2Patch;
	private final JsonMapper jsonMapper;
	private final com.fasterxml.jackson.databind.ObjectMapper jackson2ObjectMapper;
	
	/**
	 * Creates a new adapter for a Jackson 2 Patch.
	 * 
	 * @param jackson2Patch the Jackson 2 patch to wrap (from json-patch library)
	 * @param jsonMapper Jackson 3 mapper (application)
	 * @param jackson2ObjectMapper Jackson 2 mapper (json-patch library)
	 */
	public Jackson3PatchAdapter(
			com.github.fge.jsonpatch.Patch jackson2Patch,
			JsonMapper jsonMapper,
			com.fasterxml.jackson.databind.ObjectMapper jackson2ObjectMapper) {
		this.jackson2Patch = jackson2Patch;
		this.jsonMapper = jsonMapper;
		this.jackson2ObjectMapper = jackson2ObjectMapper;
	}
	
	/**
	 * Implements jasper.util.Patch.apply() to apply the patch to a Jackson 3 JsonNode.
	 * This method handles the conversion between Jackson 3 (application) and Jackson 2 (json-patch library).
	 * 
	 * @param node Jackson 3 JsonNode to patch
	 * @return patched Jackson 3 JsonNode
	 */
	@Override
	public tools.jackson.databind.JsonNode apply(tools.jackson.databind.JsonNode node) {
		try {
			// 1. Serialize Jackson 3 JsonNode to JSON string
			String json = jsonMapper.writeValueAsString(node);
			
			// 2. Deserialize to Jackson 2 JsonNode
			com.fasterxml.jackson.databind.JsonNode jackson2Node = jackson2ObjectMapper.readTree(json);
			
			// 3. Apply patch using Jackson 2
			com.fasterxml.jackson.databind.JsonNode patchedJackson2 = jackson2Patch.apply(jackson2Node);
			
			// 4. Serialize back to JSON string
			String patchedJson = jackson2ObjectMapper.writeValueAsString(patchedJackson2);
			
			// 5. Deserialize to Jackson 3 JsonNode
			return jsonMapper.readTree(patchedJson);
		} catch (JsonPatchException | JacksonException | com.fasterxml.jackson.core.JsonProcessingException e) {
			throw new RuntimeException("Failed to apply patch", e);
		}
	}
	

	/**
	 * Gets the underlying Jackson 2 Patch.
	 * 
	 * @return the wrapped Jackson 2 patch from json-patch library
	 */
	public com.github.fge.jsonpatch.Patch getJackson2Patch() {
		return jackson2Patch;
	}
}
