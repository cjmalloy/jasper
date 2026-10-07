package jasper.repository.filter;

import jasper.IntegrationTest;
import jasper.domain.Ext;
import jasper.domain.Ref;
import jasper.repository.ExtRepository;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class TagQueryIT {

	@Autowired
	RefRepository refRepository;

	@Autowired
	ExtRepository extRepository;

	@BeforeEach
	void init() {
		refRepository.deleteAllInBatch();
		extRepository.deleteAllInBatch();
		ref("r1", "", "a");
		ref("r2", "", "b");
		ref("r3", "", "a", "b");
		ref("r4", "", "c");
		ref("r5", "", "a", "c", "d");
		ref("r6", "@city", "a", "c");
		ref("r7", "@town", "b");
		ref("r8", "@town", "a", "b", "d");
		ext("a", "");
		ext("b", "");
		ext("c", "");
		ext("a", "@city");
		ext("d", "@town");
	}

	void ref(String name, String origin, String... tags) {
		var ref = new Ref();
		ref.setUrl("https://www.example.com/" + name);
		ref.setOrigin(origin);
		ref.setTags(new ArrayList<>(List.of(tags)));
		refRepository.save(ref);
	}

	void ext(String tag, String origin) {
		var ext = new Ext();
		ext.setTag(tag);
		ext.setOrigin(origin);
		ext.setName(tag);
		extRepository.save(ext);
	}

	List<String> refs(String query) {
		return refRepository.findAll(RefFilter.builder().query(query).build().spec()).stream()
			.map(r -> r.getUrl().substring("https://www.example.com/".length()))
			.sorted()
			.toList();
	}

	List<String> exts(String query) {
		return extRepository.findAll(TagFilter.builder().query(query).build().<Ext>spec()).stream()
			.map(e -> e.getTag() + e.getOrigin())
			.sorted()
			.toList();
	}

	@Test
	void testNegatedOrGroup() {
		assertThat(refs("!(a|b)"))
			.isEqualTo(refs("!a:!b"))
			.containsExactly("r4");
		assertThat(exts("!(a|b)"))
			.isEqualTo(exts("!a:!b"))
			.containsExactly("c", "d@town");
	}

	@Test
	void testNegatedAndGroup() {
		assertThat(refs("!(a:b)"))
			.isEqualTo(refs("!a|!b"))
			.containsExactly("r1", "r2", "r4", "r5", "r6", "r7");
		assertThat(exts("!(a:b)"))
			.isEqualTo(exts("!a|!b"))
			.containsExactly("a", "a@city", "b", "c", "d@town");
	}

	@Test
	void testDoubleNegation() {
		assertThat(refs("!!(a|b)"))
			.isEqualTo(refs("a|b"))
			.containsExactly("r1", "r2", "r3", "r5", "r6", "r7", "r8");
		assertThat(refs("!(!a)"))
			.isEqualTo(refs("a"))
			.containsExactly("r1", "r3", "r5", "r6", "r8");
		assertThat(exts("!!(a|b)"))
			.isEqualTo(exts("a|b"));
		assertThat(exts("!(!a)"))
			.isEqualTo(exts("a"))
			.containsExactly("a", "a@city");
	}

	@Test
	void testNestedNegatedGroup() {
		assertThat(refs("a:!(b|c:d)"))
			.isEqualTo(refs("a:!b:(!c|!d)"))
			.containsExactly("r1", "r6");
		assertThat(refs("!(a:!(b|c))"))
			.isEqualTo(refs("!a|b|c"))
			.containsExactly("r2", "r3", "r4", "r5", "r6", "r7", "r8");
		assertThat(exts("!(a:!(b|@city))"))
			.isEqualTo(exts("!a|b|@city"))
			.containsExactly("a@city", "b", "c", "d@town");
	}

	@Test
	void testNegatedOriginGroup() {
		assertThat(refs("!(@city|@town)"))
			.isEqualTo(refs("!@city:!@town"))
			.containsExactly("r1", "r2", "r3", "r4", "r5");
		assertThat(exts("!(@city|@town)"))
			.isEqualTo(exts("!@city:!@town"))
			.containsExactly("a", "b", "c");
	}

	@Test
	void testNegatedQualifiedTagGroup() {
		assertThat(refs("!(a@town|c@city)"))
			.isEqualTo(refs("!a@town:!c@city"))
			.containsExactly("r1", "r2", "r3", "r4", "r5", "r7");
		assertThat(refs("b:!(a@town|@)"))
			.isEqualTo(refs("b:!a@town:!@"))
			.containsExactly("r7");
		assertThat(exts("!(a@city|d@town)"))
			.isEqualTo(exts("!a@city:!d@town"))
			.containsExactly("a", "b", "c");
	}
}
