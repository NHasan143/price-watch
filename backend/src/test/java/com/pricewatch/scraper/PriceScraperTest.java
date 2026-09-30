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
        PageFetcher fetcher = new PageFetcher(1000, "test") {
            @Override
            public Document fetch(String url) {
                if (plainHtml == null) {
                    throw new ScrapeFailedException("The store answered with HTTP 403. It may be blocking automated requests.");
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
        return new PriceScraper(fetcher, renderer, extractors);
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
                .hasMessageContaining("HTTP 403");
    }

    @Test
    void explainsWhenNoPriceIsFoundEvenAfterRendering() {
        assertThatThrownBy(() -> scraper(PLAIN_HTML, PLAIN_HTML).scrape(URL, null))
                .isInstanceOf(ScrapeFailedException.class)
                .hasMessageContaining("Could not find a price on that page");
    }

    @Test
    void appliesTheUserSelectorToTheRenderedPage() {
        ScrapeResult result = scraper(PLAIN_HTML, RENDERED_HTML).scrape(URL, ".pdp-price_type_deleted");

        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("7500"));
    }
}
