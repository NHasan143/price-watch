package com.pricewatch.scraper;

import java.math.BigDecimal;

/** A price read from a page. {@code currency} is an ISO 4217 code, or null when unknown. */
public record ExtractedPrice(BigDecimal price, String currency) {
}
