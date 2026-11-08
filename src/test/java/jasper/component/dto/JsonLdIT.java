package jasper.component.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jasper.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins how {@link JsonLd} is read with the app mapper, as done in Scraper.parseLinkedData.
 */
@IntegrationTest
public class JsonLdIT {

	@Autowired
	ObjectMapper objectMapper;

	/**
	 * Same branching as Scraper.parseLinkedData.
	 */
	List<JsonLd> parse(String html) throws Exception {
		var json = html.trim().replaceAll("\n", " ");
		return json.startsWith("{") ?
			List.of(objectMapper.readValue(json, JsonLd.class)) :
			objectMapper.readValue(json, new TypeReference<List<JsonLd>>(){});
	}

	JsonLd one(String json) throws Exception {
		var list = parse(json);
		assertThat(list).hasSize(1);
		return list.get(0);
	}

	@Test
	void typeSingleValue() throws Exception {
		var ld = one("{\"@context\": \"https://schema.org\", \"@type\": \"NewsArticle\"}");
		assertThat(ld.getContext()).isEqualTo("https://schema.org");
		assertThat(ld.getTypes()).containsExactly("NewsArticle");
		assertThat(ld.getType()).isEqualTo("NewsArticle");
	}

	@Test
	void typeArray() throws Exception {
		var ld = one("{\"@type\": [\"VideoObject\", \"CreativeWork\"]}");
		assertThat(ld.getTypes()).containsExactly("VideoObject", "CreativeWork");
		assertThat(ld.getType()).isEqualTo("VideoObject");
	}

	@Test
	void typeAbsentNullAndEmpty() throws Exception {
		assertThat(one("{}").getTypes()).isNull();
		assertThat(one("{}").getType()).isNull();
		assertThat(one("{\"@type\": null}").getTypes()).isNull();
		assertThat(one("{\"@type\": \"\"}").getTypes()).containsExactly("");
		assertThat(one("{\"@type\": []}").getTypes()).isNotNull().isEmpty();
	}

	@Test
	void thumbnailUrlsSingleValue() throws Exception {
		var ld = one("{\"thumbnailUrls\": \"https://example.com/t.jpg\"}");
		assertThat(ld.getThumbnailUrls()).containsExactly("https://example.com/t.jpg");
		assertThat(ld.getThumbnailUrl()).isEqualTo("https://example.com/t.jpg");
	}

	@Test
	void thumbnailUrlsArray() throws Exception {
		var ld = one("{\"thumbnailUrls\": [\"https://example.com/small.jpg\", \"https://example.com/large.jpg\"]}");
		assertThat(ld.getThumbnailUrls()).containsExactly("https://example.com/small.jpg", "https://example.com/large.jpg");
		assertThat(ld.getThumbnailUrl()).isEqualTo("https://example.com/large.jpg");
	}

	@Test
	void thumbnailUrlSingularIsNotMapped() throws Exception {
		// Only "thumbnailUrls" is a property; schema.org "thumbnailUrl" is silently ignored
		var ld = one("{\"thumbnailUrl\": \"https://example.com/t.jpg\"}");
		assertThat(ld.getThumbnailUrls()).isNull();
		assertThat(ld.getThumbnailUrl()).isNull();
	}

	@Test
	void imageSingleValue() throws Exception {
		var text = one("{\"image\": \"https://example.com/i.jpg\"}");
		assertThat(text.getImage()).hasSize(1);
		assertThat(text.getImage().get(0).isTextual()).isTrue();
		assertThat(text.getImage().get(0).textValue()).isEqualTo("https://example.com/i.jpg");

		var obj = one("{\"image\": {\"@type\": \"ImageObject\", \"contentUrl\": \"https://example.com/i.jpg\"}}");
		assertThat(obj.getImage()).hasSize(1);
		assertThat(obj.getImage().get(0).isObject()).isTrue();
		var image = objectMapper.convertValue(obj.getImage().get(0), JsonLd.class);
		assertThat(image.getType()).isEqualTo("ImageObject");
		assertThat(image.getContentUrl()).isEqualTo("https://example.com/i.jpg");
	}

	@Test
	void imageArray() throws Exception {
		var ld = one("{\"image\": [\"https://example.com/a.jpg\", {\"@type\": [\"ImageObject\"], \"embedUrl\": \"https://example.com/e\"}]}");
		assertThat(ld.getImage()).hasSize(2);
		assertThat(ld.getImage().get(0).textValue()).isEqualTo("https://example.com/a.jpg");
		var image = objectMapper.convertValue(ld.getImage().get(1), JsonLd.class);
		assertThat(image.getTypes()).containsExactly("ImageObject");
		assertThat(image.getEmbedUrl()).isEqualTo("https://example.com/e");
	}

	@Test
	void videoSingleValue() throws Exception {
		var text = one("{\"video\": \"https://example.com/v.mp4\"}");
		assertThat(text.getVideo()).hasSize(1);
		assertThat(text.getVideo().get(0).textValue()).isEqualTo("https://example.com/v.mp4");

		var obj = one("{\"video\": {\"@type\": \"VideoObject\", \"contentUrl\": \"https://example.com/v.mp4\", \"thumbnailUrls\": \"https://example.com/t.jpg\"}}");
		assertThat(obj.getVideo()).hasSize(1);
		var video = objectMapper.convertValue(obj.getVideo().get(0), JsonLd.class);
		assertThat(video.getType()).isEqualTo("VideoObject");
		assertThat(video.getContentUrl()).isEqualTo("https://example.com/v.mp4");
		assertThat(video.getThumbnailUrls()).containsExactly("https://example.com/t.jpg");
	}

	@Test
	void videoArray() throws Exception {
		var ld = one("{\"video\": [{\"@type\": \"VideoObject\", \"embedUrl\": \"https://example.com/e1\"}, \"https://example.com/v.mp4\"]}");
		assertThat(ld.getVideo()).hasSize(2);
		assertThat(ld.getVideo().get(0).isObject()).isTrue();
		assertThat(objectMapper.convertValue(ld.getVideo().get(0), JsonLd.class).getEmbedUrl()).isEqualTo("https://example.com/e1");
		assertThat(ld.getVideo().get(1).textValue()).isEqualTo("https://example.com/v.mp4");
	}

	@Test
	void authorSingleValue() throws Exception {
		var text = one("{\"author\": \"Alice\"}");
		assertThat(text.getAuthor()).hasSize(1);
		assertThat(text.getAuthor().get(0).textValue()).isEqualTo("Alice");

		var obj = one("{\"author\": {\"@type\": \"Person\", \"name\": \"Alice\"}}");
		assertThat(obj.getAuthor()).hasSize(1);
		assertThat(obj.getAuthor().get(0).get("name").textValue()).isEqualTo("Alice");
	}

	@Test
	void authorArray() throws Exception {
		var ld = one("{\"author\": [{\"@type\": \"Person\", \"name\": \"Alice\"}, \"Bob\"]}");
		assertThat(ld.getAuthor()).hasSize(2);
		assertThat(ld.getAuthor().get(0).get("name").textValue()).isEqualTo("Alice");
		assertThat(ld.getAuthor().get(1).textValue()).isEqualTo("Bob");
	}

	@Test
	void publisherSingleValue() throws Exception {
		var text = one("{\"publisher\": \"ACME\"}");
		assertThat(text.getPublisher()).hasSize(1);
		assertThat(text.getPublisher().get(0).isTextual()).isTrue();

		var obj = one("{\"publisher\": {\"@type\": \"Organization\", \"name\": \"ACME\", \"logo\": {\"@type\": \"ImageObject\", \"url\": \"https://example.com/logo.png\"}}}");
		assertThat(obj.getPublisher()).hasSize(1);
		var pub = objectMapper.convertValue(obj.getPublisher().get(0), JsonLd.class);
		assertThat(pub.getType()).isEqualTo("Organization");
		assertThat(pub.getName().textValue()).isEqualTo("ACME");
		assertThat(pub.getLogo().isObject()).isTrue();
		assertThat(pub.getLogo().get("url").textValue()).isEqualTo("https://example.com/logo.png");
	}

	@Test
	void publisherArray() throws Exception {
		var ld = one("{\"publisher\": [{\"@type\": \"Organization\", \"logo\": [\"https://example.com/a.png\", {\"url\": \"https://example.com/b.png\"}]}, \"ACME\"]}");
		assertThat(ld.getPublisher()).hasSize(2);
		var pub = objectMapper.convertValue(ld.getPublisher().get(0), JsonLd.class);
		// logo is a plain JsonNode: arrays stay arrays
		assertThat(pub.getLogo().isArray()).isTrue();
		assertThat(pub.getLogo().get(0).textValue()).isEqualTo("https://example.com/a.png");
		assertThat(pub.getLogo().get(1).get("url").textValue()).isEqualTo("https://example.com/b.png");
		assertThat(ld.getPublisher().get(1).textValue()).isEqualTo("ACME");
	}

	@Test
	void explicitNullsForArrayFields() throws Exception {
		var ld = one("{\"image\": null, \"video\": null, \"author\": null, \"publisher\": null, \"thumbnailUrls\": null}");
		assertThat(ld.getImage()).isNull();
		assertThat(ld.getVideo()).isNull();
		assertThat(ld.getAuthor()).isNull();
		assertThat(ld.getPublisher()).isNull();
		assertThat(ld.getThumbnailUrls()).isNull();
	}

	@Test
	void nameIsRawJsonNode() throws Exception {
		assertThat(one("{\"name\": \"Title\"}").getName().textValue()).isEqualTo("Title");
		assertThat(one("{\"name\": [\"A\", \"B\"]}").getName().isArray()).isTrue();
		assertThat(one("{\"name\": null}").getName().isNull()).isTrue();
		assertThat(one("{}").getName()).isNull();
	}

	@Test
	void scalarFields() throws Exception {
		var ld = one("""
			{"url": "https://example.com/a", "description": "d", "embedUrl": "https://example.com/e",
			 "contentUrl": "https://example.com/c", "datePublished": "2024-01-02T03:04:05Z"}
			""");
		assertThat(ld.getUrl()).isEqualTo("https://example.com/a");
		assertThat(ld.getDescription()).isEqualTo("d");
		assertThat(ld.getEmbedUrl()).isEqualTo("https://example.com/e");
		assertThat(ld.getContentUrl()).isEqualTo("https://example.com/c");
		assertThat(ld.getDatePublished()).isEqualTo("2024-01-02T03:04:05Z");
	}

	@Test
	void unknownPropertiesAreIgnored() throws Exception {
		var ld = one("{\"@context\": \"https://schema.org\", \"@id\": \"x\", \"headline\": \"h\", \"mainEntityOfPage\": {\"@id\": \"y\"}}");
		assertThat(ld.getContext()).isEqualTo("https://schema.org");
		assertThat(ld.getTypes()).isNull();
	}

	@Test
	void contextAsObjectFails() {
		// @context is a String; an object @context fails the whole block (Scraper logs and skips it)
		assertThatThrownBy(() -> one("{\"@context\": {\"@vocab\": \"https://schema.org/\"}, \"@type\": \"Thing\"}"))
			.isInstanceOf(MismatchedInputException.class);
	}

	@Test
	void topLevelList() throws Exception {
		var list = parse("""
			[
			  {"@type": "NewsArticle", "datePublished": "2024-01-02"},
			  {"@type": ["VideoObject"], "contentUrl": "https://example.com/v.mp4"}
			]
			""");
		assertThat(list).hasSize(2);
		assertThat(list.get(0).getType()).isEqualTo("NewsArticle");
		assertThat(list.get(0).getDatePublished()).isEqualTo("2024-01-02");
		assertThat(list.get(1).getTypes()).containsExactly("VideoObject");
		assertThat(list.get(1).getContentUrl()).isEqualTo("https://example.com/v.mp4");
	}

	@Test
	void topLevelObjectWithNewlines() throws Exception {
		var ld = one("""

			  {
			    "@type": "AudioObject",
			    "url": "https://example.com/a.mp3"
			  }
			""");
		assertThat(ld.getType()).isEqualTo("AudioObject");
		assertThat(ld.getUrl()).isEqualTo("https://example.com/a.mp3");
	}

	@Test
	void singleObjectIsNotAcceptedAsTopLevelList() {
		// The list branch does not accept a single object: Scraper must branch on "{"
		assertThatThrownBy(() -> objectMapper.readValue("{\"@type\": \"Thing\"}", new TypeReference<List<JsonLd>>(){}))
			.isInstanceOf(MismatchedInputException.class);
	}

	@Test
	void lenientJsonFeatures() throws Exception {
		// jsonCustomizer enables ALLOW_UNESCAPED_CONTROL_CHARS and ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER
		assertThat(one("{\"description\": \"a\tb\"}").getDescription()).isEqualTo("a\tb");
		assertThat(one("{\"description\": \"it\\'s\"}").getDescription()).isEqualTo("it's");
	}
}
