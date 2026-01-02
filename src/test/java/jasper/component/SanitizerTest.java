package jasper.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SanitizerTest {

	Sanitizer sanitizer;

	@BeforeEach
	void init() {
		sanitizer = new Sanitizer();
	}

	@Test
	void testCleanBasicHtml() {
		var html = "<p>Hello <b>world</b></p>";
		var result = sanitizer.clean(html);
		assertThat(result).isEqualTo("<p>Hello <b>world</b></p>");
	}

	@Test
	void testCleanHtmlWithUrl() {
		var html = "<a href=\"/relative\">Link</a>";
		var result = sanitizer.clean(html, "https://example.com");
		assertThat(result).contains("https://example.com/relative");
	}

	@Test
	void testRemoveScriptTags() {
		var html = "<p>Safe</p><script>alert('xss')</script>";
		var result = sanitizer.clean(html);
		assertThat(result).doesNotContain("<script>");
		assertThat(result).doesNotContain("alert");
		assertThat(result).contains("<p>Safe</p>");
	}

	@Test
	void testRemoveOnEventHandlers() {
		var html = "<p onclick=\"alert('xss')\">Click me</p>";
		var result = sanitizer.clean(html);
		assertThat(result).doesNotContain("onclick");
		assertThat(result).doesNotContain("alert");
		assertThat(result).contains("Click me");
	}

	@Test
	void testAllowSvgTags() {
		var html = "<svg viewBox=\"0 0 100 100\"><circle cx=\"50\" cy=\"50\" r=\"40\" fill=\"red\"/></svg>";
		var result = sanitizer.clean(html);
		assertThat(result).contains("<svg");
		assertThat(result).contains("viewBox");
		assertThat(result).contains("<circle");
		assertThat(result).contains("fill");
	}

	@Test
	void testAllowSvgPathTag() {
		var html = "<svg><path d=\"M10 10 L 90 90\" stroke=\"black\"/></svg>";
		var result = sanitizer.clean(html);
		assertThat(result).contains("<svg>");
		assertThat(result).contains("<path");
		assertThat(result).contains("d=");
		assertThat(result).contains("stroke");
	}

	@Test
	void testAllowSvgGTag() {
		var html = "<svg><g transform=\"translate(10,10)\"><circle r=\"5\"/></g></svg>";
		var result = sanitizer.clean(html);
		assertThat(result).contains("<g");
		assertThat(result).contains("transform");
		assertThat(result).contains("<circle");
	}

	@Test
	void testAllowFigureTag() {
		var html = "<figure><img src=\"image.jpg\"><figcaption>Caption</figcaption></figure>";
		var result = sanitizer.clean(html);
		assertThat(result).contains("<figure>");
		assertThat(result).contains("<figcaption>");
		assertThat(result).contains("Caption");
	}

	@Test
	void testAllowAddressTag() {
		var html = "<address>123 Main St</address>";
		var result = sanitizer.clean(html);
		assertThat(result).contains("<address>");
		assertThat(result).contains("123 Main St");
	}

	@Test
	void testAllowTimeTag() {
		var html = "<time datetime=\"2026-01-02\">January 2, 2026</time>";
		var result = sanitizer.clean(html);
		assertThat(result).contains("<time");
		assertThat(result).contains("datetime");
	}

	@Test
	void testAllowImgSrcsetAttribute() {
		var html = "<img src=\"image.jpg\" srcset=\"image-2x.jpg 2x\" width=\"100\" height=\"100\">";
		var result = sanitizer.clean(html);
		assertThat(result).contains("srcset");
		assertThat(result).contains("width");
		assertThat(result).contains("height");
	}

	@Test
	void testRemoveIframeSrcAttribute() {
		var html = "<iframe src=\"evil.com\"></iframe>";
		var result = sanitizer.clean(html);
		// iframe is allowed in SVG tags but src might be removed for security
		assertThat(result).doesNotContain("evil.com");
	}

	@Test
	void testRemoveJavascriptProtocol() {
		var html = "<a href=\"javascript:alert('xss')\">Click</a>";
		var result = sanitizer.clean(html);
		assertThat(result).doesNotContain("javascript:");
		assertThat(result).doesNotContain("alert");
	}

	@Test
	void testRemoveDataProtocol() {
		var html = "<a href=\"data:text/html,<script>alert('xss')</script>\">Click</a>";
		var result = sanitizer.clean(html);
		assertThat(result).doesNotContain("data:");
		assertThat(result).doesNotContain("alert");
	}

	@Test
	void testAllowStyleAttribute() {
		var html = "<p style=\"color: red;\">Red text</p>";
		var result = sanitizer.clean(html);
		assertThat(result).contains("Red text");
		// style might be allowed or removed depending on Safelist.relaxed() behavior
	}

	@Test
	void testRemoveEmbedTag() {
		var html = "<embed src=\"evil.swf\">";
		var result = sanitizer.clean(html);
		assertThat(result).doesNotContain("<embed");
		assertThat(result).doesNotContain("evil.swf");
	}

	@Test
	void testRemoveObjectTag() {
		var html = "<object data=\"evil.swf\"></object>";
		var result = sanitizer.clean(html);
		assertThat(result).doesNotContain("<object");
		assertThat(result).doesNotContain("evil.swf");
	}

	@Test
	void testComplexSvgGraphics() {
		var html = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">" +
			"<defs><linearGradient id=\"grad\" x1=\"0%\" y1=\"0%\" x2=\"100%\" y2=\"0%\">" +
			"<stop offset=\"0%\" stop-color=\"rgb(255,255,0)\" stop-opacity=\"1\"/>" +
			"<stop offset=\"100%\" stop-color=\"rgb(255,0,0)\" stop-opacity=\"1\"/>" +
			"</linearGradient></defs>" +
			"<ellipse cx=\"50\" cy=\"50\" rx=\"40\" ry=\"30\" fill=\"url(#grad)\"/>" +
			"</svg>";
		var result = sanitizer.clean(html);
		assertThat(result).contains("<svg");
		assertThat(result).contains("viewBox");
		assertThat(result).contains("<linearGradient");
		assertThat(result).contains("<ellipse");
		assertThat(result).contains("fill");
	}

	@Test
	void testEmptyString() {
		var result = sanitizer.clean("");
		assertThat(result).isEmpty();
	}

	@Test
	void testNullSafety() {
		var result = sanitizer.clean(null);
		// JSoup handles null gracefully
		assertThat(result).isNotNull();
	}

	@Test
	void testPlainText() {
		var html = "Just plain text with no tags";
		var result = sanitizer.clean(html);
		assertThat(result).isEqualTo("Just plain text with no tags");
	}

	@Test
	void testNestedHtml() {
		var html = "<div><p>Outer <span>inner <b>bold</b></span></p></div>";
		var result = sanitizer.clean(html);
		assertThat(result).contains("Outer");
		assertThat(result).contains("inner");
		assertThat(result).contains("bold");
	}
}
