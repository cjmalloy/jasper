package jasper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.MultiTenantIntegrationTest;
import jasper.config.Props;
import jasper.domain.Ext;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.errors.InvalidTemplateException;
import jasper.errors.NotFoundException;
import jasper.repository.ExtRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import jasper.repository.filter.TagFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WithMockUser("tester")
@MultiTenantIntegrationTest
@Transactional
public class ExtServiceMTIT {

	@Autowired
	Props props;

	@Autowired
	ExtService extService;

	@Autowired
	ExtRepository extRepository;

	@Autowired
	TemplateRepository templateRepository;

	@Autowired
	UserRepository userRepository;

	Ext getExt() {
		var ext = new Ext();
		ext.setOrigin("@other");
		return ext;
	}

	User getUser() {
		var user = new User();
		user.setOrigin("@other");
		return user;
	}

	Template getTemplate() {
		var t = new Template();
		t.setOrigin("@other");
		return t;
	}

	@BeforeEach
	void clearDefaultPermissions() {
		props.setDefaultReadAccess(null);
		props.setDefaultWriteAccess(null);
		props.setDefaultTagReadAccess(null);
		props.setDefaultTagWriteAccess(null);
	}

	@Test
	void testCreateExt() {
		var ext = getExt();
		ext.setTag("custom");
		ext.setName("Custom");

		assertThatThrownBy(() -> extService.create(ext))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testCreateUserExt() {
		var ext = getExt();
		ext.setTag("+user/tester");
		ext.setName("Custom");

		extService.create(ext);

		assertThat(extRepository.existsByQualifiedTag("+user/tester@other"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("+user/tester@other").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/tester");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	@WithMockUser(value = "tester", roles = "MOD")
	void testModCreateExt() {
		var ext = getExt();
		ext.setTag("custom");
		ext.setName("Custom");

		extService.create(ext);

		assertThat(extRepository.existsByQualifiedTag("custom@other"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("custom@other").get();
		assertThat(fetched.getTag())
			.isEqualTo("custom");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	void testReadNonExistentExt() {
		assertThatThrownBy(() -> extService.get("custom@other"))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void testReadNonExistentPrivateExt() {
		var user = getUser();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_secret"));
		userRepository.save(user);

		assertThatThrownBy(() -> extService.get("_secret@other"))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void testReadPrivateExtDenied() {
		assertThatThrownBy(() -> extService.get("_secret@other"))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testReadPublicExt() {
		var ext = getExt();
		ext.setTag("public");
		ext.setName("Custom");
		extRepository.save(ext);

		var fetched = extService.get("public@other");

		assertThat(fetched.getTag())
			.isEqualTo("public");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	void testReadPublicRemoteExt() {
		var ext = getExt();
		ext.setTag("public");
		ext.setOrigin("@remote");
		ext.setName("Custom");
		extRepository.save(ext);

		assertThatThrownBy(() -> extService.get("public@remote"))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testReadUserExt() {
		var ext = getExt();
		ext.setTag("+user/tester");
		ext.setName("Custom");
		extRepository.save(ext);

		var fetched = extService.get("+user/tester@other");

		assertThat(fetched.getTag())
			.isEqualTo("+user/tester");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	void testReadExt() {
		var ext = getExt();
		ext.setTag("custom");
		ext.setName("Custom");
		extRepository.save(ext);

		var fetched = extService.get("custom@other");

		assertThat(fetched.getTag())
			.isEqualTo("custom");
		assertThat(fetched.getName())
			.isEqualTo("Custom");
	}

	@Test
	void testReadPrivateExtFailed() {
		var ext = getExt();
		ext.setTag("_secret");
		ext.setName("Secret");
		extRepository.save(ext);

		assertThatThrownBy(() -> extService.get("_secret@other"))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testReadPrivateExt() {
		var user = getUser();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_secret"));
		userRepository.save(user);
		var ext = getExt();
		ext.setTag("_secret");
		ext.setName("Secret");
		extRepository.save(ext);

		var fetched = extService.get("_secret@other");

		assertThat(fetched.getTag())
			.isEqualTo("_secret");
		assertThat(fetched.getName())
			.isEqualTo("Secret");
	}

	@Test
	@WithMockUser(value = "tester", roles = {"USER", "PRIVATE"})
	void testReadPrivateUserExt() {
		var ext = getExt();
		ext.setTag("_user/tester");
		ext.setName("Secret");
		extRepository.save(ext);

		var fetched = extService.get("_user/tester@other");

		assertThat(fetched.getTag())
			.isEqualTo("_user/tester");
		assertThat(fetched.getName())
			.isEqualTo("Secret");
	}

	@Test
	void testReadPrivateUserExtFailed() {
		var ext = getExt();
		ext.setTag("_user/other");
		ext.setName("Secret");
		extRepository.save(ext);

		assertThatThrownBy(() -> extService.get("_user/other@other"))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void testPagePublicExt() {
		var ext = getExt();
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
		var ext = getExt();
		ext.setTag("custom");
		ext.setName("Custom");
		extRepository.save(ext);
		var remote = getExt();
		remote.setTag("extra");
		remote.setOrigin("@remote");
		remote.setName("Extra");
		extRepository.save(remote);

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
	@WithMockUser(value = "tester", roles = {"MOD"})
	void testPagePublicRemoteExtMod() {
		var ext = getExt();
		ext.setTag("custom");
		ext.setName("Custom");
		extRepository.save(ext);
		var remote = getExt();
		remote.setTag("extra");
		remote.setOrigin("@remote");
		remote.setName("Extra");
		extRepository.save(remote);

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
	@WithMockUser(value = "tester", roles = {"ADMIN"})
	void testPagePublicRemoteExtAdmin() {
		var ext = getExt();
		ext.setTag("custom");
		ext.setName("Custom");
		extRepository.save(ext);
		var remote = getExt();
		remote.setTag("extra");
		remote.setOrigin("@remote");
		remote.setName("Extra");
		extRepository.save(remote);

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
	@WithMockUser(value = "tester", roles = {"SYSADMIN"})
	void testPagePublicRemoteExtSysAdmin() {
		var ext = getExt();
		ext.setTag("custom");
		ext.setName("Custom");
		extRepository.save(ext);
		var remote = getExt();
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
	void testPagePublicRemoteReadAccessExt() {
		props.setDefaultReadAccess(new String[]{"@remote"});
		var ext = getExt();
		ext.setTag("custom");
		ext.setName("Custom");
		extRepository.save(ext);
		var remote = getExt();
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
		var ext = getExt();
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
		var user = getUser();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_secret"));
		userRepository.save(user);
		var ext = getExt();
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
		var ext = getExt();
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
		var ext = getExt();
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
		var ext = getExt();
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
		var user = getUser();
		user.setTag("+user/tester");
		user.setWriteAccess(List.of("+custom"));
		userRepository.save(user);
		var ext = getExt();
		ext.setTag("+custom");
		ext.setName("First");
		extRepository.save(ext);
		var updated = getExt();
		updated.setTag("+custom");
		updated.setName("Second");
		updated.setModified(ext.getModified());

		extService.update(updated);

		assertThat(extRepository.existsByQualifiedTag("+custom@other"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("+custom@other").get();
		assertThat(fetched.getTag())
			.isEqualTo("+custom");
		assertThat(fetched.getName())
			.isEqualTo("Second");
	}

	@Test
	void testUpdateExtFailed() {
		var ext = getExt();
		ext.setTag("custom");
		ext.setName("First");
		extRepository.save(ext);
		var updated = getExt();
		updated.setTag("custom");
		updated.setName("Second");
		updated.setModified(ext.getModified());

		assertThatThrownBy(() -> extService.update(updated))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(extRepository.existsByQualifiedTag("custom@other"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("custom@other").get();
		assertThat(fetched.getTag())
			.isEqualTo("custom");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testUpdateUserExt() {
		var ext = getExt();
		ext.setTag("+user/tester");
		ext.setName("First");
		extRepository.save(ext);
		var updated = getExt();
		updated.setTag("+user/tester");
		updated.setName("Second");
		updated.setModified(ext.getModified());

		extService.update(updated);

		assertThat(extRepository.existsByQualifiedTag("+user/tester@other"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("+user/tester@other").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/tester");
		assertThat(fetched.getName())
			.isEqualTo("Second");
	}

	@Test
	void testUpdateUserExtFailed() {
		var ext = getExt();
		ext.setTag("+user/other");
		ext.setName("First");
		extRepository.save(ext);
		var updated = getExt();
		updated.setTag("+user/other");
		updated.setName("Second");
		updated.setModified(ext.getModified());

		assertThatThrownBy(() -> extService.update(updated))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(extRepository.existsByQualifiedTag("+user/other@other"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("+user/other@other").get();
		assertThat(fetched.getTag())
			.isEqualTo("+user/other");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testUpdatePrivateExt() {
		var user = getUser();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_secret"));
		user.setWriteAccess(List.of("_secret"));
		userRepository.save(user);
		var ext = getExt();
		ext.setTag("_secret");
		ext.setName("First");
		extRepository.save(ext);
		var updated = getExt();
		updated.setTag("_secret");
		updated.setName("Second");
		updated.setModified(ext.getModified());

		extService.update(updated);

		assertThat(extRepository.existsByQualifiedTag("_secret@other"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("_secret@other").get();
		assertThat(fetched.getTag())
			.isEqualTo("_secret");
		assertThat(fetched.getName())
			.isEqualTo("Second");
	}

	@Test
	void testUpdatePrivateExtFailed() {
		var user = getUser();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_secret"));
		userRepository.save(user);
		var ext = getExt();
		ext.setTag("_secret");
		ext.setName("First");
		extRepository.save(ext);
		var updated = getExt();
		updated.setTag("_secret");
		updated.setName("Second");
		updated.setModified(ext.getModified());

		assertThatThrownBy(() -> extService.update(updated))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(extRepository.existsByQualifiedTag("_secret@other"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("_secret@other").get();
		assertThat(fetched.getTag())
			.isEqualTo("_secret");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testUpdatePublicExtFailed() {
		var ext = getExt();
		ext.setTag("public");
		ext.setName("First");
		extRepository.save(ext);
		var updated = getExt();
		updated.setTag("public");
		updated.setName("Second");
		updated.setModified(ext.getModified());

		assertThatThrownBy(() -> extService.update(updated))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(extRepository.existsByQualifiedTag("public@other"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("public@other").get();
		assertThat(fetched.getTag())
			.isEqualTo("public");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testDeleteExt() {
		var user = getUser();
		user.setTag("+user/tester");
		user.setWriteAccess(List.of("+custom"));
		userRepository.save(user);
		var ext = getExt();
		ext.setTag("+custom");
		ext.setName("First");
		extRepository.save(ext);

		extService.delete("+custom@other");

		assertThat(extRepository.existsByQualifiedTag("+custom@other"))
			.isFalse();
	}

	@Test
	void testDeleteExtFailed() {
		var ext = getExt();
		ext.setTag("custom");
		ext.setName("First");
		extRepository.save(ext);

		assertThatThrownBy(() -> extService.delete("custom@other"))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(extRepository.existsByQualifiedTag("custom@other"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("custom@other").get();
		assertThat(fetched.getTag())
			.isEqualTo("custom");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testDeletePrivateExt() {
		var user = getUser();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_secret"));
		user.setWriteAccess(List.of("_secret"));
		userRepository.save(user);
		var ext = getExt();
		ext.setTag("_secret");
		ext.setName("First");
		extRepository.save(ext);

		extService.delete("_secret@other");

		assertThat(extRepository.existsByQualifiedTag("_secret@other"))
			.isFalse();
	}

	@Test
	void testDeletePrivateExtFailed() {
		var user = getUser();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_secret"));
		userRepository.save(user);
		var ext = getExt();
		ext.setTag("_secret");
		ext.setName("First");
		extRepository.save(ext);

		assertThatThrownBy(() -> extService.delete("_secret@other"))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(extRepository.existsByQualifiedTag("_secret@other"))
			.isTrue();
		var fetched = extRepository.findOneByQualifiedTag("_secret@other").get();
		assertThat(fetched.getTag())
			.isEqualTo("_secret");
		assertThat(fetched.getName())
			.isEqualTo("First");
	}

	@Test
	void testValidateExt() {
		var ext = getExt();
		ext.setTag("+user/tester");
		ext.setName("First");

		extService.validate(ext, false);
	}

	@Test
	void testValidateTagWithInvalidTemplate() throws IOException {
		var template = getTemplate();
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
		var ext = getExt();
		ext.setTag("+user/tester");
		ext.setName("First");

		assertThatThrownBy(() -> extService.validate(ext, false))
			.isInstanceOf(InvalidTemplateException.class);
	}

	@Test
	void testValidateTagWithTemplate() throws IOException {
		var template = getTemplate();
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
		var ext = getExt();
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
		var user = getUser();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_slug/custom"));
		user.setWriteAccess(List.of("_slug/custom"));
		userRepository.save(user);
		var ext = getExt();
		ext.setTag("_slug/custom");
		ext.setName("First");

		extService.validate(ext, false);
	}

	@Test
	void testValidatePrivateTagWithInvalidTemplate() throws IOException {
		var user = getUser();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_slug/custom"));
		user.setWriteAccess(List.of("_slug/custom"));
		userRepository.save(user);
		var template = getTemplate();
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
		var ext = getExt();
		ext.setTag("_slug/custom");
		ext.setName("First");

		assertThatThrownBy(() -> extService.validate(ext, false))
			.isInstanceOf(InvalidTemplateException.class);
	}

	@Test
	void testValidateTagWithMergedTemplate() throws IOException {
		var user = getUser();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("+slug/more/custom"));
		user.setWriteAccess(List.of("+slug/more/custom"));
		userRepository.save(user);
		var mapper = new ObjectMapper();
		var template1 = getTemplate();
		template1.setTag("slug");
		template1.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));
		templateRepository.save(template1);
		var template2 = getTemplate();
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
		var ext = getExt();
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
		var user = getUser();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("+slug/more/custom"));
		user.setWriteAccess(List.of("+slug/more/custom"));
		userRepository.save(user);
		var mapper = new ObjectMapper();
		var template1 = getTemplate();
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
		var template2 = getTemplate();
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
		var ext = getExt();
		ext.setTag("+slug/more/custom");
		ext.setName("First");

		extService.validate(ext, true);
	}

	@Test
	void testValidatePrivateTagWithTemplate() throws IOException {
		var user = getUser();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_slug/custom"));
		user.setWriteAccess(List.of("_slug/custom"));
		userRepository.save(user);
		var template = getTemplate();
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
		var ext = getExt();
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
		var user = getUser();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_slug/custom"));
		user.setWriteAccess(List.of("_slug/custom"));
		userRepository.save(user);
		var template = getTemplate();
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
		var ext = getExt();
		ext.setTag("_slug/custom");
		ext.setName("First");

		assertThatThrownBy(() -> extService.validate(ext, false))
			.isInstanceOf(InvalidTemplateException.class);
	}

	@Test
	void testValidatePrivateTagWithPrivateTemplate() throws IOException {
		var user = getUser();
		user.setTag("+user/tester");
		user.setReadAccess(List.of("_slug/custom"));
		user.setWriteAccess(List.of("_slug/custom"));
		userRepository.save(user);
		var template = getTemplate();
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
		var ext = getExt();
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
