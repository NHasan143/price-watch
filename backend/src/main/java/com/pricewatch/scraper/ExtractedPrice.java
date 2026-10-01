package com.pricewatch.scraper;

import java.math.BigDecimal;

/**
 * A price read from a page. {@code currency} is an ISO 4217 code, or null when unknown.
 * {@code availability} is {@link Availability#UNKNOWN} when the page does not say.
 */
public record ExtractedPrice(BigDecimal price, String currency, Availability availability) {

    public ExtractedPrice(BigDecimal price, String currency) {
        this(price, currency, Availability.UNKNOWN);
    }

    public ExtractedPrice withAvailability(Availability availability) {
        return new ExtractedPrice(price, currency, availability);
    }
}
