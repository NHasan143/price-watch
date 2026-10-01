package com.pricewatch.scraper;

import java.util.Locale;

/** Whether a product can be bought right now, as published by the shop. */
public enum Availability {
    IN_STOCK,
    OUT_OF_STOCK,
    /** Can be ordered now but ships later (schema.org PreOrder, PreSale, BackOrder). */
    PREORDER,
    UNKNOWN;

    /**
     * Reads a schema.org ItemAvailability value ({@code "https://schema.org/InStock"}, {@code "InStock"})
     * or the free text shops put in meta tags ({@code "In Stock"}, {@code "out of stock"}).
     */
    public static Availability parse(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        String value = raw.trim();
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        return switch (value.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "")) {
            case "instock", "instoreonly", "onlineonly", "limitedavailability", "available" -> IN_STOCK;
            case "outofstock", "soldout", "discontinued", "unavailable" -> OUT_OF_STOCK;
            case "preorder", "presale", "backorder" -> PREORDER;
            default -> UNKNOWN;
        };
    }
}
