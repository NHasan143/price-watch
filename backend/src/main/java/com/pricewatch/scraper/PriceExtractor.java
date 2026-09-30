package com.pricewatch.scraper;

import org.jsoup.nodes.Document;

import java.util.Optional;

/**
 * Strategy for pulling a price out of an HTML page. Add a new implementation (as a Spring
 * {@code @Component}) to support another kind of store; {@link PriceScraper} picks the first
 * extractor that {@linkplain #supports supports} the target and finds a price.
 */
public interface PriceExtractor {

    boolean supports(ScrapeTarget target);

    Optional<ExtractedPrice> extract(Document document, ScrapeTarget target);
}
