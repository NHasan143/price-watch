package com.pricewatch.scraper;

/** Thrown when a page cannot be downloaded or no price can be read from it. */
public class ScrapeFailedException extends RuntimeException {

    /** Why a check failed, which decides whether trying again soon can help. */
    public enum Reason {
        /** The shop refused automated access (HTTP 403, a bot check page). Retrying soon will not help. */
        BLOCKED,
        /** Timeout, network error, rate limit or server error: likely to work if tried again later. */
        TEMPORARY,
        /** The product page no longer exists (HTTP 404 or 410). */
        PAGE_GONE,
        /** The page loaded, but no price could be read from it. */
        NO_PRICE,
        /** The link or CSS selector itself is not usable. */
        INVALID
    }

    private final Reason reason;

    public ScrapeFailedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ScrapeFailedException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
