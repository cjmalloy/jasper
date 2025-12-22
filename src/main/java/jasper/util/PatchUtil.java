package jasper.util;

import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.Patch;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Utility class for applying JSON Patch operations across Jackson 2/3 versions.
 * 
 * The json-patch library uses Jackson 2, but the application uses Jackson 3.
 * This utility bridges between the two versions by serializing through JSON strings.
 */
public class PatchUtil {
	
	/**
	 * Applies a JSON Patch to an entity, bridging between Jackson 3 (application) and Jackson 2 (json-patch library).
	 * 
	 * @param patch the JSON Patch to apply
	 * @param entity the entity to patch
	 * @param entityClass the class of the entity
	 * @param jsonMapper Jackson 3 mapper (application)
	 * @param jackson2ObjectMapper Jackson 2 mapper (json-patch library)
	 * @param <T> the entity type
	 * @return the patched entity
	 * @throws JacksonException if serialization/deserialization fails
	 * @throws JsonPatchException if patch application fails
	 * @throws com.fasterxml.jackson.core.JsonProcessingException if Jackson 2 processing fails
	 */
	public static <T> T applyPatch(
			Patch patch,
			T entity,
			Class<T> entityClass,
			JsonMapper jsonMapper,
			com.fasterxml.jackson.databind.ObjectMapper jackson2ObjectMapper
	) throws JacksonException, JsonPatchException, com.fasterxml.jackson.core.JsonProcessingException {
		// 1. Serialize Jackson 3 object to JSON string
		String entityJson = jsonMapper.writeValueAsString(entity);
		
		// 2. Parse with Jackson 2 to get Jackson 2 JsonNode
		com.fasterxml.jackson.databind.JsonNode jackson2Node = jackson2ObjectMapper.readTree(entityJson);
		
		// 3. Apply patch using Jackson 2
		com.fasterxml.jackson.databind.JsonNode patchedJackson2 = patch.apply(jackson2Node);
		
		// 4. Serialize back to JSON string
		String patchedJson = jackson2ObjectMapper.writeValueAsString(patchedJackson2);
		
		// 5. Parse with Jackson 3 and convert back to entity type
		return jsonMapper.readValue(patchedJson, entityClass);
	}
}
