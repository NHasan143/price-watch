package com.pricewatch.scraper;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VisiblePriceExtractorTest {

    private final VisiblePriceExtractor extractor = new VisiblePriceExtractor();
    private final ScrapeTarget target = new ScrapeTarget("https://shop.test/p/1", null);

    private Optional<ExtractedPrice> extract(String html) {
        return extractor.extract(Jsoup.parse(html, "https://shop.test/p/1"), target);
    }

    @Test
    void readsPriceShownUnderTheTitle() {
        // Trimmed from applegadgetsbd.com, which publishes no structured data.
        Optional<ExtractedPrice> result = extract("""
                <header><span class="cart">৳0</span></header>
                <h1 class="text-2xl font-semibold">Apple AirPods 5</h1>
                <div class="flex gap-2">
                  <div class="flex items-center">
                    <p class="text-2xl font-bold text-gray-900">৳22,499</p>
                    <span class="text-xs">(Booking Money)</span>
                  </div>
                  <p><span class="font-semibold">Code:</span> SKU-109182</p>
                </div>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("22499"));
        assertThat(result.get().currency()).isEqualTo("BDT");
    }

    @Test
    void skipsCrossedOutAndInstalmentPrices() {
        Optional<ExtractedPrice> result = extract("""
                <h1>Espresso Machine</h1>
                <div class="prices">
                  <del>€699,00</del>
                  <span class="old-price">€649,00</span>
                  <span class="emi">€49,00 / month</span>
                  <span class="price">€599,00</span>
                </div>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("599.00"));
        assertThat(result.get().currency()).isEqualTo("EUR");
    }

    @Test
    void readsPriceSplitAcrossElements() {
        Optional<ExtractedPrice> result = extract("""
                <h1>Desk Lamp</h1>
                <div class="price"><span class="symbol">£</span><span class="amount">215.00</span></div>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("215.00"));
        assertThat(result.get().currency()).isEqualTo("GBP");
    }

    @Test
    void readsTakaWrittenAsTk() {
        Optional<ExtractedPrice> result = extract("<h1>Router</h1><p>Tk 3,450</p>");

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("3450"));
        assertThat(result.get().currency()).isEqualTo("BDT");
    }

    @Test
    void ignoresNumbersWithoutACurrency() {
        assertThat(extract("<h1>Monitor 24 inch</h1><p>Code: SKU 1200</p><p>Rated 4.5 by 120 buyers</p>")).isEmpty();
    }

    @Test
    void ignoresPricesBeforeTheTitle() {
        assertThat(extract("<div class=\"cart\">$120.00</div><h1>Headphones</h1><p>Out of stock</p>")).isEmpty();
    }

    @Test
    void returnsEmptyWithoutTitle() {
        assertThat(extract("<div><p>$19.99</p></div>")).isEmpty();
    }

    @Test
    void onlySupportsTargetsWithoutSelector() {
        assertThat(extractor.supports(target)).isTrue();
        assertThat(extractor.supports(new ScrapeTarget("https://shop.test", ".price"))).isFalse();
    }
}
