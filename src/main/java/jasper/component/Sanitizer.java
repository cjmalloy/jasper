package jasper.component;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class Sanitizer {

	private final String[] SVG_TAG_LIST = {
		"a",
		"altGlyph",
		"altGlyphDef",
		"altGlyphItem",
		"animate",
		"animateColor",
		"animateMotion",
		"animateTransform",
		"animation",
		"circle",
		"clipPath",
		"color-profile",
		"cursor",
		"defs",
		"desc",
		"discard",
		"ellipse",
		"feBlend",
		"feColorMatrix",
		"feComponentTransfer",
		"feComposite",
		"feConvolveMatrix",
		"feDiffuseLighting",
		"feDisplacementMap",
		"feDistantLight",
		"feDropShadow",
		"feFlood",
		"feFuncA",
		"feFuncB",
		"feFuncG",
		"feFuncR",
		"feGaussianBlur",
		"feImage",
		"feMerge",
		"feMergeNode",
		"feMorphology",
		"feOffset",
		"fePointLight",
		"feSpecularLighting",
		"feSpotLight",
		"feTile",
		"feTurbulence",
		"filter",
		"font",
		"font-face",
		"font-face-format",
		"font-face-name",
		"font-face-src",
		"font-face-uri",
		"foreignObject",
		"g",
		"glyph",
		"glyphRef",
		"handler",
		"hkern",
		"iframe",
		"image",
		"line",
		"linearGradient",
		"listener",
		"marker",
		"mask",
		"metadata",
		"missing-glyph",
		"mpath",
		"path",
		"pattern",
		"polygon",
		"polyline",
		"radialGradient",
		"rect",
		"set",
		"solidColor",
		"stop",
		"svg",
		"switch",
		"symbol",
		"tbreak",
		"text",
		"textArea",
		"textPath",
		"title",
		"tref",
		"tspan",
		"unknown",
		"use",
		"view",
		"vkern"
	};

	private final String[] SVG_ATTRS = {
		"xmlns",
		"style",
		"viewBox",
		"viewbox",
		"x",
		"y",
		"cx",
		"cy",
		"rx",
		"ry",
		"width",
		"height",
		"r",
		"d",
		"fill",
		"path-length",
		"points",
		"gradient-units",
		"gradient-transform",
		"spread-method",
		"transform",
		"gradient-transform",
		"pattern-transform",
		"alignment-baseline",
		"baseline-shift",
		"clip-path",
		"clip-rule",
		"color",
		"color-interpolation",
		"color-interpolation-filters",
		"color-rendering",
		"cursor",
		"direction",
		"display",
		"dominant-baseline",
		"fill-opacity",
		"fill-rule",
		"filter",
		"flood-color",
		"flood-opacity",
		"font-family",
		"font-size",
		"font-size-adjust",
		"font-stretch",
		"font-style",
		"font-variant",
		"font-weight",
		"glyph-orientation-horizontal",
		"glyph-orientation-vertical",
		"image-rendering",
		"letter-spacing",
		"lighting-color",
		"marker-end",
		"marker-mid",
		"marker-start",
		"mask",
		"opacity",
		"overflow",
		"paint-order",
		"pointer-events",
		"shape-rendering",
		"stop-color",
		"stop-opacity",
		"stroke",
		"stroke-dasharray",
		"stroke-dashoffset",
		"stroke-linecap",
		"stroke-linejoin",
		"stroke-miterlimit",
		"stroke-opacity",
		"stroke-width",
		"text-anchor",
		"text-decoration",
		"text-overflow",
		"text-rendering",
		"unicode-bidi",
		"vector-effect",
		"visibility",
		"white-space",
		"word-spacing",
		"writing-mode"
	};

	private final String[] IMG_ATTRS = {
		"style",
		"width",
		"height"
	};

	// TODO: Allow custom protocols
	private final Safelist whitelist = Safelist.relaxed()
		.addTags(SVG_TAG_LIST)
		.addAttributes("svg", SVG_ATTRS)
		.addAttributes("g", SVG_ATTRS)
		.addAttributes("path", SVG_ATTRS)
		.addAttributes("img", IMG_ATTRS);

	public String clean(String html) {
		return clean(html, null);
	}

	public String clean(String html, String url) {
		return url == null
			? Jsoup.clean(html, whitelist)
			: Jsoup.clean(html, url, whitelist);
	}
}
