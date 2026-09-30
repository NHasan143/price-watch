package com.pricewatch.product;

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
        Instant lastCheckedAt,
        String lastError,
        Instant createdAt) {
}
