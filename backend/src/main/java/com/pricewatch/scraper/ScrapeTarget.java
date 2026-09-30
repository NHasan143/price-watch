package com.pricewatch.scraper;

/** What to scrape: a page URL plus an optional CSS selector pointing at the price element. */
public record ScrapeTarget(String url, String cssSelector) {

    public boolean hasSelector() {
        return cssSelector != null && !cssSelector.isBlank();
    }
}
