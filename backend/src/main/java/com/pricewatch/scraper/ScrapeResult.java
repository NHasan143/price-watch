package com.pricewatch.scraper;

import java.math.BigDecimal;

/** Everything learned from one visit to a product page. */
public record ScrapeResult(BigDecimal price, String currency, String title, String imageUrl) {
}
