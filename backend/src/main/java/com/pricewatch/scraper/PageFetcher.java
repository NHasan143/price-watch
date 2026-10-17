package com.pricewatch.scraper;

import com.pricewatch.scraper.ScrapeFailedException.Reason;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Downloads a web page. Kept separate from the extractors so those can be tested with plain HTML.
 *
 * <p>Failures that may clear up on their own (timeouts, network errors, HTTP 429 and 5xx) are tried
 * again with exponential backoff, honouring the shop's {@code Retry-After} header. A shop that
 * refuses automated access (HTTP 401/403/451 or a bot check page) is not retried: asking again right
 * away would not help and makes a block more likely to stick.
 *
 * <p>Redirects are followed here rather than by Jsoup, so {@link AddressGuard} checks every hop: a public
 * link that redirects to the server's own network is refused before anything is sent there.
 */
@Component
public class PageFetcher {

    private static final Logger log = LoggerFactory.getLogger(PageFetcher.class);
    private static final int MAX_REDIRECTS = 10;
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);

    /** Waits between attempts; replaced in tests so they run instantly. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private record Attempt(Document page, ScrapeFailedException failure, Long retryAfterMs) {
        static Attempt success(Document page) {
            return new Attempt(page, null, null);
        }

        static Attempt failed(Reason reason, String message) {
            return new Attempt(null, new ScrapeFailedException(reason, message), null);
        }
    }

    private final int timeoutMs;
    private final String userAgent;
    private final int maxAttempts;
    private final long initialBackoffMs;
    private final long maxBackoffMs;
    private final Sleeper sleeper;
    private final AddressGuard guard;

    @Autowired
    public PageFetcher(
            @Value("${pricewatch.scraper.timeout-ms:10000}") int timeoutMs,
            @Value("${pricewatch.scraper.user-agent:Mozilla/5.0 (compatible; PriceWatch/1.0)}") String userAgent,
            @Value("${pricewatch.scraper.retry.max-attempts:3}") int maxAttempts,
            @Value("${pricewatch.scraper.retry.initial-backoff-ms:1000}") long initialBackoffMs,
            @Value("${pricewatch.scraper.retry.max-backoff-ms:8000}") long maxBackoffMs,
            AddressGuard guard) {
        this(timeoutMs, userAgent, maxAttempts, initialBackoffMs, maxBackoffMs, Thread::sleep, guard);
    }

    PageFetcher(int timeoutMs, String userAgent, int maxAttempts, long initialBackoffMs, long maxBackoffMs,
                Sleeper sleeper, AddressGuard guard) {
        this.timeoutMs = timeoutMs;
        this.userAgent = userAgent;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoffMs = initialBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
        this.sleeper = sleeper;
        this.guard = guard;
    }

    /** @throws ScrapeFailedException with the {@link Reason} of the last failed attempt */
    public Document fetch(String url) {
        for (int attempt = 1; ; attempt++) {
            Attempt result = attempt(url);
            if (result.page() != null) {
                return result.page();
            }
            ScrapeFailedException failure = result.failure();
            if (failure.getReason() != Reason.TEMPORARY) {
                throw failure;
            }
            long delay = result.retryAfterMs() != null ? result.retryAfterMs() : backoff(attempt);
            if (attempt >= maxAttempts || delay > maxBackoffMs) {
                // A Retry-After longer than we are willing to wait is left to the next follow-up check.
                throw attempt == 1 ? failure : new ScrapeFailedException(Reason.TEMPORARY,
                        failure.getMessage() + " Tried " + attempt + " times.", failure.getCause());
            }
            log.info("Fetching {} failed ({}); trying again in {} ms", url, failure.getMessage(), delay);
            try {
                sleeper.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw failure;
            }
        }
    }

    /** 1 s, 2 s, 4 s, ... capped at the maximum. */
    private long backoff(int attempt) {
        long delay = initialBackoffMs << Math.min(attempt - 1, 20);
        return Math.min(delay, maxBackoffMs);
    }

    private Attempt attempt(String url) {
        Connection.Response response;
        try {
            response = download(url);
        } catch (ScrapeFailedException e) {
            return new Attempt(null, e, null);
        } catch (IllegalArgumentException e) {
            return Attempt.failed(Reason.INVALID, "That does not look like a valid URL.");
        } catch (SocketTimeoutException | HttpTimeoutException e) {
            long seconds = Math.max(1, Math.round(timeoutMs / 1000.0));
            return Attempt.failed(Reason.TEMPORARY,
                    "The shop did not respond within " + seconds + (seconds == 1 ? " second." : " seconds."));
        } catch (UnknownHostException e) {
            return Attempt.failed(Reason.TEMPORARY,
                    "Could not find the server " + e.getMessage() + ". Check the link and your internet connection.");
        } catch (IOException e) {
            return Attempt.failed(Reason.TEMPORARY, "Could not connect to the shop (" + e.getMessage() + ").");
        }

        int status = response.statusCode();
        Document page = parse(response);
        if (status >= 200 && status < 300) {
            String type = response.contentType();
            if (type != null && !type.contains("html") && !type.contains("xml") && !type.startsWith("text/")) {
                return Attempt.failed(Reason.INVALID, "That link is not a web page (" + type + ").");
            }
            if (page == null) {
                return Attempt.failed(Reason.TEMPORARY, "The shop sent a page that could not be read.");
            }
            return Attempt.success(page);
        }

        // An error page that is really a bot check (Cloudflare answers 403 or 503) is a block, not an outage.
        var botCheck = BotCheckDetector.detect(page);
        if (botCheck.isPresent()) {
            return Attempt.failed(Reason.BLOCKED, BotCheckDetector.describe(botCheck.get()));
        }
        return switch (status) {
            case 401, 403, 451 -> Attempt.failed(Reason.BLOCKED,
                    "The shop refused the request (HTTP " + status + ").");
            case 404, 410 -> Attempt.failed(Reason.PAGE_GONE,
                    "The product page no longer exists (HTTP " + status + "). The shop may have removed it; "
                            + "check that the link still opens the product.");
            case 429 -> new Attempt(null, new ScrapeFailedException(Reason.TEMPORARY,
                    "The shop is limiting how often it can be checked (HTTP 429)."), retryAfterMs(response));
            default -> status == 408 || status >= 500
                    ? new Attempt(null, new ScrapeFailedException(Reason.TEMPORARY,
                            "The shop's server had a problem (HTTP " + status + ")."), retryAfterMs(response))
                    : Attempt.failed(Reason.INVALID, "The shop answered with HTTP " + status + ".");
        };
    }

    /** Downloads the page, following redirects one hop at a time and checking each address first. */
    private Connection.Response download(String url) throws IOException {
        Map<String, String> cookies = new HashMap<>();
        Connection connection = Jsoup.connect(url);
        for (int redirects = 0; ; redirects++) {
            guard.check(connection.request().url()); // the URL exactly as Jsoup will connect to it
            Connection.Response response = connection
                    .userAgent(userAgent)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .cookies(cookies)
                    .timeout(timeoutMs)
                    .followRedirects(false)
                    // Read error responses too: their status and body say whether the shop is blocking us.
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .execute();
            String location = response.header("Location");
            if (!REDIRECTS.contains(response.statusCode()) || location == null || location.isBlank()) {
                return response;
            }
            if (redirects >= MAX_REDIRECTS) {
                throw new ScrapeFailedException(Reason.INVALID, "The link redirects too many times.");
            }
            cookies.putAll(response.cookies()); // shops often set a session cookie on the way
            try {
                connection = Jsoup.connect(StringUtil.resolve(response.url(), location.trim()).toExternalForm());
            } catch (MalformedURLException e) {
                throw new ScrapeFailedException(Reason.INVALID, "The shop redirected to an address that is not valid.");
            }
        }
    }

    private static Document parse(Connection.Response response) {
        try {
            return response.parse();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Reads {@code Retry-After} as seconds or an HTTP date; null when absent or unreadable. */
    static Long retryAfterMs(Connection.Response response) {
        String value = response.header("Retry-After");
        if (value == null || value.isBlank()) {
            return null;
        }
        value = value.trim();
        try {
            return Math.max(0, Long.parseLong(value) * 1000);
        } catch (NumberFormatException notSeconds) {
            try {
                ZonedDateTime when = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ROOT));
                return Math.max(0, Duration.between(ZonedDateTime.now(when.getZone()), when).toMillis());
            } catch (DateTimeParseException notADate) {
                return null;
            }
        }
    }
}
