package com.pricewatch.product;

import java.math.BigDecimal;
import java.time.Instant;

public record PricePointResponse(Instant checkedAt, BigDecimal price) {
}
