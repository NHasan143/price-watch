package com.pricewatch.scraper;

import com.pricewatch.scraper.ScrapeFailedException.Reason;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the fetcher against a real local HTTP server that answers with a scripted sequence of responses. */
class PageFetcherTest {

    private record Reply(int status, String contentType, String body, Map<String, String> headers, long delayMs) {
        static Reply of(int status, String body) {
            return new Reply(status, "text/html; charset=utf-8", body, Map.of(), 0);
        }
    }

    private static final String PRODUCT = "<html><head><title>Lamp</title></head><body>49.90</body></html>";

    private final Deque<Reply> replies = new ArrayDeque<>();
    private final List<Long> sleeps = new ArrayList<>();
    private int requests;
    private HttpServer server;
    private final ExecutorService threads = Executors.newCachedThreadPool();
    private String url;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::answer);
        server.setExecutor(threads); // a retry must not queue behind a deliberately slow reply
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/p/1";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        threads.shutdownNow();
    }

    private synchronized Reply next() {
        requests++;
        return replies.size() > 1 ? replies.poll() : replies.peek();
    }

    private void answer(HttpExchange exchange) throws IOException {
        Reply reply = next();
        try {
            Thread.sleep(reply.delayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", reply.contentType());
        reply.headers().forEach(exchange.getResponseHeaders()::add);
        exchange.sendResponseHeaders(reply.status(), body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        } catch (IOException clientGaveUp) {
            // the fetcher timed out and closed the connection
        }
    }

    private PageFetcher fetcher(int timeoutMs) {
        return new PageFetcher(timeoutMs, "test", 3, 1000, 8000, sleeps::add);
    }

    private ScrapeFailedException failure() {
        try {
            fetcher(2000).fetch(url);
        } catch (ScrapeFailedException e) {
            return e;
        }
        throw new AssertionError("expected the fetch to fail");
    }

    @Test
    void returnsThePageWithoutRetrying() {
        replies.add(Reply.of(200, PRODUCT));

        assertThat(fetcher(2000).fetch(url).title()).isEqualTo("Lamp");
        assertThat(requests).isEqualTo(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void retriesServerErrorsWithDoublingBackoff() {
        replies.add(Reply.of(503, "busy"));
        replies.add(Reply.of(502, "bad gateway"));
        replies.add(Reply.of(200, PRODUCT));

        assertThat(fetcher(2000).fetch(url).title()).isEqualTo("Lamp");
        assertThat(requests).isEqualTo(3);
        assertThat(sleeps).containsExactly(1000L, 2000L);
    }

    @Test
    void givesUpAfterTheLastAttemptAndSaysHowOftenItTried() {
        replies.add(Reply.of(500, "oops"));

        ScrapeFailedException e = failure();

        assertThat(e.getReason()).isEqualTo(Reason.TEMPORARY);
        assertThat(e.getMessage()).isEqualTo("The shop's server had a problem (HTTP 500). Tried 3 times.");
        assertThat(requests).isEqualTo(3);
    }

    @Test
    void waitsAsLongAsRetryAfterAsks() {
        replies.add(new Reply(429, "text/html", "slow down", Map.of("Retry-After", "3"), 0));
        replies.add(Reply.of(200, PRODUCT));

        fetcher(2000).fetch(url);

        assertThat(sleeps).containsExactly(3000L);
    }

    @Test
    void leavesALongRetryAfterToTheNextScheduledAttempt() {
        replies.add(new Reply(429, "text/html", "slow down", Map.of("Retry-After", "600"), 0));

        ScrapeFailedException e = failure();

        assertThat(e.getReason()).isEqualTo(Reason.TEMPORARY);
        assertThat(e.getMessage()).contains("limiting how often");
        assertThat(requests).isEqualTo(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void retriesTimeouts() {
        replies.add(new Reply(200, "text/html", PRODUCT, Map.of(), 1500));
        replies.add(Reply.of(200, PRODUCT));

        assertThat(fetcher(300).fetch(url).title()).isEqualTo("Lamp");
        assertThat(sleeps).containsExactly(1000L);
    }

    @Test
    void explainsATimeoutInPlainWords() {
        replies.add(new Reply(200, "text/html", PRODUCT, Map.of(), 1500));

        assertThatThrownBy(() -> fetcher(300).fetch(url))
                .hasMessage("The shop did not respond within 1 second. Tried 3 times.");
    }

    @Test
    void doesNotRetryWhenTheShopRefusesTheRequest() {
        replies.add(Reply.of(403, "<html><body>Forbidden</body></html>"));

        ScrapeFailedException e = failure();

        assertThat(e.getReason()).isEqualTo(Reason.BLOCKED);
        assertThat(e.getMessage()).isEqualTo("The shop refused the request (HTTP 403).");
        assertThat(requests).isEqualTo(1);
    }

    @Test
    void treatsABotCheckServedAsAServerErrorAsABlock() {
        replies.add(Reply.of(503, """
                <html><head><title>Just a moment...</title></head>
                <body><form id="challenge-form"></form></body></html>
                """));

        ScrapeFailedException e = failure();

        assertThat(e.getReason()).isEqualTo(Reason.BLOCKED);
        assertThat(e.getMessage()).contains("Cloudflare bot check");
        assertThat(requests).isEqualTo(1);
    }

    @Test
    void reportsARemovedPageWithoutRetrying() {
        replies.add(Reply.of(404, "<html><body>Not found</body></html>"));

        ScrapeFailedException e = failure();

        assertThat(e.getReason()).isEqualTo(Reason.PAGE_GONE);
        assertThat(requests).isEqualTo(1);
    }

    @Test
    void rejectsLinksThatAreNotWebPages() {
        replies.add(new Reply(200, "application/pdf", "%PDF-1.7", Map.of(), 0));

        assertThat(failure().getReason()).isEqualTo(Reason.INVALID);
    }

    @Test
    void rejectsMalformedUrls() {
        assertThatThrownBy(() -> fetcher(2000).fetch("not a url"))
                .isInstanceOf(ScrapeFailedException.class)
                .extracting("reason").isEqualTo(Reason.INVALID);
    }
}
