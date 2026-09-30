package com.pricewatch.scraper;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Downloads a web page. Kept separate from the extractors so those can be tested with plain HTML. */
@Component
public class PageFetcher {

    private final int timeoutMs;
    private final String userAgent;

    public PageFetcher(
            @Value("${pricewatch.scraper.timeout-ms:10000}") int timeoutMs,
            @Value("${pricewatch.scraper.user-agent:Mozilla/5.0 (compatible; PriceWatch/1.0)}") String userAgent) {
        this.timeoutMs = timeoutMs;
        this.userAgent = userAgent;
    }

    public Document fetch(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(userAgent)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(timeoutMs)
                    .followRedirects(true)
                    .get();
        } catch (HttpStatusException e) {
            throw new ScrapeFailedException("The store answered with HTTP " + e.getStatusCode()
                    + ". It may be blocking automated requests.", e);
        } catch (UnsupportedMimeTypeException e) {
            throw new ScrapeFailedException("That URL is not a web page (" + e.getMimeType() + ").", e);
        } catch (IOException e) {
            throw new ScrapeFailedException("Could not load the page: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ScrapeFailedException("That does not look like a valid URL.", e);
        }
    }
}
