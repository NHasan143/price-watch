package com.pricewatch.scraper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Default strategy: most online shops publish their price in machine-readable form for search
 * engines. Tries, in order: schema.org JSON-LD, schema.org microdata ({@code itemprop="price"}),
 * and Open Graph / product meta tags. Microdata comes before meta tags because it sits next to the
 * visible price, while shops often leave the regular (pre-discount) price in the {@code <head>} meta
 * tags during a sale.
 *
 * <p>Availability is read from the same offer as the price when it has one; otherwise from the
 * page's microdata ({@code itemprop="availability"}) or meta tags ({@code product:availability}).
 */
@Component
@Order(2)
public class StructuredDataExtractor implements PriceExtractor {

    private static final Logger log = LoggerFactory.getLogger(StructuredDataExtractor.class);
    private static final int MAX_DEPTH = 8;

    private static final List<String> META_PRICE_KEYS =
            List.of("product:sale_price:amount", "product:price:amount", "og:price:amount");
    private static final List<String> META_CURRENCY_KEYS =
            List.of("product:sale_price:currency", "product:price:currency", "og:price:currency");
    private static final List<String> META_AVAILABILITY_KEYS = List.of("product:availability", "og:availability");

    private final ObjectMapper mapper;

    public StructuredDataExtractor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(ScrapeTarget target) {
        return !target.hasSelector();
    }

    @Override
    public Optional<ExtractedPrice> extract(Document document, ScrapeTarget target) {
        return fromJsonLd(document)
                .or(() -> fromMicrodata(document))
                .or(() -> fromMetaTags(document))
                .map(found -> found.availability() == Availability.UNKNOWN
                        ? found.withAvailability(pageAvailability(document))
                        : found);
    }

    /** Availability published outside the offer the price came from. */
    private static Availability pageAvailability(Document document) {
        Element microdata = document.selectFirst("[itemprop=availability]");
        if (microdata != null) {
            // usually <link itemprop="availability" href="https://schema.org/InStock">
            String raw = microdata.hasAttr("href") ? microdata.attr("href") : valueOf(microdata);
            Availability availability = Availability.parse(raw);
            if (availability != Availability.UNKNOWN) {
                return availability;
            }
        }
        for (String key : META_AVAILABILITY_KEYS) {
            Availability availability = Availability.parse(metaContent(document, key));
            if (availability != Availability.UNKNOWN) {
                return availability;
            }
        }
        return Availability.UNKNOWN;
    }

    // ---- JSON-LD -------------------------------------------------------------------------

    private Optional<ExtractedPrice> fromJsonLd(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            String json = script.data();
            if (json == null || json.isBlank()) {
                continue;
            }
            try {
                Optional<ExtractedPrice> found = findOffer(mapper.readTree(json), 0);
                if (found.isPresent()) {
                    return found;
                }
            } catch (JsonProcessingException e) {
                log.debug("Ignoring malformed JSON-LD block: {}", e.getOriginalMessage());
            }
        }
        return Optional.empty();
    }

    /** Walks the JSON looking for an object that has an "offers" property. */
    private Optional<ExtractedPrice> findOffer(JsonNode node, int depth) {
        if (node == null || depth > MAX_DEPTH) {
            return Optional.empty();
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<ExtractedPrice> found = findOffer(child, depth + 1);
                if (found.isPresent()) {
                    return found;
                }
            }
            return Optional.empty();
        }
        if (!node.isObject()) {
            return Optional.empty();
        }
        JsonNode offers = node.get("offers");
        if (offers != null) {
            Optional<ExtractedPrice> found = readOffer(offers, depth + 1);
            if (found.isPresent()) {
                return found;
            }
        }
        for (JsonNode child : node) {
            if (child.isContainerNode()) {
                Optional<ExtractedPrice> found = findOffer(child, depth + 1);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /** Reads an Offer, AggregateOffer, list of offers, or PriceSpecification. */
    private Optional<ExtractedPrice> readOffer(JsonNode offer, int depth) {
        if (offer == null || depth > MAX_DEPTH) {
            return Optional.empty();
        }
        if (offer.isArray()) {
            for (JsonNode child : offer) {
                Optional<ExtractedPrice> found = readOffer(child, depth + 1);
                if (found.isPresent()) {
                    return found;
                }
            }
            return Optional.empty();
        }
        if (!offer.isObject()) {
            return Optional.empty();
        }

        String currency = PriceParser.normalizeCurrency(text(offer, "priceCurrency"));
        Availability availability = Availability.parse(text(offer, "availability"));
        for (String field : List.of("price", "lowPrice")) {
            Optional<BigDecimal> price = PriceParser.parse(text(offer, field));
            if (price.isPresent()) {
                return Optional.of(new ExtractedPrice(price.get(), currency, availability));
            }
        }

        Optional<ExtractedPrice> nested = readOffer(offer.get("priceSpecification"), depth + 1);
        if (nested.isEmpty()) {
            nested = readOffer(offer.get("offers"), depth + 1);
        }
        return nested.map(p -> new ExtractedPrice(
                p.price(),
                p.currency() != null ? p.currency() : currency,
                p.availability() != Availability.UNKNOWN ? p.availability() : availability));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isNumber() ? value.decimalValue().toPlainString() : value.asText();
    }

    // ---- Microdata -----------------------------------------------------------------------

    private Optional<ExtractedPrice> fromMicrodata(Document document) {
        Element priceElement = document.selectFirst("[itemprop=price]");
        if (priceElement == null) {
            return Optional.empty();
        }
        String raw = valueOf(priceElement);
        Optional<BigDecimal> price = PriceParser.parse(raw);
        if (price.isEmpty()) {
            return Optional.empty();
        }
        Element currencyElement = document.selectFirst("[itemprop=priceCurrency]");
        String currency = currencyElement == null ? null : PriceParser.normalizeCurrency(valueOf(currencyElement));
        if (currency == null) {
            currency = PriceParser.detectCurrency(raw).orElse(null);
        }
        return Optional.of(new ExtractedPrice(price.get(), currency));
    }

    private static String valueOf(Element element) {
        return element.hasAttr("content") ? element.attr("content") : element.text();
    }

    // ---- Meta tags -----------------------------------------------------------------------

    private Optional<ExtractedPrice> fromMetaTags(Document document) {
        for (String key : META_PRICE_KEYS) {
            String content = metaContent(document, key);
            Optional<BigDecimal> price = PriceParser.parse(content);
            if (price.isPresent()) {
                String currency = null;
                for (String currencyKey : META_CURRENCY_KEYS) {
                    currency = PriceParser.normalizeCurrency(metaContent(document, currencyKey));
                    if (currency != null) {
                        break;
                    }
                }
                return Optional.of(new ExtractedPrice(price.get(), currency));
            }
        }
        return Optional.empty();
    }

    private static String metaContent(Document document, String key) {
        Element meta = document.selectFirst("meta[property=\"" + key + "\"], meta[name=\"" + key + "\"]");
        return meta == null ? null : meta.attr("content");
    }
}
