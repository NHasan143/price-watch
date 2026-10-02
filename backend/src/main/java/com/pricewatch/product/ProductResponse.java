package com.pricewatch.product;

import com.pricewatch.scraper.Availability;
import com.pricewatch.scraper.ScrapeFailedException.Reason;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String url,
        String imageUrl,
        String currency,
        BigDecimal targetPrice,
        BigDecimal currentPrice,
        BigDecimal lowestPrice,
        BigDecimal highestPrice,
        boolean belowTarget,
        /** False for a guest's product: read once, not re-checked, no alerts until they sign up. */
        boolean tracked,
        Availability availability,
        Instant lastCheckedAt,
        String lastError,
        Reason lastErrorReason,
        int failedChecks,
        Instant nextRetryAt,
        Instant createdAt) {
}
