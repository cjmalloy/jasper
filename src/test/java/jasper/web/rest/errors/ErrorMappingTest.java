package jasper.web.rest.errors;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;

/**
 * Ensures every exception has an error code, category and documentation.
 */
class ErrorMappingTest {

	private static final Path DOCS = Path.of("docs/errors.md");
	private static final Pattern HEADING_ID = Pattern.compile("^#+\\s+(.+?)(?:\\s*\\{#([\\w-]+)})?\\s*$", Pattern.MULTILINE);

	static List<Class<?>> errorClasses() throws ClassNotFoundException {
		var scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AssignableTypeFilter(Throwable.class));
		var result = new ArrayList<Class<?>>();
		for (var bean : scanner.findCandidateComponents("jasper.errors")) {
			result.add(Class.forName(bean.getBeanClassName()));
		}
		return result;
	}

	static List<URI> categories() {
		return Arrays.stream(ErrorConstants.class.getFields())
			.filter(f -> Modifier.isStatic(f.getModifiers()) && f.getType() == URI.class)
			.map(f -> {
				try {
					return (URI) f.get(null);
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			})
			.toList();
	}

	@Test
	void testEveryExceptionIsMapped() throws Exception {
		var classes = errorClasses();
		assertThat(classes).isNotEmpty();
		for (var type : classes) {
			var mapping = ExceptionTranslator.mappingFor(type);
			assertThat(mapping).as(type.getName() + " has no ErrorMapping").isPresent();
			assertThat(mapping.get().code()).as(type.getName() + " code").startsWith("error.");
			assertThat(mapping.get().type()).as(type.getName() + " type").isNotEqualTo(ErrorConstants.DEFAULT_TYPE);
			var responseStatus = findMergedAnnotation(type, ResponseStatus.class);
			if (responseStatus != null && mapping.get().status() != null) {
				assertThat(mapping.get().status()).as(type.getName() + " status").isEqualTo(responseStatus.value());
			}
		}
	}

	@Test
	void testEveryMappingIsCategorized() {
		ExceptionTranslator.ERROR_MAPPINGS.forEach((type, mapping) -> {
			assertThat(mapping.code()).as(type.getName() + " code").startsWith("error.");
			assertThat(categories()).as(type.getName() + " type").contains(mapping.type());
		});
	}

	@Test
	void testSubclassesBeforeSuperclasses() {
		var keys = new ArrayList<>(ExceptionTranslator.ERROR_MAPPINGS.keySet());
		for (var i = 0; i < keys.size(); i++) {
			for (var j = i + 1; j < keys.size(); j++) {
				assertThat(keys.get(i).isAssignableFrom(keys.get(j)))
					.as(keys.get(j).getName() + " is shadowed by " + keys.get(i).getName())
					.isFalse();
			}
		}
	}

	@Test
	void testDocsHaveEveryCategory() throws Exception {
		var docs = Files.readString(DOCS);
		var anchors = new ArrayList<String>();
		var m = HEADING_ID.matcher(docs);
		while (m.find()) {
			anchors.add(m.group(2) != null ? m.group(2) : m.group(1).trim().toLowerCase().replaceAll("[^a-z0-9 _-]", "").replace(' ', '-'));
		}
		for (var category : categories()) {
			assertThat(category.toString()).startsWith(ErrorConstants.PROBLEM_BASE_URL + "#");
			assertThat(anchors).as("docs/errors.md heading for " + category).contains(category.getFragment());
		}
	}

	@Test
	void testDocsHaveEveryCode() throws Exception {
		var docs = Files.readString(DOCS);
		ExceptionTranslator.ERROR_MAPPINGS.forEach((type, mapping) -> {
			assertThat(docs).as("docs/errors.md code for " + type.getName()).contains("`" + mapping.code() + "`");
			assertThat(docs).as("docs/errors.md exception " + type.getName()).contains("`" + type.getSimpleName() + "`");
		});
	}
}
