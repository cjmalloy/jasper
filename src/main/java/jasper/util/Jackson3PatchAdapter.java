package jasper.util;

import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.Patch;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adapter that implements Patch interface to provide Jackson 3 compatibility.
 * 
 * This adapter bridges between Jackson 2 (used by json-patch library) and Jackson 3 (used by the application).
 * It implements the Patch interface so it can be used anywhere a Patch is expected.
 */
public class Jackson3PatchAdapter implements Patch {
	
	private final Patch jackson2Patch;
	private final JsonMapper jsonMapper;
	private final com.fasterxml.jackson.databind.ObjectMapper jackson2ObjectMapper;
	
	/**
	 * Creates a new adapter for a Jackson 2 Patch.
	 * If the patch is already a Jackson3PatchAdapter, reuses its internal patch to avoid double-wrapping.
	 * 
	 * @param jackson2Patch the Jackson 2 patch to wrap
	 * @param jsonMapper Jackson 3 mapper (application)
	 * @param jackson2ObjectMapper Jackson 2 mapper (json-patch library)
	 */
	public Jackson3PatchAdapter(
			Patch jackson2Patch,
			JsonMapper jsonMapper,
			com.fasterxml.jackson.databind.ObjectMapper jackson2ObjectMapper) {
		// If the patch is already an adapter, unwrap it to avoid double-wrapping
		if (jackson2Patch instanceof Jackson3PatchAdapter) {
			Jackson3PatchAdapter existing = (Jackson3PatchAdapter) jackson2Patch;
			this.jackson2Patch = existing.jackson2Patch;
			this.jsonMapper = existing.jsonMapper;
			this.jackson2ObjectMapper = existing.jackson2ObjectMapper;
		} else {
			this.jackson2Patch = jackson2Patch;
			this.jsonMapper = jsonMapper;
			this.jackson2ObjectMapper = jackson2ObjectMapper;
		}
	}
	
	/**
	 * Implements Patch.apply() to delegate to the wrapped patch.
	 * This allows Jackson3PatchAdapter to be used as a Patch.
	 * 
	 * @param node Jackson 2 JsonNode to patch
	 * @return patched Jackson 2 JsonNode
	 * @throws JsonPatchException if patch application fails
	 */
	@Override
	public com.fasterxml.jackson.databind.JsonNode apply(com.fasterxml.jackson.databind.JsonNode node) throws JsonPatchException {
		return jackson2Patch.apply(node);
	}
	
	/**
	 * Applies the patch to an entity, handling the full Jackson 2/3 bridge.
	 * 
	 * @param entity the entity to patch
	 * @param entityClass the class of the entity
	 * @param <T> the entity type
	 * @return the patched entity
	 * @throws JacksonException if serialization/deserialization fails
	 * @throws JsonPatchException if patch application fails
	 * @throws com.fasterxml.jackson.core.JsonProcessingException if Jackson 2 processing fails
	 */
	public <T> T apply(T entity, Class<T> entityClass) 
			throws JacksonException, JsonPatchException, com.fasterxml.jackson.core.JsonProcessingException {
		// 1. Serialize Jackson 3 object to JSON string
		String entityJson = jsonMapper.writeValueAsString(entity);
		
		// 2. Parse with Jackson 2 to get Jackson 2 JsonNode
		com.fasterxml.jackson.databind.JsonNode jackson2Node = jackson2ObjectMapper.readTree(entityJson);
		
		// 3. Apply patch using Jackson 2
		com.fasterxml.jackson.databind.JsonNode patchedJackson2 = jackson2Patch.apply(jackson2Node);
		
		// 4. Serialize back to JSON string
		String patchedJson = jackson2ObjectMapper.writeValueAsString(patchedJackson2);
		
		// 5. Parse with Jackson 3 and convert back to entity type
		return jsonMapper.readValue(patchedJson, entityClass);
	}
	
	
	/**
	 * Gets the underlying Jackson 2 Patch.
	 * 
	 * @return the wrapped Jackson 2 patch
	 */
	public Patch getPatch() {
		return jackson2Patch;
	}
}
