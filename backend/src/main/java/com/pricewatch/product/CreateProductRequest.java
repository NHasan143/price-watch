package com.pricewatch.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductRequest(
        @Size(max = 255, message = "must be at most 255 characters")
        String name,

        @NotBlank(message = "is required")
        @Size(max = 2048, message = "must be at most 2048 characters")
        @Pattern(regexp = "(?i)^https?://\\S+$", message = "must be a valid http(s) URL")
        String url,

        @NotNull(message = "is required")
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 10, fraction = 2, message = "must have at most 10 digits and 2 decimals")
        BigDecimal targetPrice,

        @Size(max = 255, message = "must be at most 255 characters")
        String cssSelector) {
}
