package com.pricewatch.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CssSelectorExtractorTest {

    private final CssSelectorExtractor extractor = new CssSelectorExtractor();
    private final Document document = Jsoup.parse("""
            <html><body>
              <span id="old">$199.00</span>
              <span id="now" class="sale">€ 1.299,00</span>
            </body></html>
            """);

    @Test
    void readsPriceAndCurrencyFromSelectedElement() {
        ScrapeTarget target = new ScrapeTarget("https://shop.test", "#now");

        ExtractedPrice result = extractor.extract(document, target).orElseThrow();

        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("1299.00"));
        assertThat(result.currency()).isEqualTo("EUR");
    }

    @Test
    void returnsEmptyWhenNothingMatches() {
        assertThat(extractor.extract(document, new ScrapeTarget("https://shop.test", ".missing"))).isEmpty();
    }

    @Test
    void invalidSelectorBecomesScrapeFailure() {
        ScrapeTarget target = new ScrapeTarget("https://shop.test", "div[");

        assertThatThrownBy(() -> extractor.extract(document, target))
                .isInstanceOf(ScrapeFailedException.class);
    }

    @Test
    void onlySupportsTargetsWithSelector() {
        assertThat(extractor.supports(new ScrapeTarget("https://shop.test", ".price"))).isTrue();
        assertThat(extractor.supports(new ScrapeTarget("https://shop.test", "  "))).isFalse();
        assertThat(extractor.supports(new ScrapeTarget("https://shop.test", null))).isFalse();
    }
}
