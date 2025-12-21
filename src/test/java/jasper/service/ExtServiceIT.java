package jasper.service;

import com.github.fge.jsonpatch.JsonPatch;
import jasper.IntegrationTest;
import jasper.domain.Ext;
import jasper.domain.User;
import jasper.errors.InvalidPatchException;
import jasper.errors.NotFoundException;
import jasper.repository.ExtRepository;
import jasper.repository.UserRepository;
import jasper.repository.filter.TagFilter;
import jasper.repository.spec.ExtSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WithMockUser("+user/tester")
@IntegrationTest
public class ExtServiceIT {

	@Autowired
	ExtService extService;

	@Autowired
	ExtRepository extRepository;

	@Autowired
	UserRepository userRepository;

	@BeforeEach
	void init() {
		extRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void testCreateExt() {
		var ext = new Ext();
		ext.setTag("custom");
		ext.setName("Custom");

		assertThatThrownBy(() -> extService.create(ext))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testCreateUserExt() {
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("Custom");

		extService.create(ext);

		assertThat(extRepository.existsByQualifiedTag("+user/tester"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("+user/tester").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/tester");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = "MOD")
	void testModCreateExt() {
		var ext = new Ext();
		ext.setTag("custom");
		ext.setName("Custom");

		extService.create(ext);

		assertThat(extRepository.existsByQualifiedTag("custom"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("custom").get();
		assertThat(fetched.getTag())
			.isEqualTo("custom");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	void testReadNonExistentExt() {
		assertThatThrownBy(() -> extService.get("custom"))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void testReadNonExistentPrivateExt() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_secret"));
		userRepository.save(user);

		assertThatThrownBy(() -> extService.get("_secret"))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void testReadPrivateExtDenied() {
		assertThatThrownBy(() -> extService.get("_secret"))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testReadPublicExt() {
		var ext = new Ext();
		ext.setTag("public");
		ext.setName("Custom");
		extRepository.save(ext);

		var fetched = extService.get("public");

		assertThat(fetched.getTag())
			.isEqualTo("public");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	void testReadPublicRemoteExt() {
		var ext = new Ext();
		ext.setOrigin("@remote");
		ext.setTag("public");
		ext.setName("Custom");
		extRepository.save(ext);

		var fetched = extService.get("public@remote");

		assertThat(fetched.getTag())
			.isEqualTo("public");
		assertThat(fetched.getOrigin())
			.isEqualTo("@remote");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	void testReadUserExt() {
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("Custom");
		extRepository.save(ext);

		var fetched = extService.get("+user/tester");

		assertThat(fetched.getTag())
			.isEqualTo("+user/tester");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	void testReadExt() {
		var ext = new Ext();
		ext.setTag("custom");
		ext.setName("Custom");
		extRepository.save(ext);

		var fetched = extService.get("custom");

		assertThat(fetched.getTag())
			.isEqualTo("custom");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	void testReadPrivateExtFailed() {
		var ext = new Ext();
		ext.setTag("_secret");
		ext.setName("Secret");
		extRepository.save(ext);

		assertThatThrownBy(() -> extService.get("_secret"))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testReadPrivateExt() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_secret"));
		userRepository.save(user);
		var ext = new Ext();
		ext.setTag("_secret");
		ext.setName("Secret");
		extRepository.save(ext);

		var fetched = extService.get("_secret");

		assertThat(fetched.getTag())
			.isEqualTo("_secret");
		assertThat(fetched.getName())
			.isEqualTo("Secret");
	}

	@Test
	@WithMockUser("_user/tester")
	void testReadPrivateUserExt() {
		var ext = new Ext();
		ext.setTag("_user/tester");
		ext.setName("Secret");
		extRepository.save(ext);

		var fetched = extService.get("_user/tester");

		assertThat(fetched.getTag())
			.isEqualTo("_user/tester");
		assertThat(fetched.getName())
			.isEqualTo("Secret");
	}

	@Test
	void testReadPrivateUserExtFailed() {
		var ext = new Ext();
		ext.setTag("_user/other");
		ext.setName("Secret");
		extRepository.save(ext);

		assertThatThrownBy(() -> extService.get("_user/other"))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testPagePublicExt() {
		var ext = new Ext();
		ext.setTag("custom");
		ext.setName("Custom");
		extRepository.save(ext);

		var page = extService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().get(0).getTag())
			.isEqualTo("custom");
		assertThat(page.getContent().get(0).getName())
			.isEqualTo("Custom");
	}

	@Test
	void testPagePublicRemoteExt() {
		var ext = new Ext();
		ext.setTag("custom");
		ext.setName("Custom");
		extRepository.save(ext);
		var remote = new Ext();
		remote.setTag("extra");
		remote.setOrigin("@remote");
		remote.setName("Extra");
		extRepository.save(remote);

		var page = extService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(2);
	}


	@Test
	void testPagePrivateExtHidden() {
		var ext = new Ext();
		ext.setTag("_secret");
		ext.setName("Secret");
		extRepository.save(ext);

		var page = extService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	void testPagePrivateExt() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_secret"));
		userRepository.save(user);
		var ext = new Ext();
		ext.setTag("_secret");
		ext.setName("Secret");
		extRepository.save(ext);

		var page = extService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().get(0).getTag())
			.isEqualTo("_secret");
		assertThat(page.getContent().get(0).getName())
			.isEqualTo("Secret");
	}

	@Test
	@WithMockUser("_user/tester")
	void testPagePrivateUserExt() {
		var ext = new Ext();
		ext.setTag("_user/tester");
		ext.setName("Secret");
		extRepository.save(ext);

		var page = extService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().get(0).getTag())
			.isEqualTo("_user/tester");
		assertThat(page.getContent().get(0).getName())
			.isEqualTo("Secret");
	}

	@Test
	@WithMockUser("_user/tester")
	void testPagePrivateUserExtFailed() {
		var ext = new Ext();
		ext.setTag("_user/other");
		ext.setName("Secret");
		extRepository.save(ext);

		var page = extService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	void testPageUserExt() {
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("Secret");
		extRepository.save(ext);

		var page = extService.page(
			TagFilter.builder().build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
		assertThat(page.getContent().get(0).getTag())
			.isEqualTo("+user/tester");
		assertThat(page.getContent().get(0).getName())
			.isEqualTo("Secret");
	}

	@Test
	void testUpdateExt() {
		var user = new User();
		user.setTag("+user/tester");
		user.setWriteAccess(List.of("+custom"));
		userRepository.save(user);
		var ext = new Ext();
		ext.setTag("+custom");
		ext.setName("First");
		extRepository.save(ext);
		var updated = new Ext();
		updated.setTag("+custom");
		updated.setName("Second");
		updated.setModified(ext.getModified());

		extService.update(updated);

		assertThat(extRepository.existsByQualifiedTag("+custom"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("+custom").get();
		assertThat(fetched.getTag())
			.isEqualTo("+custom");
		assertThat(fetched.getName())
			.isEqualTo("Second");
	}

	@Test
	void testUpdateExtFailed() {
		var ext = new Ext();
		ext.setTag("custom");
		ext.setName("First");
		extRepository.save(ext);
		var updated = new Ext();
		updated.setTag("custom");
		updated.setName("Second");
		updated.setModified(ext.getModified());

		assertThatThrownBy(() -> extService.update(updated))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(extRepository.existsByQualifiedTag("custom"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("custom").get();
		assertThat(fetched.getTag())
			.isEqualTo("custom");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testUpdateUserExt() {
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("First");
		extRepository.save(ext);
		var updated = new Ext();
		updated.setTag("+user/tester");
		updated.setName("Second");
		updated.setModified(ext.getModified());

		extService.update(updated);

		assertThat(extRepository.existsByQualifiedTag("+user/tester"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("+user/tester").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/tester");
		assertThat(fetched.getName())
			.isEqualTo("Second");
	}

	@Test
	void testUpdateUserExtFailed() {
		var ext = new Ext();
		ext.setTag("+user/other");
		ext.setName("First");
		extRepository.save(ext);
		var updated = new Ext();
		updated.setTag("+user/other");
		updated.setName("Second");
		updated.setModified(ext.getModified());

		assertThatThrownBy(() -> extService.update(updated))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(extRepository.existsByQualifiedTag("+user/other"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("+user/other").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/other");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testUpdatePrivateExt() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_secret"));
		user.setWriteAccess(List.of("_secret"));
		userRepository.save(user);
		var ext = new Ext();
		ext.setTag("_secret");
		ext.setName("First");
		extRepository.save(ext);
		var updated = new Ext();
		updated.setTag("_secret");
		updated.setName("Second");
		updated.setModified(ext.getModified());

		extService.update(updated);

		assertThat(extRepository.existsByQualifiedTag("_secret"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("_secret").get();
		assertThat(fetched.getTag())
			.isEqualTo("_secret");
		assertThat(fetched.getName())
			.isEqualTo("Second");
	}

	@Test
	void testUpdatePrivateExtFailed() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_secret"));
		userRepository.save(user);
		var ext = new Ext();
		ext.setTag("_secret");
		ext.setName("First");
		extRepository.save(ext);
		var updated = new Ext();
		updated.setTag("_secret");
		updated.setName("Second");
		updated.setModified(ext.getModified());

		assertThatThrownBy(() -> extService.update(updated))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(extRepository.existsByQualifiedTag("_secret"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("_secret").get();
		assertThat(fetched.getTag())
			.isEqualTo("_secret");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testUpdatePublicExtFailed() {
		var ext = new Ext();
		ext.setTag("public");
		ext.setName("First");
		extRepository.save(ext);
		var updated = new Ext();
		updated.setTag("public");
		updated.setName("Second");
		updated.setModified(ext.getModified());

		assertThatThrownBy(() -> extService.update(updated))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(extRepository.existsByQualifiedTag("public"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("public").get();
		assertThat(fetched.getTag())
			.isEqualTo("public");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testDeleteExt() {
		var user = new User();
		user.setTag("+user/tester");
		user.setWriteAccess(List.of("+custom"));
		userRepository.save(user);
		var ext = new Ext();
		ext.setTag("+custom");
		ext.setName("First");
		extRepository.save(ext);

		extService.delete("+custom");

		assertThat(extRepository.existsByQualifiedTag("+custom"))
			.isFalse();
	}

	@Test
	void testDeleteExtFailed() {
		var ext = new Ext();
		ext.setTag("custom");
		ext.setName("First");
		extRepository.save(ext);

		assertThatThrownBy(() -> extService.delete("custom"))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(extRepository.existsByQualifiedTag("custom"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("custom").get();
		assertThat(fetched.getTag())
			.isEqualTo("custom");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testDeletePrivateExt() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_secret"));
		user.setWriteAccess(List.of("_secret"));
		userRepository.save(user);
		var ext = new Ext();
		ext.setTag("_secret");
		ext.setName("First");
		extRepository.save(ext);

		extService.delete("_secret");

		assertThat(extRepository.existsByQualifiedTag("_secret"))
			.isFalse();
	}

	@Test
	void testDeletePrivateExtFailed() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_secret"));
		userRepository.save(user);
		var ext = new Ext();
		ext.setTag("_secret");
		ext.setName("First");
		extRepository.save(ext);

		assertThatThrownBy(() -> extService.delete("_secret"))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(extRepository.existsByQualifiedTag("_secret"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("_secret").get();
		assertThat(fetched.getTag())
			.isEqualTo("_secret");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testApplySortingSpec_WithNoSort() {
		// Create test Ext entities
		var ext1 = new Ext();
		ext1.setTag("+user/test1");
		ext1.setName("Test1");
		extRepository.save(ext1);
		var ext2 = new Ext();
		ext2.setTag("+user/test2");
		ext2.setName("Test2");
		extRepository.save(ext2);

		var spec = ExtSpec.sort(
			TagFilter.builder().build().spec(),
			PageRequest.of(0, 10));

		// Execute query to verify no exceptions
		var result = extRepository.findAll(spec, PageRequest.ofSize(10));
		assertThat(result.getContent()).hasSize(2);
	}

	@Test
	void testApplySortingSpec_WithConfigSort() {
		// Create Ext entities with config->value
		var mapper = JsonMapper.builder().build();
		var ext1 = new Ext();
		ext1.setTag("+user/test1");
		ext1.setName("Test1");
		ext1.setConfig((ObjectNode) mapper.readTree("{\"value\": \"alpha\"}"));
		extRepository.save(ext1);

		var ext2 = new Ext();
		ext2.setTag("+user/test2");
		ext2.setName("Test2");
		ext2.setConfig((ObjectNode) mapper.readTree("{\"value\": \"beta\"}"));
		extRepository.save(ext2);

		var pageable = PageRequest.of(0, 10, Sort.by(
			Sort.Order.desc("config->value")));
		var spec = ExtSpec.sort(
			TagFilter.builder().build().spec(),
			pageable);

		// Execute query to verify sorting works
		var result = extRepository.findAll(spec, PageRequest.ofSize(10));
		assertThat(result.getContent()).hasSize(2);
		// Verify descending order (beta before alpha)
		assertThat(result.getContent().get(0).getTag()).isEqualTo("+user/test2");
		assertThat(result.getContent().get(1).getTag()).isEqualTo("+user/test1");
	}

	@Test
	void testApplySortingSpec_WithNumericSort() {
		// Create Ext entities with numeric config->count
		var mapper = JsonMapper.builder().build();
		var ext1 = new Ext();
		ext1.setTag("+user/test1");
		ext1.setName("Test1");
		ext1.setConfig((ObjectNode) mapper.readTree("{\"count\": 2}"));
		extRepository.save(ext1);

		var ext2 = new Ext();
		ext2.setTag("+user/test2");
		ext2.setName("Test2");
		ext2.setConfig((ObjectNode) mapper.readTree("{\"count\": 10}"));
		extRepository.save(ext2);

		var pageable = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by(
			org.springframework.data.domain.Sort.Order.asc("config->count:num")));
		var spec = ExtSpec.sort(
			TagFilter.builder().build().spec(),
			pageable);

		// Execute query to verify numeric sorting (2 before 10, not string sort where "10" < "2")
		var result = extRepository.findAll(spec, PageRequest.of(0, 10));
		assertThat(result.getContent()).hasSize(2);
		// Verify ascending numeric order (2 before 10)
		assertThat(result.getContent().get(0).getTag()).isEqualTo("+user/test1");
		assertThat(result.getContent().get(1).getTag()).isEqualTo("+user/test2");
	}

	// JSON Patch Jackson 3 Compatibility Tests
	// These tests verify that the json-patch library (which uses Jackson 2) works correctly
	// with Jackson 3 through the conversion pattern in ExtService.patch()

	@Test
	void testJsonPatch_AddOperation() throws Exception {
		// Create an existing Ext
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("Original");
		extRepository.save(ext);
		var cursor = ext.getModified();

		// Create a JSON Patch to add a new field
		var patchJson = "[{\"op\":\"add\",\"path\":\"/name\",\"value\":\"Updated\"}]";
		var jackson2Mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var patch = JsonPatch.fromJson(jackson2Mapper.readTree(patchJson));

		// Apply the patch
		var modified = extService.patch("+user/tester", cursor, patch);

		// Verify the patch was applied
		var fetched = extRepository.findOneByQualifiedTag("+user/tester").get();
		assertThat(fetched.getName()).isEqualTo("Updated");
		assertThat(modified).isAfter(cursor);
	}

	@Test
	void testJsonPatch_ReplaceOperation() throws Exception {
		// Create an existing Ext with config
		var mapper = JsonMapper.builder().build();
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("Original");
		ext.setConfig((ObjectNode) mapper.readTree("{\"key\":\"value1\"}"));
		extRepository.save(ext);
		var cursor = ext.getModified();

		// Create a JSON Patch to replace a config value
		var patchJson = "[{\"op\":\"replace\",\"path\":\"/config/key\",\"value\":\"value2\"}]";
		var jackson2Mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var patch = JsonPatch.fromJson(jackson2Mapper.readTree(patchJson));

		// Apply the patch
		extService.patch("+user/tester", cursor, patch);

		// Verify the patch was applied
		var fetched = extRepository.findOneByQualifiedTag("+user/tester").get();
		assertThat(fetched.getConfig().get("key").asText()).isEqualTo("value2");
	}

	@Test
	void testJsonPatch_RemoveOperation() throws Exception {
		// Create an existing Ext with config field
		var mapper = JsonMapper.builder().build();
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("Test");
		ext.setConfig((ObjectNode) mapper.readTree("{\"toRemove\":\"value\"}"));
		extRepository.save(ext);
		var cursor = ext.getModified();

		// Create a JSON Patch to remove config field
		var patchJson = "[{\"op\":\"remove\",\"path\":\"/config/toRemove\"}]";
		var jackson2Mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var patch = JsonPatch.fromJson(jackson2Mapper.readTree(patchJson));

		// Apply the patch
		extService.patch("+user/tester", cursor, patch);

		// Verify the patch was applied
		var fetched = extRepository.findOneByQualifiedTag("+user/tester").get();
		assertThat(fetched.getConfig().has("toRemove")).isFalse();
	}

	@Test
	void testJsonPatch_CopyOperation() throws Exception {
		// Create an existing Ext with config
		var mapper = JsonMapper.builder().build();
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("Test");
		ext.setConfig((ObjectNode) mapper.readTree("{\"source\":\"value\"}"));
		extRepository.save(ext);
		var cursor = ext.getModified();

		// Create a JSON Patch to copy a value
		var patchJson = "[{\"op\":\"copy\",\"from\":\"/config/source\",\"path\":\"/config/target\"}]";
		var jackson2Mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var patch = JsonPatch.fromJson(jackson2Mapper.readTree(patchJson));

		// Apply the patch
		extService.patch("+user/tester", cursor, patch);

		// Verify the patch was applied
		var fetched = extRepository.findOneByQualifiedTag("+user/tester").get();
		assertThat(fetched.getConfig().get("source").asText()).isEqualTo("value");
		assertThat(fetched.getConfig().get("target").asText()).isEqualTo("value");
	}

	@Test
	void testJsonPatch_MoveOperation() throws Exception {
		// Create an existing Ext with config
		var mapper = JsonMapper.builder().build();
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("Test");
		ext.setConfig((ObjectNode) mapper.readTree("{\"old\":\"value\"}"));
		extRepository.save(ext);
		var cursor = ext.getModified();

		// Create a JSON Patch to move a value
		var patchJson = "[{\"op\":\"move\",\"from\":\"/config/old\",\"path\":\"/config/new\"}]";
		var jackson2Mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var patch = JsonPatch.fromJson(jackson2Mapper.readTree(patchJson));

		// Apply the patch
		extService.patch("+user/tester", cursor, patch);

		// Verify the patch was applied
		var fetched = extRepository.findOneByQualifiedTag("+user/tester").get();
		assertThat(fetched.getConfig().has("old")).isFalse();
		assertThat(fetched.getConfig().get("new").asText()).isEqualTo("value");
	}

	@Test
	void testJsonPatch_TestOperationSuccess() throws Exception {
		// Create an existing Ext
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("Test");
		extRepository.save(ext);
		var cursor = ext.getModified();

		// Create a JSON Patch with test operation that should pass
		var patchJson = "[{\"op\":\"test\",\"path\":\"/name\",\"value\":\"Test\"},{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"Updated\"}]";
		var jackson2Mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var patch = JsonPatch.fromJson(jackson2Mapper.readTree(patchJson));

		// Apply the patch
		extService.patch("+user/tester", cursor, patch);

		// Verify the patch was applied
		var fetched = extRepository.findOneByQualifiedTag("+user/tester").get();
		assertThat(fetched.getName()).isEqualTo("Updated");
	}

	@Test
	void testJsonPatch_TestOperationFailure() throws Exception {
		// Create an existing Ext
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("Test");
		extRepository.save(ext);
		var cursor = ext.getModified();

		// Create a JSON Patch with test operation that should fail
		var patchJson = "[{\"op\":\"test\",\"path\":\"/name\",\"value\":\"Wrong\"},{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"Updated\"}]";
		var jackson2Mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var patch = JsonPatch.fromJson(jackson2Mapper.readTree(patchJson));

		// Apply the patch - should throw exception
		assertThatThrownBy(() -> extService.patch("+user/tester", cursor, patch))
			.isInstanceOf(InvalidPatchException.class);

		// Verify the patch was not applied
		var fetched = extRepository.findOneByQualifiedTag("+user/tester").get();
		assertThat(fetched.getName()).isEqualTo("Test");
	}

	@Test
	void testJsonPatch_NestedObjectPatching() throws Exception {
		// Create an existing Ext with nested config
		var mapper = JsonMapper.builder().build();
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("Test");
		ext.setConfig((ObjectNode) mapper.readTree("{\"nested\":{\"level1\":{\"level2\":\"value\"}}}"));
		extRepository.save(ext);
		var cursor = ext.getModified();

		// Create a JSON Patch to modify deeply nested value
		var patchJson = "[{\"op\":\"replace\",\"path\":\"/config/nested/level1/level2\",\"value\":\"updated\"}]";
		var jackson2Mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var patch = JsonPatch.fromJson(jackson2Mapper.readTree(patchJson));

		// Apply the patch
		extService.patch("+user/tester", cursor, patch);

		// Verify the patch was applied
		var fetched = extRepository.findOneByQualifiedTag("+user/tester").get();
		assertThat(fetched.getConfig().get("nested").get("level1").get("level2").asText()).isEqualTo("updated");
	}

	@Test
	void testJsonPatch_ArrayOperations() throws Exception {
		// Create an existing Ext with array in config
		var mapper = JsonMapper.builder().build();
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("Test");
		ext.setConfig((ObjectNode) mapper.readTree("{\"items\":[\"a\",\"b\",\"c\"]}"));
		extRepository.save(ext);
		var cursor = ext.getModified();

		// Create a JSON Patch to add to array
		var patchJson = "[{\"op\":\"add\",\"path\":\"/config/items/-\",\"value\":\"d\"}]";
		var jackson2Mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var patch = JsonPatch.fromJson(jackson2Mapper.readTree(patchJson));

		// Apply the patch
		extService.patch("+user/tester", cursor, patch);

		// Verify the patch was applied
		var fetched = extRepository.findOneByQualifiedTag("+user/tester").get();
		assertThat(fetched.getConfig().get("items").size()).isEqualTo(4);
		assertThat(fetched.getConfig().get("items").get(3).asText()).isEqualTo("d");
	}

	@Test
	void testJsonPatch_ArrayReplace() throws Exception {
		// Create an existing Ext with array in config
		var mapper = JsonMapper.builder().build();
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("Test");
		ext.setConfig((ObjectNode) mapper.readTree("{\"items\":[\"a\",\"b\",\"c\"]}"));
		extRepository.save(ext);
		var cursor = ext.getModified();

		// Create a JSON Patch to replace array element
		var patchJson = "[{\"op\":\"replace\",\"path\":\"/config/items/1\",\"value\":\"replaced\"}]";
		var jackson2Mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var patch = JsonPatch.fromJson(jackson2Mapper.readTree(patchJson));

		// Apply the patch
		extService.patch("+user/tester", cursor, patch);

		// Verify the patch was applied
		var fetched = extRepository.findOneByQualifiedTag("+user/tester").get();
		assertThat(fetched.getConfig().get("items").get(0).asText()).isEqualTo("a");
		assertThat(fetched.getConfig().get("items").get(1).asText()).isEqualTo("replaced");
		assertThat(fetched.getConfig().get("items").get(2).asText()).isEqualTo("c");
	}

	@Test
	void testJsonPatch_CreateNewExt() throws Exception {
		// Patch a non-existent ext (should create it)
		var patchJson = "[{\"op\":\"add\",\"path\":\"/name\",\"value\":\"Created\"}]";
		var jackson2Mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var patch = JsonPatch.fromJson(jackson2Mapper.readTree(patchJson));

		// Apply the patch to create new ext
		var modified = extService.patch("+user/tester", Instant.now(), patch);

		// Verify the ext was created
		assertThat(extRepository.existsByQualifiedTag("+user/tester")).isTrue();
		var fetched = extRepository.findOneByQualifiedTag("+user/tester").get();
		assertThat(fetched.getName()).isEqualTo("Created");
		assertThat(modified).isNotNull();
	}

	@Test
	void testJsonPatch_ComplexMultipleOperations() throws Exception {
		// Create an existing Ext with complex structure
		var mapper = JsonMapper.builder().build();
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("Original");
		ext.setConfig((ObjectNode) mapper.readTree("{\"keep\":\"this\",\"remove\":\"that\",\"nested\":{\"value\":1},\"oldField\":\"toRemove\"}"));
		extRepository.save(ext);
		var cursor = ext.getModified();

		// Create a complex JSON Patch with multiple operations
		var patchJson = "[" +
			"{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"Updated\"}," +
			"{\"op\":\"remove\",\"path\":\"/config/oldField\"}," +
			"{\"op\":\"remove\",\"path\":\"/config/remove\"}," +
			"{\"op\":\"replace\",\"path\":\"/config/nested/value\",\"value\":2}," +
			"{\"op\":\"add\",\"path\":\"/config/new\",\"value\":\"added\"}" +
			"]";
		var jackson2Mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var patch = JsonPatch.fromJson(jackson2Mapper.readTree(patchJson));

		// Apply the patch
		extService.patch("+user/tester", cursor, patch);

		// Verify all operations were applied correctly
		var fetched = extRepository.findOneByQualifiedTag("+user/tester").get();
		assertThat(fetched.getName()).isEqualTo("Updated");
		assertThat(fetched.getConfig().has("oldField")).isFalse();
		assertThat(fetched.getConfig().get("keep").asText()).isEqualTo("this");
		assertThat(fetched.getConfig().has("remove")).isFalse();
		assertThat(fetched.getConfig().get("nested").get("value").asInt()).isEqualTo(2);
		assertThat(fetched.getConfig().get("new").asText()).isEqualTo("added");
	}

	@Test
	void testJsonPatch_InvalidPatch() throws Exception {
		// Create an existing Ext
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("Test");
		extRepository.save(ext);
		var cursor = ext.getModified();

		// Create an invalid JSON Patch (path doesn't exist)
		var patchJson = "[{\"op\":\"replace\",\"path\":\"/nonexistent\",\"value\":\"value\"}]";
		var jackson2Mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var patch = JsonPatch.fromJson(jackson2Mapper.readTree(patchJson));

		// Apply the patch - should throw exception
		assertThatThrownBy(() -> extService.patch("+user/tester", cursor, patch))
			.isInstanceOf(InvalidPatchException.class);
	}

	@Test
	void testJsonPatch_SpecialCharactersInValues() throws Exception {
		// Create an existing Ext
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("Test");
		extRepository.save(ext);
		var cursor = ext.getModified();

		// Create a JSON Patch with special characters
		var patchJson = "[{\"op\":\"add\",\"path\":\"/name\",\"value\":\"Test with \\\"quotes\\\" and \\\\backslash\"}]";
		var jackson2Mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var patch = JsonPatch.fromJson(jackson2Mapper.readTree(patchJson));

		// Apply the patch
		extService.patch("+user/tester", cursor, patch);

		// Verify the patch was applied with special characters preserved
		var fetched = extRepository.findOneByQualifiedTag("+user/tester").get();
		assertThat(fetched.getName()).isEqualTo("Test with \"quotes\" and \\backslash");
	}

	@Test
	void testJsonPatch_NumericAndBooleanValues() throws Exception {
		// Create an existing Ext with config
		var mapper = JsonMapper.builder().build();
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("Test");
		ext.setConfig((ObjectNode) mapper.readTree("{}"));
		extRepository.save(ext);
		var cursor = ext.getModified();

		// Create a JSON Patch with different value types
		var patchJson = "[" +
			"{\"op\":\"add\",\"path\":\"/config/number\",\"value\":42}," +
			"{\"op\":\"add\",\"path\":\"/config/float\",\"value\":3.14}," +
			"{\"op\":\"add\",\"path\":\"/config/bool\",\"value\":true}," +
			"{\"op\":\"add\",\"path\":\"/config/null\",\"value\":null}" +
			"]";
		var jackson2Mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var patch = JsonPatch.fromJson(jackson2Mapper.readTree(patchJson));

		// Apply the patch
		extService.patch("+user/tester", cursor, patch);

		// Verify the patch was applied with correct types
		var fetched = extRepository.findOneByQualifiedTag("+user/tester").get();
		assertThat(fetched.getConfig().get("number").asInt()).isEqualTo(42);
		assertThat(fetched.getConfig().get("float").asDouble()).isEqualTo(3.14);
		assertThat(fetched.getConfig().get("bool").asBoolean()).isTrue();
		assertThat(fetched.getConfig().get("null").isNull()).isTrue();
	}

}
