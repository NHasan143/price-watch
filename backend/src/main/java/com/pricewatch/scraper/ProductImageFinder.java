package com.pricewatch.scraper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Finds the product photo for the card. Tries what shops publish for link previews and search
 * engines first (Open Graph, Twitter card, image_src, schema.org JSON-LD and microdata), then the
 * main gallery image of shops that publish none of those but mark it in the HTML (Amazon,
 * WooCommerce), then the photo headless Chrome marked as the largest image near the top of the
 * page, for shops such as Best Buy.
 */
@Component
public class ProductImageFinder {

    /** Set by {@link ChromePageRenderer} on the largest image in the first screens of the page. */
    static final String HERO_ATTRIBUTE = "data-pricewatch-hero";

    private static final int MAX_IMAGE_URL_LENGTH = 2048;
    private static final int MAX_DEPTH = 8;

    private final ObjectMapper mapper;

    public ProductImageFinder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** @return an absolute http(s) image URL, or null when the page has no usable product photo */
    public String find(Document document) {
        return attributeUrl(document, "meta[property=\"og:image\"], meta[property=\"og:image:url\"]", "content")
                .or(() -> attributeUrl(document, "meta[name=\"twitter:image\"], meta[property=\"twitter:image\"]", "content"))
                .or(() -> attributeUrl(document, "link[rel=\"image_src\"]", "href"))
                .or(() -> fromJsonLd(document))
                .or(() -> attributeUrl(document, "[itemprop=image][content]", "content"))
                .or(() -> attributeUrl(document, "img[itemprop=image]", "src"))
                .or(() -> fromGallery(document))
                .or(() -> attributeUrl(document, "img[" + HERO_ATTRIBUTE + "]", "src"))
                .orElse(null);
    }

    private static Optional<String> attributeUrl(Document document, String selector, String attribute) {
        for (Element element : document.select(selector)) {
            Optional<String> url = usable(absolute(element, attribute));
            if (url.isPresent()) {
                return url;
            }
        }
        return Optional.empty();
    }

    /**
     * Amazon's main image ({@code #landingImage}, or {@code #imgBlkFront} for books): the full-size
     * {@code data-old-hires}, else the largest size listed in {@code data-a-dynamic-image}, else the
     * shown {@code src}. Then WooCommerce's {@code data-large_image}.
     */
    private Optional<String> fromGallery(Document document) {
        for (Element image : document.select("img#landingImage, img#imgBlkFront")) {
            Optional<String> url = usable(absolute(image, "data-old-hires"))
                    .or(() -> largestDynamicImage(image).flatMap(raw -> usable(resolve(document, raw))))
                    .or(() -> usable(absolute(image, "src")));
            if (url.isPresent()) {
                return url;
            }
        }
        return attributeUrl(document, "img[data-large_image]", "data-large_image");
    }

    /** {@code data-a-dynamic-image} maps each image URL to its [width, height]. */
    private Optional<String> largestDynamicImage(Element image) {
        String json = image.attr("data-a-dynamic-image");
        if (json.isBlank()) {
            return Optional.empty();
        }
        try {
            String best = null;
            long bestArea = -1;
            for (var entry : mapper.readTree(json).properties()) {
                JsonNode size = entry.getValue();
                long area = size.path(0).asLong() * size.path(1).asLong();
                if (area > bestArea) {
                    best = entry.getKey();
                    bestArea = area;
                }
            }
            return Optional.ofNullable(best);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private Optional<String> fromJsonLd(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                Optional<String> url = productImage(mapper.readTree(script.data()), 0)
                        .flatMap(raw -> usable(resolve(document, raw)));
                if (url.isPresent()) {
                    return url;
                }
            } catch (JsonProcessingException e) {
                // malformed block: try the next one
            }
        }
        return Optional.empty();
    }

    /** Walks the JSON for a Product (or anything with offers) and reads its "image". */
    private static Optional<String> productImage(JsonNode node, int depth) {
        if (node == null || depth > MAX_DEPTH) {
            return Optional.empty();
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<String> found = productImage(child, depth + 1);
                if (found.isPresent()) {
                    return found;
                }
            }
            return Optional.empty();
        }
        if (!node.isObject()) {
            return Optional.empty();
        }
        String type = node.path("@type").asText("");
        if ((type.contains("Product") || node.has("offers")) && node.has("image")) {
            Optional<String> image = imageUrl(node.get("image"));
            if (image.isPresent()) {
                return image;
            }
        }
        for (JsonNode child : node) {
            if (child.isContainerNode()) {
                Optional<String> found = productImage(child, depth + 1);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /** "image" may be a URL, a list of URLs, or an ImageObject with "url" / "contentUrl". */
    private static Optional<String> imageUrl(JsonNode image) {
        if (image.isTextual()) {
            return Optional.of(image.asText());
        }
        if (image.isArray()) {
            for (JsonNode item : image) {
                Optional<String> url = imageUrl(item);
                if (url.isPresent()) {
                    return url;
                }
            }
        }
        if (image.isObject()) {
            JsonNode url = image.has("url") ? image.get("url") : image.get("contentUrl");
            if (url != null && url.isTextual()) {
                return Optional.of(url.asText());
            }
        }
        return Optional.empty();
    }

    private static String resolve(Document document, String raw) {
        try {
            return java.net.URI.create(document.location()).resolve(raw.trim()).toString();
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }

    /** Like {@link Element#absUrl}, but a blank attribute stays blank instead of becoming the page's own URL. */
    private static String absolute(Element element, String attribute) {
        return element.attr(attribute).isBlank() ? "" : element.absUrl(attribute);
    }

    private static Optional<String> usable(String url) {
        if (url == null || url.isBlank() || url.length() > MAX_IMAGE_URL_LENGTH) {
            return Optional.empty();
        }
        return url.startsWith("https://") || url.startsWith("http://") ? Optional.of(url) : Optional.empty();
    }
}
