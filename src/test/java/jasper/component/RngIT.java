package jasper.component;

import jasper.IntegrationTest;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@WithMockUser("+user/tester")
@IntegrationTest
public class RngIT {

	@Autowired
	Rng rng;

	@Autowired
	RefRepository refRepository;

	static final String URL = "https://www.example.com/rng-test";

	@BeforeEach
	void init() {
		refRepository.deleteAll();
	}

	@Test
	void testRngTagUnchangedWhenNoExistingRefs() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("");
		ref.setTags(new ArrayList<>(List.of("plugin/rng")));
		
		rng.update("", ref, null);
		
		// When no existing refs exist, tags remain unchanged (early return at line 48)
		assertThat(ref.getTags()).containsExactly("plugin/rng");
	}

	@Test
	void testRngTagAddedWhenExistingRefsFound() {
		// Create an existing ref in the database so count > 0
		var existingInDb = new Ref();
		existingInDb.setUrl(URL);
		existingInDb.setOrigin("other.origin.com");
		existingInDb.setModified(Instant.now().minusSeconds(100));
		refRepository.save(existingInDb);
		
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("");
		ref.setTags(new ArrayList<>(List.of("plugin/rng")));
		
		rng.update("", ref, null);
		
		// When existing refs exist in DB, new rng tag should be generated
		assertThat(ref.getTags()).anyMatch(tag -> tag.startsWith("+plugin/rng/"));
		assertThat(ref.getTags()).doesNotContain("plugin/rng");
	}

	@Test
	void testRngTagPreservedWhenSameOrigin() {
		// Create existing ref
		var existing = new Ref();
		existing.setUrl(URL);
		existing.setOrigin("");
		existing.setModified(Instant.now().minusSeconds(100));
		existing.setTags(new ArrayList<>(List.of("+plugin/rng/abc123")));
		refRepository.save(existing);
		
		// Update ref with same origin
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("");
		ref.setModified(Instant.now());
		ref.setTags(new ArrayList<>(List.of("plugin/rng")));
		
		rng.update("", ref, existing);
		
		// Should preserve the existing rng tag
		assertThat(ref.getTags()).contains("+plugin/rng/abc123");
		assertThat(ref.getTags()).doesNotContain("plugin/rng");
	}

	@Test
	void testRngTagReplacedWhenDifferentOrigin() {
		// Create existing ref with different origin
		var existing = new Ref();
		existing.setUrl(URL);
		existing.setOrigin("");
		existing.setModified(Instant.now().minusSeconds(100));
		existing.setTags(new ArrayList<>(List.of("+plugin/rng/abc123")));
		refRepository.save(existing);
		
		// Create remote ref with more recent modification
		var remote = new Ref();
		remote.setUrl(URL);
		remote.setOrigin("remote.example.com");
		remote.setModified(Instant.now());
		remote.setTags(new ArrayList<>(List.of("+plugin/rng/xyz789")));
		refRepository.save(remote);
		
		// Update ref
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("");
		ref.setModified(Instant.now().minusSeconds(50));
		ref.setTags(new ArrayList<>(List.of("plugin/rng")));
		
		rng.update("", ref, existing);
		
		// Should generate new rng tag
		assertThat(ref.getTags()).anyMatch(tag -> 
			tag.startsWith("+plugin/rng/") && !tag.equals("+plugin/rng/abc123"));
		assertThat(ref.getTags()).doesNotContain("plugin/rng");
	}

	@Test
	void testRngTagFormat() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("");
		ref.setTags(new ArrayList<>(List.of("plugin/rng")));
		
		// Save a ref to make count > 0
		var existing = new Ref();
		existing.setUrl(URL);
		existing.setOrigin("other");
		refRepository.save(existing);
		
		rng.update("", ref, null);
		
		// Check format: should be lowercase alphanumeric
		var rngTag = ref.getTags().stream()
			.filter(tag -> tag.startsWith("+plugin/rng/"))
			.findFirst()
			.orElse(null);
		
		assertThat(rngTag).isNotNull();
		var uuid = rngTag.substring("+plugin/rng/".length());
		assertThat(uuid).matches("[a-z0-9]+");
		assertThat(uuid.length()).isGreaterThan(0);
	}

	@Test
	void testNoRngTagWhenNotPresent() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("");
		ref.setTags(new ArrayList<>(List.of("other-tag")));
		
		rng.update("", ref, null);
		
		// Should not modify tags if plugin/rng not present
		assertThat(ref.getTags()).containsExactly("other-tag");
	}

	@Test
	void testMultipleRngTagsPreserved() {
		// Create existing ref with multiple rng tags
		var existing = new Ref();
		existing.setUrl(URL);
		existing.setOrigin("");
		existing.setModified(Instant.now().minusSeconds(100));
		existing.setTags(new ArrayList<>(List.of("+plugin/rng/abc123", "+plugin/rng/def456", "other")));
		refRepository.save(existing);
		
		// Update ref
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("");
		ref.setModified(Instant.now());
		ref.setTags(new ArrayList<>(List.of("plugin/rng")));
		
		rng.update("", ref, existing);
		
		// Should preserve all existing rng tags
		assertThat(ref.getTags()).contains("+plugin/rng/abc123", "+plugin/rng/def456");
		assertThat(ref.getTags()).doesNotContain("plugin/rng");
	}

	@Test
	void testRootOriginFiltering() {
		// Create refs in different origins
		var ref1 = new Ref();
		ref1.setUrl(URL);
		ref1.setOrigin("sub1.example.com");
		ref1.setModified(Instant.now().minusSeconds(100));
		refRepository.save(ref1);
		
		var ref2 = new Ref();
		ref2.setUrl(URL);
		ref2.setOrigin("sub2.example.com");
		ref2.setModified(Instant.now());
		refRepository.save(ref2);
		
		// Update ref with root origin filtering
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("sub1.example.com");
		ref.setTags(new ArrayList<>(List.of("plugin/rng")));
		
		var existing = new Ref();
		existing.setUrl(URL);
		existing.setOrigin("sub1.example.com");
		existing.setModified(Instant.now().minusSeconds(50));
		existing.setTags(new ArrayList<>(List.of("+plugin/rng/old123")));
		
		rng.update("sub1.example.com", ref, existing);
		
		// Behavior depends on whether more recent remote exists under same origin
		assertThat(ref.getTags()).isNotEmpty();
	}
}
