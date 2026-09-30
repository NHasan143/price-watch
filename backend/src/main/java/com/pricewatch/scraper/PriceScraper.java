package com.pricewatch.scraper;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Downloads a product page and asks each {@link PriceExtractor} (in {@code @Order}) to read the
 * price until one succeeds. Also collects the page title and image for display.
 */
@Service
public class PriceScraper {

    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_IMAGE_URL_LENGTH = 2048;

    private final PageFetcher fetcher;
    private final List<PriceExtractor> extractors;

    public PriceScraper(PageFetcher fetcher, List<PriceExtractor> extractors) {
        this.fetcher = fetcher;
        this.extractors = extractors;
    }

    /**
     * @param cssSelector optional selector for the price element; null/blank uses structured data
     * @throws ScrapeFailedException if the page cannot be loaded or contains no readable price
     */
    public ScrapeResult scrape(String url, String cssSelector) {
        ScrapeTarget target = new ScrapeTarget(url, cssSelector);
        Document document = fetcher.fetch(url);

        ExtractedPrice extracted = extractors.stream()
                .filter(extractor -> extractor.supports(target))
                .map(extractor -> extractor.extract(document, target))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new ScrapeFailedException(target.hasSelector()
                        ? "Nothing matching the CSS selector '" + cssSelector + "' contained a readable price."
                        : "Could not find a price on that page. It may load prices with JavaScript; "
                        + "try adding a CSS selector for the price element."));

        return new ScrapeResult(extracted.price(), extracted.currency(), title(document), image(document));
    }

    private static String title(Document document) {
        Element og = document.selectFirst("meta[property=\"og:title\"]");
        String title = og != null ? og.attr("content") : document.title();
        title = title == null ? "" : title.trim();
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
    }

    private static String image(Document document) {
        Element og = document.selectFirst("meta[property=\"og:image\"]");
        if (og == null) {
            return null;
        }
        String url = og.absUrl("content");
        if (url.isBlank() || url.length() > MAX_IMAGE_URL_LENGTH) {
            return null;
        }
        return url;
    }
}
