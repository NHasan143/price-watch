package com.pricewatch.scraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceScraperTest {

    private static final String URL = "https://shop.test/p/1";

    /** What the shop sends before JavaScript runs: title and JSON-LD, but no price. */
    private static final String PLAIN_HTML = """
            <html><head><meta property="og:title" content="Office Chair">
            <script type="application/ld+json">{"@type":"Product","name":"Office Chair","offers":{"@type":"Offer"}}</script>
            </head><body><h1>Office Chair</h1><div id="price"></div></body></html>
            """;

    /** The same page after JavaScript filled in the sale price (trimmed from daraz.com.bd). */
    private static final String RENDERED_HTML = """
            <html><head><meta property="og:title" content="Office Chair"></head><body>
            <h1>Office Chair</h1>
            <div id="price"><span class="pdp-price">৳ 4,000</span>
            <span class="pdp-price pdp-price_type_deleted">৳ 7,500</span></div>
            </body></html>
            """;

    private final AtomicInteger renders = new AtomicInteger();

    private PriceScraper scraper(String plainHtml, String renderedHtml) {
        return scraper(plainHtml == null ? BLOCKED : null, plainHtml, renderedHtml);
    }

    private static final ScrapeFailedException BLOCKED = new ScrapeFailedException(
            ScrapeFailedException.Reason.BLOCKED, "The shop refused the request (HTTP 403).");

    private PriceScraper scraper(ScrapeFailedException fetchFailure, String plainHtml, String renderedHtml) {
        PageFetcher fetcher = new PageFetcher(1000, "test", 1, 0, 0, millis -> { }) {
            @Override
            public Document fetch(String url) {
                if (fetchFailure != null) {
                    throw fetchFailure;
                }
                return Jsoup.parse(plainHtml, url);
            }
        };
        PageRenderer renderer = (url, ready) -> {
            renders.incrementAndGet();
            return Optional.ofNullable(renderedHtml).map(html -> Jsoup.parse(html, url));
        };
        List<PriceExtractor> extractors = List.of(
                new CssSelectorExtractor(), new StructuredDataExtractor(new ObjectMapper()), new VisiblePriceExtractor());
        return new PriceScraper(fetcher, renderer, new ProductNameFinder(new ObjectMapper()),
                new ProductImageFinder(new ObjectMapper()), extractors);
    }

    @Test
    void usesThePlainDownloadWhenItHasAPrice() {
        ScrapeResult result = scraper(RENDERED_HTML, null).scrape(URL, null);

        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("4000"));
        assertThat(renders).hasValue(0);
    }

    @Test
    void rendersThePageWhenThePriceIsBuiltByJavaScript() {
        ScrapeResult result = scraper(PLAIN_HTML, RENDERED_HTML).scrape(URL, null);

        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("4000"));
        assertThat(result.currency()).isEqualTo("BDT");
        assertThat(result.title()).isEqualTo("Office Chair");
        assertThat(renders).hasValue(1);
    }

    @Test
    void rendersThePageWhenTheShopBlocksThePlainDownload() {
        ScrapeResult result = scraper(null, RENDERED_HTML).scrape(URL, null);

        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("4000"));
    }

    @Test
    void reportsTheOriginalFailureWhenTheBrowserCannotHelp() {
        assertThatThrownBy(() -> scraper(null, null).scrape(URL, null))
                .isInstanceOf(ScrapeFailedException.class)
                .hasMessageContaining("HTTP 403")
                .extracting("reason").isEqualTo(ScrapeFailedException.Reason.BLOCKED);
    }

    /** Cloudflare's interstitial, trimmed. */
    private static final String CLOUDFLARE_CHALLENGE = """
            <html><head><title>Just a moment...</title></head>
            <body><div id="challenge-running">Checking your browser before accessing shop.test.</div>
            <form id="challenge-form" action="/p/1?__cf_chl_f_tk=x" method="POST"></form></body></html>
            """;

    @Test
    void saysTheShopIsBlockingWhenTheBrowserLandsOnABotCheck() {
        assertThatThrownBy(() -> scraper(BLOCKED, null, CLOUDFLARE_CHALLENGE).scrape(URL, null))
                .isInstanceOf(ScrapeFailedException.class)
                .hasMessageContaining("Cloudflare bot check")
                .extracting("reason").isEqualTo(ScrapeFailedException.Reason.BLOCKED);
    }

    @Test
    void saysTheShopIsBlockingWhenThePlainDownloadIsABotCheck() {
        // Amazon answers 200 with a robot check instead of the product.
        String robotCheck = """
                <html><head><title>Robot Check</title></head><body>
                <form action="/errors/validateCaptcha"><input name="field-keywords"></form></body></html>
                """;

        assertThatThrownBy(() -> scraper(null, robotCheck, null).scrape(URL, null))
                .extracting("reason").isEqualTo(ScrapeFailedException.Reason.BLOCKED);
    }

    @Test
    void reportsBlockedWhenTheBrowserGetsAPageWithoutAPrice() {
        assertThatThrownBy(() -> scraper(BLOCKED, null, PLAIN_HTML).scrape(URL, null))
                .extracting("reason").isEqualTo(ScrapeFailedException.Reason.BLOCKED);
    }

    @Test
    void doesNotOpenTheBrowserForARemovedPage() {
        ScrapeFailedException gone = new ScrapeFailedException(
                ScrapeFailedException.Reason.PAGE_GONE, "The product page no longer exists (HTTP 404).");

        assertThatThrownBy(() -> scraper(gone, null, RENDERED_HTML).scrape(URL, null))
                .extracting("reason").isEqualTo(ScrapeFailedException.Reason.PAGE_GONE);
        assertThat(renders).hasValue(0);
    }

    @Test
    void readsThePriceWhenTheBrowserGetsPastATemporaryFailure() {
        ScrapeFailedException timeout = new ScrapeFailedException(
                ScrapeFailedException.Reason.TEMPORARY, "The shop did not respond within 10 seconds.");

        assertThat(scraper(timeout, null, RENDERED_HTML).scrape(URL, null).price())
                .isEqualByComparingTo(new BigDecimal("4000"));
    }

    @Test
    void explainsWhenNoPriceIsFoundEvenAfterRendering() {
        assertThatThrownBy(() -> scraper(PLAIN_HTML, PLAIN_HTML).scrape(URL, null))
                .isInstanceOf(ScrapeFailedException.class)
                .hasMessageContaining("Could not find a price on that page")
                .extracting("reason").isEqualTo(ScrapeFailedException.Reason.NO_PRICE);
    }

    @Test
    void readsStockStatusEvenWhenTheShopPublishesNoStructuredData() {
        String page = """
                <html><body><h1>Office Chair</h1><span class="price">৳ 4,000</span>
                <button type="submit" disabled>Sold out</button></body></html>
                """;

        ScrapeResult bySelector = scraper(page, null).scrape(URL, ".price");
        ScrapeResult byVisiblePrice = scraper(page, null).scrape(URL, null);

        assertThat(bySelector.availability()).isEqualTo(Availability.OUT_OF_STOCK);
        assertThat(byVisiblePrice.availability()).isEqualTo(Availability.OUT_OF_STOCK);
    }

    @Test
    void appliesTheUserSelectorToTheRenderedPage() {
        ScrapeResult result = scraper(PLAIN_HTML, RENDERED_HTML).scrape(URL, ".pdp-price_type_deleted");

        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("7500"));
    }
}
