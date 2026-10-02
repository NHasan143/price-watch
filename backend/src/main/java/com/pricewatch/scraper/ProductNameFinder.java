package com.pricewatch.scraper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds the product name for the card when the user did not type one. Prefers the name the shop
 * publishes for the product itself (schema.org JSON-LD, then microdata, then Amazon's
 * {@code #productTitle}), then the page's {@code og:title}, then its {@code <title>}.
 *
 * <p>Shops pad names, page titles especially, with their own name, a category and search-engine
 * text ("Amazon.com: … : Electronics", "… Price in Bangladesh"), so each candidate is cleaned:
 * <ol>
 *   <li>a leading or trailing part that is the shop's name (from {@code og:site_name} or the host)
 *       is removed;</li>
 *   <li>when one was, a short trailing category after the same separator is removed too;</li>
 *   <li>known SEO tails such as "Price in Bangladesh" are removed.</li>
 * </ol>
 * A candidate that cleans down to nothing is kept as it was, so names never get worse.
 */
@Component
public class ProductNameFinder {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_DEPTH = 8;

    /** A pipe, a spaced dash or colon, or a colon right after a word ("Amazon.com: …"). Hyphens in "R7-8700F" are not. */
    private static final Pattern SEPARATOR = Pattern.compile("\\s*\\|\\s*|\\s+[-–—:]\\s+|(?<=\\S):\\s+");

    private static final Pattern PRODUCT_ITEMTYPE =
            Pattern.compile("(?i)schema\\.org/(Product|ProductGroup|ProductModel|IndividualProduct)/?$");

    /** "Price in Bangladesh", "at Best Price in India", "Online at Lowest Price in BD 2026". */
    private static final Pattern PRICE_IN_COUNTRY = Pattern.compile(
            "(?:\\s*[-|–—:,]\\s*|\\s+)(?:online\\s+)?(?:at\\s+)?(?:the\\s+)?(?:(?:best|lowest)\\s+)?prices?\\s+in\\s+"
            + "(?:bangladesh|bd|india|pakistan|nepal|sri\\s+lanka)(?:\\s+\\d{4})?$",
            Pattern.CASE_INSENSITIVE);
    /** "Online at Best Price", "at the Lowest Prices". */
    private static final Pattern AT_BEST_PRICE = Pattern.compile(
            "(?:\\s*[-|–—:,]\\s*|\\s+)(?:online\\s+)?at\\s+(?:the\\s+)?(?:best|lowest)\\s+prices?$",
            Pattern.CASE_INSENSITIVE);
    /** "Buy … Online", left behind by the tails above or used on its own. */
    private static final Pattern BUY_ONLINE = Pattern.compile("^buy\\s+(.+?)(?:\\s+online)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUY_ONLINE_STRICT = Pattern.compile("^buy\\s+(.+?)\\s+online$", Pattern.CASE_INSENSITIVE);

    /** Host labels that never name the shop. */
    private static final Set<String> GENERIC_HOST_LABELS = Set.of("www", "m", "com", "co", "net", "org", "shop", "store");

    private static final int MAX_CATEGORY_LENGTH = 40;
    private static final int MAX_CATEGORY_WORDS = 4;

    private final ObjectMapper mapper;

    public ProductNameFinder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** @return the cleaned product name, at most 255 characters, or "" when the page has none */
    public String find(Document document) {
        Set<String> siteKeys = siteKeys(document);
        String name = Stream.<Optional<String>>of(
                        fromJsonLd(document),
                        fromMicrodata(document),
                        text(document.selectFirst("#productTitle")),
                        text(document.selectFirst("meta[property=\"og:title\"]"), "content"),
                        Optional.of(document.title()))
                .flatMap(Optional::stream)
                .map(ProductNameFinder::collapseWhitespace)
                .filter(candidate -> !candidate.isEmpty())
                .findFirst()
                .map(candidate -> clean(candidate, siteKeys))
                .orElse("");
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    // ---- Structured names ----------------------------------------------------------------

    private Optional<String> fromJsonLd(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                Optional<String> name = productName(mapper.readTree(script.data()), 0);
                if (name.isPresent()) {
                    return name;
                }
            } catch (JsonProcessingException e) {
                // malformed block: try the next one
            }
        }
        return Optional.empty();
    }

    /** Walks the JSON for a Product (or ProductGroup, …) and reads its "name". */
    private static Optional<String> productName(JsonNode node, int depth) {
        if (node == null || depth > MAX_DEPTH || !node.isContainerNode()) {
            return Optional.empty();
        }
        if (node.isObject() && isProductType(node.path("@type")) && node.path("name").isTextual()
                && !node.path("name").asText().isBlank()) {
            return Optional.of(node.path("name").asText());
        }
        for (JsonNode child : node) {
            Optional<String> found = productName(child, depth + 1);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /** "@type" may be a string or a list ("Product", ["Product", "Car"]). */
    private static boolean isProductType(JsonNode type) {
        if (type.isArray()) {
            for (JsonNode item : type) {
                if (isProductType(item)) {
                    return true;
                }
            }
            return false;
        }
        return type.isTextual() && type.asText().contains("Product");
    }

    /**
     * The {@code itemprop="name"} that belongs to the Product scope itself, not to a breadcrumb,
     * brand or offer nested in or placed before it (StarTech lists the category and brand first).
     */
    private static Optional<String> fromMicrodata(Document document) {
        for (Element product : document.select("[itemscope][itemtype]")) {
            if (!PRODUCT_ITEMTYPE.matcher(product.attr("itemtype").trim()).find()) {
                continue;
            }
            for (Element name : product.select("[itemprop=name]")) {
                if (name != product && owningScope(name) == product) {
                    Optional<String> value = name.hasAttr("content") ? text(name, "content") : text(name);
                    if (value.isPresent()) {
                        return value;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Element owningScope(Element element) {
        for (Element parent = element.parent(); parent != null; parent = parent.parent()) {
            if (parent.hasAttr("itemscope")) {
                return parent;
            }
        }
        return null;
    }

    private static Optional<String> text(Element element) {
        return element == null ? Optional.empty() : Optional.of(element.text()).filter(s -> !s.isBlank());
    }

    private static Optional<String> text(Element element, String attribute) {
        return element == null ? Optional.empty() : Optional.of(element.attr(attribute)).filter(s -> !s.isBlank());
    }

    // ---- Cleaning ------------------------------------------------------------------------

    static String clean(String name, Set<String> siteKeys) {
        String cleaned = stripSeoTails(stripSiteAndCategory(name, siteKeys)).trim();
        return cleaned.isEmpty() ? name : cleaned;
    }

    private static String stripSiteAndCategory(String name, Set<String> siteKeys) {
        List<String> parts = new ArrayList<>();
        List<String> separators = new ArrayList<>();
        Matcher matcher = SEPARATOR.matcher(name);
        int start = 0;
        while (matcher.find()) {
            parts.add(name.substring(start, matcher.start()));
            separators.add(matcher.group());
            start = matcher.end();
        }
        parts.add(name.substring(start));
        if (parts.size() < 2) {
            return name;
        }

        String siteSeparator = null;
        if (isSite(parts.getFirst(), siteKeys)) {
            parts.removeFirst();
            siteSeparator = separators.removeFirst();
        } else if (isSite(parts.getLast(), siteKeys)) {
            parts.removeLast();
            siteSeparator = separators.removeLast();
        }

        // "Amazon.com: <name> : Electronics", "Amazon.com | <name> | Chukka": a page title that
        // names the shop often names the department too, after the same kind of separator.
        if (siteSeparator != null && parts.size() > 1) {
            String last = parts.getLast();
            String lastSeparator = separators.getLast();
            String kept = String.join("", parts.subList(0, parts.size() - 1));
            if (spaced(lastSeparator) && lastSeparator.strip().equals(siteSeparator.strip())
                    && looksLikeCategory(last) && kept.length() > last.length()) {
                parts.removeLast();
                separators.removeLast();
            }
        }

        StringBuilder joined = new StringBuilder(parts.getFirst());
        for (int i = 1; i < parts.size(); i++) {
            joined.append(separators.get(i - 1)).append(parts.get(i));
        }
        return joined.toString();
    }

    /** A pipe, or a separator with space before it: "Star Wars: Episode IV" is one name, not name and category. */
    private static boolean spaced(String separator) {
        return separator.contains("|") || Character.isWhitespace(separator.charAt(0));
    }

    private static boolean looksLikeCategory(String part) {
        String trimmed = part.trim();
        return !trimmed.isEmpty()
                && trimmed.length() <= MAX_CATEGORY_LENGTH
                && trimmed.split("\\s+").length <= MAX_CATEGORY_WORDS
                && trimmed.chars().noneMatch(Character::isDigit); // "256GB", "08": a variant, not a department
    }

    private static boolean isSite(String part, Set<String> siteKeys) {
        String key = key(part);
        return !key.isEmpty() && siteKeys.contains(key);
    }

    private static String stripSeoTails(String name) {
        String stripped = PRICE_IN_COUNTRY.matcher(name).replaceFirst("");
        stripped = AT_BEST_PRICE.matcher(stripped).replaceFirst("");
        Matcher buy = (stripped.equals(name) ? BUY_ONLINE_STRICT : BUY_ONLINE).matcher(stripped);
        return buy.matches() ? buy.group(1) : stripped;
    }

    // ---- Site names ----------------------------------------------------------------------

    /**
     * Ways the page may name its shop, as {@link #key}s: {@code og:site_name}, the host
     * ("amazon.com", "startech.com.bd") and the host's distinctive labels ("amazon", "startech").
     */
    static Set<String> siteKeys(Document document) {
        Set<String> keys = new HashSet<>();
        Element siteName = document.selectFirst("meta[property=\"og:site_name\"]");
        if (siteName != null) {
            keys.add(key(siteName.attr("content")));
        }
        String host = host(document.location());
        if (host != null) {
            host = host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            keys.add(key(host));
            String[] labels = host.split("\\.");
            for (int i = 0; i < labels.length - 1; i++) {
                if (!GENERIC_HOST_LABELS.contains(labels[i])) {
                    keys.add(key(labels[i]));
                }
            }
        }
        keys.removeIf(key -> key.length() < 3);
        return keys;
    }

    private static String host(String location) {
        try {
            return location == null || location.isBlank() ? null : URI.create(location).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** "Amazon.com" and "amazon.com" both become "amazoncom"; "Star Tech" becomes "startech". */
    private static String key(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String collapseWhitespace(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
