package com.pricewatch.scraper;

/** Thrown when a page cannot be downloaded or no price can be read from it. */
public class ScrapeFailedException extends RuntimeException {

    public ScrapeFailedException(String message) {
        super(message);
    }

    public ScrapeFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
