package com.pricewatch.scraper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.NodeFilter;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Works out whether a product can be bought, on any page, whichever way its price was found.
 * Sources, most reliable first:
 * <ol>
 *   <li>structured data: schema.org JSON-LD (including product variants), microdata and
 *       {@code product:availability} meta tags;</li>
 *   <li>stock widgets: elements whose id or class names them as the stock status (Amazon's
 *       {@code #availability}, WooCommerce's {@code p.stock}, Magento's {@code .stock}) and whose
 *       text starts with a stock phrase ("In stock", "Only 3 left", "Currently unavailable");</li>
 *   <li>the buy button: an enabled "Add to cart" or "Buy now" means in stock, "Sold out" or
 *       "Notify me" means out of stock, "Pre-order" means pre-order;</li>
 *   <li>short status text near the product, when all of it agrees.</li>
 * </ol>
 * Related-product carousels, reviews, menus, footers and size/colour pickers are skipped, so a
 * sold-out badge on another product or on one size does not mark this product sold out. When the
 * page gives no clear answer the result is {@link Availability#UNKNOWN}: a wrong "sold out" would
 * later send a false back-in-stock alert, so guessing is worse than not knowing.
 */
final class AvailabilityDetector {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_DEPTH = 8;
    private static final List<String> META_KEYS = List.of("product:availability", "og:availability");

    /** Look this many elements past the product title for buttons and status text. */
    private static final int MAX_ELEMENTS_AFTER_TITLE = 2500;
    private static final int MAX_STATUS_TEXT = 120;
    private static final int MAX_BUTTON_TEXT = 40;

    /** Containers that describe other products, or things other than this product's stock. */
    private static final Pattern EXCLUDED_CONTAINER = Pattern.compile(
            "related|recommend|similar|carousel|upsell|up-sell|cross-?sell|recently|also-?(bought|viewed)|sponsor"
                    + "|bundle|compare|review|rating|faq|question|newsletter|subscribe|cookie|modal|popup|drawer"
                    + "|mini-?cart|cart-?drawer|breadcrumb|megamenu|mega-menu|swatch|variant|size-?(picker|selector|chart)",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> EXCLUDED_TAGS =
            Set.of("nav", "footer", "aside", "select", "option", "script", "style", "noscript", "template", "svg");

    private static final Pattern STOCK_WIDGET = Pattern.compile(
            "availability|stock", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATUS_LABEL = Pattern.compile(
            "^(availability|stock status|stock|status|in stock status)\\s*[:\\-–]\\s*", Pattern.CASE_INSENSITIVE);

    private static final Pattern OUT_TEXT = Pattern.compile(
            "^(out of stock|out-of-stock|sold out|sold-out|stock out|stockout|not in stock|currently unavailable"
                    + "|temporarily unavailable|temporarily out of stock|no longer available|this item is no longer available"
                    + "|item unavailable|product unavailable|unavailable\\s*[.!]?$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IN_TEXT = Pattern.compile(
            "^(in stock|in-stock|instock|available\\s*(now)?\\s*[.!]?$|available to ship|only \\d+ left"
                    + "|\\d+ (items? |pcs |pieces |units )?(in stock|available|left)|usually ships|ships (today|tomorrow|within)"
                    + "|ready to ship)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PREORDER_TEXT = Pattern.compile(
            "^(pre-?order|available for pre-?order|available on back-?order|back-?order)", Pattern.CASE_INSENSITIVE);

    private static final Pattern BUTTON_IN = Pattern.compile(
            "^(add to (cart|bag|basket|trolley|order)|add to shopping (cart|bag)|buy( it)? now|buy\\s*$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BUTTON_OUT = Pattern.compile(
            "^(sold out|out of stock|stock out|unavailable|currently unavailable|notify me|notify when available"
                    + "|email me when (it's |it is )?(back|available))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BUTTON_PREORDER = Pattern.compile("^pre-?order", Pattern.CASE_INSENSITIVE);

    private AvailabilityDetector() {
    }

    static Availability detect(Document document) {
        Availability structured = fromStructuredData(document);
        return structured != Availability.UNKNOWN ? structured : fromPage(document);
    }

    // ---- Structured data ----------------------------------------------------------------

    /** JSON-LD Product offers, then microdata, then meta tags. */
    static Availability fromStructuredData(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                Availability found = findProduct(MAPPER.readTree(script.data()), 0);
                if (found != Availability.UNKNOWN) {
                    return found;
                }
            } catch (JsonProcessingException e) {
                // malformed block: try the next one
            }
        }
        Element microdata = document.selectFirst("[itemprop=availability]");
        if (microdata != null) {
            // usually <link itemprop="availability" href="https://schema.org/InStock">
            String raw = microdata.hasAttr("href") ? microdata.attr("href")
                    : microdata.hasAttr("content") ? microdata.attr("content") : microdata.text();
            Availability availability = Availability.parse(raw);
            if (availability != Availability.UNKNOWN) {
                return availability;
            }
        }
        for (String key : META_KEYS) {
            Element meta = document.selectFirst("meta[property=\"" + key + "\"], meta[name=\"" + key + "\"]");
            Availability availability = Availability.parse(meta == null ? null : meta.attr("content"));
            if (availability != Availability.UNKNOWN) {
                return availability;
            }
        }
        return Availability.UNKNOWN;
    }

    /** The first Product (or ProductGroup) that says anything about its offers. */
    private static Availability findProduct(JsonNode node, int depth) {
        if (node == null || depth > MAX_DEPTH || !node.isContainerNode()) {
            return Availability.UNKNOWN;
        }
        if (node.isObject() && node.path("@type").toString().contains("Product")) {
            Set<Availability> seen = EnumSet.noneOf(Availability.class);
            collectOffers(node, seen, depth);
            Availability combined = combine(seen);
            if (combined != Availability.UNKNOWN) {
                return combined;
            }
        }
        for (JsonNode child : node) {
            Availability found = findProduct(child, depth + 1);
            if (found != Availability.UNKNOWN) {
                return found;
            }
        }
        return Availability.UNKNOWN;
    }

    /** Availability of every offer of a product and of its variants ({@code hasVariant}). */
    private static void collectOffers(JsonNode product, Set<Availability> seen, int depth) {
        if (depth > MAX_DEPTH) {
            return;
        }
        readOffer(product.get("offers"), seen, depth + 1);
        JsonNode variants = product.get("hasVariant");
        if (variants != null) {
            for (JsonNode variant : variants.isArray() ? variants : List.of(variants)) {
                collectOffers(variant, seen, depth + 1);
            }
        }
    }

    private static void readOffer(JsonNode offer, Set<Availability> seen, int depth) {
        if (offer == null || depth > MAX_DEPTH) {
            return;
        }
        if (offer.isArray()) {
            offer.forEach(child -> readOffer(child, seen, depth + 1));
            return;
        }
        if (offer.isObject()) {
            JsonNode availability = offer.get("availability");
            if (availability != null && availability.isTextual()) {
                seen.add(Availability.parse(availability.asText()));
            }
            readOffer(offer.get("offers"), seen, depth + 1);
        }
    }

    /** Availability of a list of offers (one per size or colour, say): in stock if any of them is. */
    static Availability ofOffers(JsonNode offers) {
        Set<Availability> seen = EnumSet.noneOf(Availability.class);
        readOffer(offers, seen, 0);
        return combine(seen);
    }

    /** One buyable variant is enough to call the product in stock. */
    private static Availability combine(Set<Availability> seen) {
        if (seen.contains(Availability.IN_STOCK)) {
            return Availability.IN_STOCK;
        }
        if (seen.contains(Availability.PREORDER)) {
            return Availability.PREORDER;
        }
        return seen.contains(Availability.OUT_OF_STOCK) ? Availability.OUT_OF_STOCK : Availability.UNKNOWN;
    }

    // ---- Visible page ---------------------------------------------------------------------

    /** What one pass over the product area found. */
    private static final class Signals implements NodeFilter {
        private final Element title;
        private boolean afterTitle;
        private int counted;
        Availability widget = Availability.UNKNOWN;
        final Set<Availability> buttons = EnumSet.noneOf(Availability.class);
        final Set<Availability> texts = EnumSet.noneOf(Availability.class);

        Signals(Element title) {
            this.title = title;
            this.afterTitle = title == null;
        }

        @Override
        public FilterResult head(Node node, int depth) {
            if (!(node instanceof Element element)) {
                return FilterResult.CONTINUE;
            }
            if (isExcluded(element)) {
                return FilterResult.SKIP_ENTIRELY;
            }
            if (element == title) {
                afterTitle = true;
            }
            // Stock widgets are specific enough to trust anywhere on the page.
            if (widget == Availability.UNKNOWN && isStockWidget(element)) {
                widget = statusText(element.text());
            }
            if (afterTitle && counted++ < MAX_ELEMENTS_AFTER_TITLE) {
                Availability button = buttonSignal(element);
                if (button != Availability.UNKNOWN) {
                    buttons.add(button);
                    return FilterResult.SKIP_CHILDREN;
                }
                if (element.ownText().length() > 0) {
                    Availability text = statusText(element.text());
                    if (text != Availability.UNKNOWN) {
                        texts.add(text);
                    }
                }
            }
            return FilterResult.CONTINUE;
        }
    }

    static Availability fromPage(Document document) {
        Element body = document.body();
        if (body == null) {
            return Availability.UNKNOWN;
        }
        Element scope = body.selectFirst("main, [role=main]");
        if (scope == null) {
            scope = body;
        }
        Signals signals = new Signals(scope.selectFirst("h1"));
        scope.filter(signals);

        if (signals.widget != Availability.UNKNOWN) {
            return signals.widget;
        }
        // An enabled "Add to cart" means it can be bought, whatever a badge elsewhere says.
        Availability fromButtons = combine(signals.buttons);
        if (fromButtons != Availability.UNKNOWN) {
            return fromButtons;
        }
        // Loose text only counts when it all agrees.
        return signals.texts.size() == 1 ? signals.texts.iterator().next() : Availability.UNKNOWN;
    }

    private static boolean isExcluded(Element element) {
        if (EXCLUDED_TAGS.contains(element.normalName())
                || element.hasAttr("hidden")
                || "true".equals(element.attr("aria-hidden"))) {
            return true;
        }
        String names = element.id() + " " + element.className();
        return !names.isBlank() && EXCLUDED_CONTAINER.matcher(names).find();
    }

    private static boolean isStockWidget(Element element) {
        String names = element.id() + " " + element.className();
        return !names.isBlank() && STOCK_WIDGET.matcher(names).find() && element.text().length() <= MAX_STATUS_TEXT;
    }

    /** Reads "In stock", "Availability: Only 2 left", "Stock Out", "Currently unavailable." and the like. */
    static Availability statusText(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty() || text.length() > MAX_STATUS_TEXT) {
            return Availability.UNKNOWN;
        }
        Availability direct = matchStatus(text);
        if (direct != Availability.UNKNOWN) {
            return direct;
        }
        String unlabelled = STATUS_LABEL.matcher(text).replaceFirst("");
        return unlabelled.equals(text) ? Availability.UNKNOWN : matchStatus(unlabelled);
    }

    private static Availability matchStatus(String text) {
        if (OUT_TEXT.matcher(text).find()) {
            return Availability.OUT_OF_STOCK;
        }
        if (PREORDER_TEXT.matcher(text).find()) {
            return Availability.PREORDER;
        }
        return IN_TEXT.matcher(text).find() ? Availability.IN_STOCK : Availability.UNKNOWN;
    }

    private static Availability buttonSignal(Element element) {
        String tag = element.normalName();
        boolean button = tag.equals("button")
                || (tag.equals("input") && element.attr("type").toLowerCase(Locale.ROOT).matches("submit|button"))
                || element.attr("role").equals("button");
        if (!button) {
            return Availability.UNKNOWN;
        }
        String label = (tag.equals("input") ? element.attr("value") : element.text()).trim();
        if (label.isEmpty()) {
            label = element.attr("aria-label").trim();
        }
        if (label.isEmpty() || label.length() > MAX_BUTTON_TEXT) {
            return Availability.UNKNOWN;
        }
        if (BUTTON_OUT.matcher(label).find()) {
            return Availability.OUT_OF_STOCK;
        }
        if (BUTTON_PREORDER.matcher(label).find()) {
            return Availability.PREORDER;
        }
        // A disabled "Add to cart" usually just waits for a size or colour to be picked.
        boolean disabled = element.hasAttr("disabled") || "true".equals(element.attr("aria-disabled"));
        return !disabled && BUTTON_IN.matcher(label).find() ? Availability.IN_STOCK : Availability.UNKNOWN;
    }
}
