package com.pricewatch.scraper;

import org.jsoup.nodes.Document;

import java.util.Optional;
import java.util.function.Predicate;

/** Loads a page in a real browser, so prices that JavaScript puts on the page can be read. */
public interface PageRenderer {

    /**
     * Opens {@code url} and returns the rendered page as soon as {@code ready} accepts it, or the
     * last state of the page when time runs out. Empty when rendering is turned off or failed.
     */
    Optional<Document> render(String url, Predicate<Document> ready);
}
