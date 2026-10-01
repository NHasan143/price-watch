package com.pricewatch.scraper;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Downloads a product page and asks each {@link PriceExtractor} (in {@code @Order}) to read the
 * price until one succeeds. When the plain download has no readable price, or the shop refuses it,
 * the page is loaded again in a headless browser ({@link PageRenderer}) so prices that JavaScript
 * puts on the page can be read too. Also collects the page title and image for display.
 */
@Service
public class PriceScraper {

    private static final Logger log = LoggerFactory.getLogger(PriceScraper.class);
    private static final int MAX_TITLE_LENGTH = 255;

    private final PageFetcher fetcher;
    private final PageRenderer renderer;
    private final ProductImageFinder imageFinder;
    private final List<PriceExtractor> extractors;

    public PriceScraper(
            PageFetcher fetcher, PageRenderer renderer, ProductImageFinder imageFinder, List<PriceExtractor> extractors) {
        this.fetcher = fetcher;
        this.renderer = renderer;
        this.imageFinder = imageFinder;
        this.extractors = extractors;
    }

    /**
     * @param cssSelector optional selector for the price element; null/blank uses automatic detection
     * @throws ScrapeFailedException if the page cannot be loaded or contains no readable price
     */
    public ScrapeResult scrape(String url, String cssSelector) {
        ScrapeTarget target = new ScrapeTarget(url, cssSelector);

        Document document = null;
        ScrapeFailedException fetchFailure = null;
        try {
            document = fetcher.fetch(url);
        } catch (ScrapeFailedException e) {
            fetchFailure = e;
        }

        Optional<ExtractedPrice> extracted = document == null ? Optional.empty() : extract(document, target);
        if (extracted.isEmpty()) {
            log.debug("No price in the plain download of {}; trying headless Chrome", url);
            Optional<Document> rendered = renderer.render(url, page -> extract(page, target).isPresent());
            if (rendered.isPresent()) {
                Optional<ExtractedPrice> fromRendered = extract(rendered.get(), target);
                if (fromRendered.isPresent() || document == null) {
                    document = rendered.get();
                    extracted = fromRendered;
                }
            }
        }

        if (extracted.isEmpty()) {
            if (document == null && fetchFailure != null) {
                throw fetchFailure;
            }
            throw new ScrapeFailedException(target.hasSelector()
                    ? "Nothing matching the CSS selector '" + cssSelector + "' contained a readable price."
                    : "Could not find a price on that page. Check that the link opens a single product; "
                    + "if it does, add a CSS selector for the price element under Advanced.");
        }

        ExtractedPrice price = extracted.get();
        return new ScrapeResult(
                price.price(), price.currency(), price.availability(), title(document), imageFinder.find(document));
    }

    private Optional<ExtractedPrice> extract(Document document, ScrapeTarget target) {
        return extractors.stream()
                .filter(extractor -> extractor.supports(target))
                .map(extractor -> extractor.extract(document, target))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static String title(Document document) {
        Element og = document.selectFirst("meta[property=\"og:title\"]");
        String title = og != null ? og.attr("content") : document.title();
        title = title == null ? "" : title.trim();
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
    }
}
