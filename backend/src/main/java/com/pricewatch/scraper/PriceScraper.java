package com.pricewatch.scraper;

import com.pricewatch.scraper.ScrapeFailedException.Reason;
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
 *
 * <p>When no price turns up, the failure says why: the shop blocked us (an HTTP refusal or a bot
 * check page, in either the plain download or the browser), the page could not be loaded, or the
 * page loaded but had no readable price.
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
     * @throws ScrapeFailedException if the page cannot be loaded or contains no readable price; its
     *         {@link Reason} tells blocked shops apart from temporary failures
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

        if (fetchFailure != null && fetchFailure.getReason() != Reason.BLOCKED
                && fetchFailure.getReason() != Reason.TEMPORARY) {
            throw fetchFailure; // a bad link or a removed page: a browser will not do better
        }

        Optional<ExtractedPrice> extracted = document == null ? Optional.empty() : extract(document, target);
        Document rendered = null;
        if (extracted.isEmpty()) {
            log.debug("No price in the plain download of {}; trying headless Chrome", url);
            rendered = renderer.render(url, page -> extract(page, target).isPresent()).orElse(null);
            if (rendered != null) {
                Optional<ExtractedPrice> fromRendered = extract(rendered, target);
                if (fromRendered.isPresent() || document == null) {
                    document = rendered;
                    extracted = fromRendered;
                }
            }
        }

        if (extracted.isEmpty()) {
            throw noPrice(target, fetchFailure, document, rendered);
        }

        ExtractedPrice price = extracted.get();
        // Read availability from the whole page whichever extractor found the price (a CSS selector,
        // the visible price), so stock status works on shops without structured data too.
        Availability availability = price.availability() != Availability.UNKNOWN
                ? price.availability()
                : AvailabilityDetector.detect(document);
        return new ScrapeResult(price.price(), price.currency(), availability, title(document), imageFinder.find(document));
    }

    private static ScrapeFailedException noPrice(
            ScrapeTarget target, ScrapeFailedException fetchFailure, Document document, Document rendered) {
        Optional<String> botCheck = BotCheckDetector.detect(rendered).or(() -> BotCheckDetector.detect(document));
        if (botCheck.isPresent()) {
            return new ScrapeFailedException(Reason.BLOCKED, BotCheckDetector.describe(botCheck.get()));
        }
        // The browser may have got a page the shop refused to send to the plain download, but with no
        // price on it the refusal is still the best explanation.
        if (fetchFailure != null && (document == null || fetchFailure.getReason() == Reason.BLOCKED)) {
            return fetchFailure;
        }
        return new ScrapeFailedException(Reason.NO_PRICE, target.hasSelector()
                ? "Nothing matching the CSS selector '" + target.cssSelector() + "' contained a readable price."
                : "Could not find a price on that page. Check that the link opens a single product; "
                + "if it does, add a CSS selector for the price element under Advanced.");
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
