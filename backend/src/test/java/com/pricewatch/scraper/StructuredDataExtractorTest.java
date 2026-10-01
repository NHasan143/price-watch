package com.pricewatch.scraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredDataExtractorTest {

    private final StructuredDataExtractor extractor = new StructuredDataExtractor(new ObjectMapper());
    private final ScrapeTarget target = new ScrapeTarget("https://shop.test/p/1", null);

    private Optional<ExtractedPrice> extract(String html) {
        Document document = Jsoup.parse(html, "https://shop.test/p/1");
        return extractor.extract(document, target);
    }

    @Test
    void readsJsonLdOffer() {
        Optional<ExtractedPrice> result = extract("""
                <html><head><script type="application/ld+json">
                {"@context":"https://schema.org","@type":"Product","name":"Lamp",
                 "offers":{"@type":"Offer","price":"49.90","priceCurrency":"EUR"}}
                </script></head><body></body></html>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("49.90"));
        assertThat(result.get().currency()).isEqualTo("EUR");
    }

    @Test
    void readsNumericPriceInsideGraphAndOfferList() {
        Optional<ExtractedPrice> result = extract("""
                <script type="application/ld+json">
                {"@graph":[{"@type":"WebSite"},
                  {"@type":"Product","offers":[{"@type":"Offer","price":1299.5,"priceCurrency":"usd"}]}]}
                </script>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("1299.50"));
        assertThat(result.get().currency()).isEqualTo("USD");
    }

    @Test
    void readsAggregateOfferLowPrice() {
        Optional<ExtractedPrice> result = extract("""
                <script type="application/ld+json">
                {"@type":"Product","offers":{"@type":"AggregateOffer","lowPrice":"20","highPrice":"30","priceCurrency":"GBP"}}
                </script>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("20"));
    }

    @Test
    void skipsBrokenJsonLdAndFallsBackToMetaTags() {
        Optional<ExtractedPrice> result = extract("""
                <head>
                <script type="application/ld+json">{ not json </script>
                <meta property="product:price:amount" content="15.25">
                <meta property="product:price:currency" content="CAD">
                </head>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("15.25"));
        assertThat(result.get().currency()).isEqualTo("CAD");
    }

    @Test
    void readsMicrodata() {
        Optional<ExtractedPrice> result = extract("""
                <div itemscope itemtype="https://schema.org/Offer">
                  <span itemprop="priceCurrency" content="BDT">BDT</span>
                  <span itemprop="price">1,250.00</span>
                </div>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("1250"));
        assertThat(result.get().currency()).isEqualTo("BDT");
    }

    @Test
    void prefersMicrodataOverStaleMetaTagPrice() {
        // Trimmed from startech.com.bd during a sale: the <head> meta tag keeps the regular price.
        Optional<ExtractedPrice> result = extract("""
                <head>
                <meta property="product:price:amount" content="15700.0000" />
                <meta property="product:price:currency" content="BDT" />
                </head>
                <body>
                <td class="product-info-data product-price"><ins>13,500৳</ins><del>15,700৳</del></td>
                <div itemscope itemtype="http://schema.org/Product">
                  <meta itemprop="priceCurrency" content="BDT" />
                  <meta itemprop="price" content="13500.0000" />
                </div>
                </body>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("13500"));
        assertThat(result.get().currency()).isEqualTo("BDT");
    }

    @Test
    void prefersSalePriceMetaTag() {
        Optional<ExtractedPrice> result = extract("""
                <head>
                <meta property="product:price:amount" content="100.00">
                <meta property="product:sale_price:amount" content="79.00">
                <meta property="product:price:currency" content="USD">
                </head>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("79"));
        assertThat(result.get().currency()).isEqualTo("USD");
    }

    @Test
    void readsAvailabilityFromJsonLdOffer() {
        Optional<ExtractedPrice> result = extract("""
                <script type="application/ld+json">
                {"@type":"Product","offers":{"@type":"Offer","price":"49.90","priceCurrency":"EUR",
                 "availability":"https://schema.org/OutOfStock"}}
                </script>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().availability()).isEqualTo(Availability.OUT_OF_STOCK);
    }

    @Test
    void readsAvailabilityFromTheOfferWhosePriceWasUsed() {
        Optional<ExtractedPrice> result = extract("""
                <script type="application/ld+json">
                {"@type":"Product","offers":[
                  {"@type":"Offer","price":"20","priceCurrency":"USD","availability":"http://schema.org/PreOrder"},
                  {"@type":"Offer","price":"25","priceCurrency":"USD","availability":"http://schema.org/InStock"}]}
                </script>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(result.get().availability()).isEqualTo(Availability.PREORDER);
    }

    @Test
    void readsOfferAvailabilityWhenPriceIsInPriceSpecification() {
        Optional<ExtractedPrice> result = extract("""
                <script type="application/ld+json">
                {"@type":"Product","offers":{"@type":"Offer","availability":"InStock",
                 "priceSpecification":{"@type":"UnitPriceSpecification","price":"9.99","priceCurrency":"GBP"}}}
                </script>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("9.99"));
        assertThat(result.get().availability()).isEqualTo(Availability.IN_STOCK);
    }

    @Test
    void readsAvailabilityFromMicrodataLink() {
        Optional<ExtractedPrice> result = extract("""
                <div itemscope itemtype="https://schema.org/Offer">
                  <meta itemprop="priceCurrency" content="USD">
                  <span itemprop="price">19.99</span>
                  <link itemprop="availability" href="https://schema.org/SoldOut">Sold out
                </div>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().availability()).isEqualTo(Availability.OUT_OF_STOCK);
    }

    @Test
    void readsAvailabilityFromMetaTag() {
        // Trimmed from startech.com.bd: the availability meta tag holds plain text.
        Optional<ExtractedPrice> result = extract("""
                <head>
                <meta property="product:price:amount" content="15700.0000" />
                <meta property="product:price:currency" content="BDT" />
                <meta property="product:availability" content="In Stock" />
                </head>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().availability()).isEqualTo(Availability.IN_STOCK);
    }

    @Test
    void readsSoldOutProductThatStillListsAPrice() {
        // Trimmed from startech.com.bd/benq-gw2490-fhd-monitor: sold out, but the price is still published.
        Optional<ExtractedPrice> result = extract("""
                <head>
                <meta property="product:availability" content="Out Of Stock" />
                <meta property="product:price:amount" content="16000.0000" />
                <meta property="product:price:currency" content="BDT" />
                </head>
                <body>
                <div class="short-description" itemprop="offers" itemscope itemtype="http://schema.org/Offer">
                  <link itemprop="availability" href="http://schema.org/OutOfStock"/>
                  <link itemprop="itemCondition" href="http://schema.org/NewCondition">
                  <meta itemprop="priceCurrency" content="BDT" />
                  <meta itemprop="price" content="16000.0000" />
                </div>
                </body>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("16000"));
        assertThat(result.get().availability()).isEqualTo(Availability.OUT_OF_STOCK);
    }

    @Test
    void fallsBackToPageAvailabilityWhenOfferHasNone() {
        Optional<ExtractedPrice> result = extract("""
                <head>
                <script type="application/ld+json">
                {"@type":"Product","offers":{"@type":"Offer","price":"49.90","priceCurrency":"EUR"}}
                </script>
                <meta property="product:availability" content="out of stock">
                </head>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().availability()).isEqualTo(Availability.OUT_OF_STOCK);
    }

    @Test
    void availabilityIsUnknownWhenPageDoesNotSay() {
        Optional<ExtractedPrice> result = extract("""
                <meta property="product:price:amount" content="10.00">
                <meta property="product:availability" content="Call for price">
                """);

        assertThat(result).isPresent();
        assertThat(result.get().availability()).isEqualTo(Availability.UNKNOWN);
    }

    @Test
    void returnsEmptyWhenPageHasNoPrice() {
        assertThat(extract("<html><body><p>Hello</p></body></html>")).isEmpty();
    }

    @Test
    void onlySupportsTargetsWithoutSelector() {
        assertThat(extractor.supports(target)).isTrue();
        assertThat(extractor.supports(new ScrapeTarget("https://shop.test", ".price"))).isFalse();
    }
}
