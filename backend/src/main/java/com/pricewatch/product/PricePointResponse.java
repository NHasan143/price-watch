package com.pricewatch.product;

import com.pricewatch.scraper.Availability;

import java.math.BigDecimal;
import java.time.Instant;

public record PricePointResponse(Instant checkedAt, BigDecimal price, Availability availability) {
}
