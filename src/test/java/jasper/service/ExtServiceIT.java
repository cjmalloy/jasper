package jasper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;

import jasper.IntegrationTest;
import jasper.domain.*;
import jasper.repository.*;
import jasper.repository.filter.TagFilter;
import jasper.errors.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

@WithMockUser("tester")
@IntegrationTest
@Transactional
public class ExtServiceIT {

	@Autowired
	ExtService extService;

	@Autowired
	ExtRepository extRepository;

	@Autowired
	TemplateRepository templateRepository;

	@Autowired
	UserRepository userRepository;

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
	@WithMockUser(value = "tester", roles = "MOD")
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
	@WithMockUser(value = "tester", roles = {"USER", "PRIVATE"})
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
	@WithMockUser(value = "tester", roles = {"USER", "PRIVATE"})
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
	@WithMockUser(value = "tester", roles = {"USER", "PRIVATE"})
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
	void testValidateExt() {
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("First");

		extService.validate(ext, false);
	}

	@Test
	void testValidateTagWithInvalidTemplate() throws IOException {
		var template = new Template();
		template.setTag("user");
		var mapper = new ObjectMapper();
		template.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));
		templateRepository.save(template);
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("First");

		assertThatThrownBy(() -> extService.validate(ext, false))
			.isInstanceOf(InvalidTemplateException.class);
	}

	@Test
	void testValidateTagWithTemplate() throws IOException {
		var template = new Template();
		template.setTag("user");
		var mapper = new ObjectMapper();
		template.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));
		templateRepository.save(template);
		var ext = new Ext();
		ext.setTag("+user/tester");
		ext.setName("First");
		ext.setConfig(mapper.readTree("""
		{
			"name": "Alice",
			"age": 100
		}"""));

		extService.validate(ext, false);
	}

	@Test
	void testValidatePrivateExt() {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_slug/custom"));
		user.setWriteAccess(List.of("_slug/custom"));
		userRepository.save(user);
		var ext = new Ext();
		ext.setTag("_slug/custom");
		ext.setName("First");

		extService.validate(ext, false);
	}

	@Test
	void testValidatePrivateTagWithInvalidTemplate() throws IOException {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_slug/custom"));
		user.setWriteAccess(List.of("_slug/custom"));
		userRepository.save(user);
		var template = new Template();
		template.setTag("slug");
		var mapper = new ObjectMapper();
		template.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));
		templateRepository.save(template);
		var ext = new Ext();
		ext.setTag("_slug/custom");
		ext.setName("First");

		assertThatThrownBy(() -> extService.validate(ext, false))
			.isInstanceOf(InvalidTemplateException.class);
	}

	@Test
	void testValidateTagWithMergedTemplate() throws IOException {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("+slug/more/custom"));
		user.setWriteAccess(List.of("+slug/more/custom"));
		userRepository.save(user);
		var mapper = new ObjectMapper();
		var template1 = new Template();
		template1.setTag("slug");
		template1.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));
		templateRepository.save(template1);
		var template2 = new Template();
		template2.setTag("slug/more");
		template2.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"location": { "type": "string" },
				"lat": { "type": "uint32" },
				"lng": { "type": "uint32" }
			}
		}"""));
		templateRepository.save(template2);
		var ext = new Ext();
		ext.setTag("+slug/more/custom");
		ext.setName("First");
		ext.setConfig(mapper.readTree("""
		{
			"name": "Alice",
			"age": 100,
			"location": "Paris",
			"lat": 123,
			"lng": 456
		}"""));

		extService.validate(ext, false);
	}

	@Test
	void testValidateTagWithMergedDefaultsTemplate() throws IOException {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("+slug/more/custom"));
		user.setWriteAccess(List.of("+slug/more/custom"));
		userRepository.save(user);
		var mapper = new ObjectMapper();
		var template1 = new Template();
		template1.setTag("slug");
		template1.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));
		template1.setDefaults(mapper.readTree("""
		{
			"name": "Alice",
			"age": 100
		}"""));
		templateRepository.save(template1);
		var template2 = new Template();
		template2.setTag("slug/more");
		template2.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"location": { "type": "string" },
				"lat": { "type": "uint32" },
				"lng": { "type": "uint32" }
			}
		}"""));
		template2.setDefaults(mapper.readTree("""
		{
			"location": "Paris",
			"lat": 123,
			"lng": 456
		}"""));
		templateRepository.save(template2);
		var ext = new Ext();
		ext.setTag("+slug/more/custom");
		ext.setName("First");

		extService.validate(ext, true);
	}

	@Test
	void testValidatePrivateTagWithTemplate() throws IOException {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_slug/custom"));
		user.setWriteAccess(List.of("_slug/custom"));
		userRepository.save(user);
		var template = new Template();
		template.setTag("slug");
		var mapper = new ObjectMapper();
		template.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));
		templateRepository.save(template);
		var ext = new Ext();
		ext.setTag("_slug/custom");
		ext.setName("First");
		ext.setConfig(mapper.readTree("""
		{
			"name": "Alice",
			"age": 100
		}"""));

		extService.validate(ext, false);
	}

	@Test
	void testValidatePrivateTagWithInvalidPrivateTemplate() throws IOException {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_slug/custom"));
		user.setWriteAccess(List.of("_slug/custom"));
		userRepository.save(user);
		var template = new Template();
		template.setTag("_slug");
		var mapper = new ObjectMapper();
		template.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));
		templateRepository.save(template);
		var ext = new Ext();
		ext.setTag("_slug/custom");
		ext.setName("First");

		assertThatThrownBy(() -> extService.validate(ext, false))
			.isInstanceOf(InvalidTemplateException.class);
	}

	@Test
	void testValidatePrivateTagWithPrivateTemplate() throws IOException {
		var user = new User();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_slug/custom"));
		user.setWriteAccess(List.of("_slug/custom"));
		userRepository.save(user);
		var template = new Template();
		template.setTag("_slug");
		var mapper = new ObjectMapper();
		template.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));
		templateRepository.save(template);
		var ext = new Ext();
		ext.setTag("_slug/custom");
		ext.setName("First");
		ext.setConfig(mapper.readTree("""
		{
			"name": "Alice",
			"age": 100
		}"""));

		extService.validate(ext, false);
	}
}
