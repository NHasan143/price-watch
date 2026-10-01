package com.pricewatch.scraper;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityDetectorTest {

    private static Availability detect(String html) {
        return AvailabilityDetector.detect(Jsoup.parse(html, "https://shop.test/p/1"));
    }

    // ---- structured data -------------------------------------------------------------------

    @Test
    void readsJsonLdAnywhereOnThePage() {
        assertThat(detect("""
                <script type="application/ld+json">{"@graph":[{"@type":"BreadcrumbList"},
                  {"@type":"Product","offers":{"@type":"Offer","availability":"https://schema.org/OutOfStock"}}]}</script>
                """)).isEqualTo(Availability.OUT_OF_STOCK);
    }

    @Test
    void oneBuyableVariantMakesTheProductInStock() {
        assertThat(detect("""
                <script type="application/ld+json">{"@type":"ProductGroup","hasVariant":[
                  {"@type":"Product","offers":{"availability":"http://schema.org/OutOfStock"}},
                  {"@type":"Product","offers":{"availability":"http://schema.org/InStock"}}]}</script>
                """)).isEqualTo(Availability.IN_STOCK);
    }

    @Test
    void structuredDataWinsOverPageText() {
        assertThat(detect("""
                <meta property="product:availability" content="out of stock">
                <h1>Lamp</h1><button>Add to cart</button>
                """)).isEqualTo(Availability.OUT_OF_STOCK);
    }

    // ---- stock widgets ---------------------------------------------------------------------

    @Test
    void readsAmazonAvailabilityBox() {
        // Trimmed from amazon.com/dp/B00593T928: Amazon publishes no structured availability.
        assertThat(detect("""
                <div id="centerCol"><h1 id="title"><span id="productTitle">UGG Men's Neumel Boot</span></h1></div>
                <div id="availability" class="a-section a-spacing-base">
                  <span class="a-size-medium a-color-success"> In Stock </span></div>
                <input id="add-to-cart-button" type="submit" value="Add to Cart">
                """)).isEqualTo(Availability.IN_STOCK);
        assertThat(detect("""
                <h1 id="title">MSI Codex</h1>
                <div id="availability"><span class="a-color-price a-text-bold">Currently unavailable.</span>
                <span>We don't know when or if this item will be back in stock.</span></div>
                """)).isEqualTo(Availability.OUT_OF_STOCK);
        assertThat(detect("""
                <h1>Lamp</h1><div id="availability"><span>Only 3 left in stock - order soon.</span></div>
                """)).isEqualTo(Availability.IN_STOCK);
    }

    @Test
    void readsWooCommerceAndMagentoStockLabels() {
        assertThat(detect("""
                <div class="summary entry-summary"><h1 class="product_title">Lamp</h1>
                <p class="stock out-of-stock">Out of stock</p></div>
                """)).isEqualTo(Availability.OUT_OF_STOCK);
        assertThat(detect("""
                <div class="product-info-main"><h1>Lamp</h1>
                <div class="stock available" title="Availability"><span>In stock</span></div></div>
                """)).isEqualTo(Availability.IN_STOCK);
    }

    @Test
    void readsLabelledStatusLikeBangladeshiShops() {
        assertThat(detect("""
                <h1>BenQ GW2490</h1>
                <table><tr><td class="product-info-label">Status</td>
                <td class="product-info-data product-status">Stock Out</td></tr></table>
                """)).isEqualTo(Availability.OUT_OF_STOCK);
        assertThat(detect("<h1>Lamp</h1><span class=\"stock-status\">Availability: In Stock</span>"))
                .isEqualTo(Availability.IN_STOCK);
    }

    // ---- buy button ------------------------------------------------------------------------

    @Test
    void readsTheBuyButton() {
        assertThat(detect("<h1>Lamp</h1><form action=\"/cart/add\"><button type=\"submit\">Add to cart</button></form>"))
                .isEqualTo(Availability.IN_STOCK);
        assertThat(detect("<h1>Lamp</h1><button type=\"submit\" disabled>Sold out</button>"))
                .isEqualTo(Availability.OUT_OF_STOCK);
        assertThat(detect("<h1>Lamp</h1><button>Notify me when available</button>"))
                .isEqualTo(Availability.OUT_OF_STOCK);
        assertThat(detect("<h1>Lamp</h1><button>Pre-order now</button>")).isEqualTo(Availability.PREORDER);
    }

    @Test
    void aDisabledAddToCartIsNotASignal() {
        // usually waiting for a size to be picked
        assertThat(detect("<h1>Boots</h1><button disabled>Add to cart</button>")).isEqualTo(Availability.UNKNOWN);
    }

    @Test
    void anEnabledAddToCartBeatsASoldOutSize() {
        assertThat(detect("""
                <h1>Boots</h1>
                <fieldset><label>8 <span>Sold out</span></label><label>9</label></fieldset>
                <button name="add">Add to cart</button>
                """)).isEqualTo(Availability.IN_STOCK);
    }

    // ---- not fooled ------------------------------------------------------------------------

    @Test
    void ignoresSoldOutBadgesOnOtherProducts() {
        assertThat(detect("""
                <main><h1>Desk Lamp</h1><p>A warm light for late nights.</p>
                <section class="related-products"><div class="card">Floor Lamp <span class="badge">Sold out</span></div>
                <button>Notify me</button></section></main>
                """)).isEqualTo(Availability.UNKNOWN);
    }

    @Test
    void ignoresNewsletterAndFooterButtons() {
        assertThat(detect("""
                <h1>Desk Lamp</h1>
                <footer><form class="newsletter"><button>Notify me</button></form><p>Sold out? We restock weekly.</p></footer>
                """)).isEqualTo(Availability.UNKNOWN);
    }

    @Test
    void ignoresTextThatOnlyStartsLikeAStockWord() {
        assertThat(detect("""
                <h1>Desk Lamp</h1><p>Available colours: black, white</p><p>Ships in its own gift box</p>
                <p>Discontinued</p>
                """)).isEqualTo(Availability.UNKNOWN);
    }

    @Test
    void conflictingLooseTextGivesNoAnswer() {
        assertThat(detect("<h1>Lamp</h1><p>In stock</p><p>Out of stock</p>")).isEqualTo(Availability.UNKNOWN);
    }

    @Test
    void returnsUnknownForAPageWithoutAnySignal() {
        assertThat(detect("<h1>Lamp</h1><p>A warm light.</p><span class=\"price\">$20</span>"))
                .isEqualTo(Availability.UNKNOWN);
    }
}
