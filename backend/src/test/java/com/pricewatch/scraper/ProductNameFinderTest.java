package com.pricewatch.scraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductNameFinderTest {

    private static final String AMAZON = "https://www.amazon.com/dp/B00593T928";
    private static final String STARTECH = "https://www.startech.com.bd/benq-gw2490-fhd-monitor";

    private final ProductNameFinder finder = new ProductNameFinder(new ObjectMapper());

    private String find(String html) {
        return find(html, "https://shop.test/p/1");
    }

    private String find(String html, String url) {
        return finder.find(Jsoup.parse(html, url));
    }

    // ---- Page titles: the issue's three examples ---------------------------------------------

    @Test
    void stripsAmazonsNameAndDepartmentAroundColons() {
        // Trimmed from amazon.com/dp/B0HCWCFHYR, without #productTitle.
        assertThat(find("""
                <title>Amazon.com: [2025] MSI Codex Z2 A8NVM-485US (AMD Ryzen 7 8700F, 64GB RAM, 2X 4TB NVMe SSD, \
                NVIDIA GeForce RTX 5060Ti, Windows 11) Gaming Desktop PC : Electronics</title>
                """, "https://www.amazon.com/dp/B0HCWCFHYR"))
                .isEqualTo("[2025] MSI Codex Z2 A8NVM-485US (AMD Ryzen 7 8700F, 64GB RAM, 2X 4TB NVMe SSD, "
                        + "NVIDIA GeForce RTX 5060Ti, Windows 11) Gaming Desktop PC");
        assertThat(find("<title>Amazon.com: msi Codex Z2 Gaming Desktop, AMD R7-8700F : Electronics</title>", AMAZON))
                .isEqualTo("msi Codex Z2 Gaming Desktop, AMD R7-8700F");
    }

    @Test
    void stripsAmazonsNameAndDepartmentAroundPipes() {
        // Trimmed from amazon.com/dp/B00593T928, without #productTitle.
        assertThat(find("<title>Amazon.com | UGG Men&#x27;s Neumel Boot, Chestnut, 08 | Chukka</title>", AMAZON))
                .isEqualTo("UGG Men's Neumel Boot, Chestnut, 08");
    }

    @Test
    void stripsPriceInBangladesh() {
        // Trimmed from startech.com.bd, without the product microdata.
        assertThat(find("""
                <head><title>BenQ GW2490 23.8&quot; FHD IPS Monitor Price in Bangladesh</title>
                <meta property="og:title" content="BenQ GW2490 23.8&quot; FHD IPS Monitor Price in Bangladesh" />
                <meta property="og:site_name" content="Star Tech Ltd " /></head>
                """, STARTECH)).isEqualTo("BenQ GW2490 23.8\" FHD IPS Monitor");
    }

    // ---- Structured names come first ---------------------------------------------------------

    @Test
    void prefersAmazonsProductTitle() {
        // Trimmed from amazon.com/dp/B00593T928: no og:title, JSON-LD or product microdata.
        assertThat(find("""
                <head><title>Amazon.com | UGG Men&#x27;s Neumel Boot, Chestnut, 08 | Chukka</title></head>
                <div id="titleSection" class="a-section a-spacing-none"> <h1 id="title" class="a-size-large">
                <span id="productTitle" class="a-size-large product-title-word-break">        UGG Men&#39;s Neumel Chukka Boots       </span>
                </h1></div>
                """, AMAZON)).isEqualTo("UGG Men's Neumel Chukka Boots");
    }

    @Test
    void readsTheProductsOwnMicrodataNameNotTheBreadcrumbsOrBrand() {
        // Trimmed from startech.com.bd/benq-gw2490-fhd-monitor.
        assertThat(find("""
                <head><title>BenQ GW2490 23.8&quot; FHD IPS Monitor Price in Bangladesh</title></head>
                <ul class="breadcrumb" itemscope itemtype="http://schema.org/BreadcrumbList">
                <li itemprop="itemListElement" itemscope itemtype="http://schema.org/ListItem"><a itemtype="http://schema.org/Thing" itemprop="item" href="https://www.startech.com.bd/monitor"><span itemprop="name">Monitor</span></a><meta itemprop="position" content="1" /></li>
                <li itemprop="itemListElement" itemscope itemtype="http://schema.org/ListItem"><a itemtype="http://schema.org/Thing" itemprop="item" href="https://www.startech.com.bd/benq-monitor"><span itemprop="name">BenQ</span></a><meta itemprop="position" content="2" /></li>
                </ul>
                <div class="product-details content" itemscope itemtype="http://schema.org/Product">
                <table><tr class="product-info-group" itemprop="brand" itemtype="http://schema.org/Thing" itemscope>
                <td class="product-info-label">Brand</td><td class="product-info-data product-brand" itemprop="name">BenQ</td></tr></table>
                <h1 itemprop="name" class="product-name">BenQ GW2490 23.8&quot; 100Hz FHD IPS Monitor</h1>
                <div class="short-description" itemprop="offers" itemscope itemtype="http://schema.org/Offer">
                <meta itemprop="priceCurrency" content="BDT" /><meta itemprop="price" content="16000.0000" /></div>
                </div>
                """, STARTECH)).isEqualTo("BenQ GW2490 23.8\" 100Hz FHD IPS Monitor");
    }

    @Test
    void readsMicrodataNameFromAMetaTag() {
        assertThat(find("""
                <div itemscope itemtype="https://schema.org/Product"><meta itemprop="name" content="Desk Lamp"></div>
                """)).isEqualTo("Desk Lamp");
    }

    @Test
    void prefersJsonLdProductName() {
        assertThat(find("""
                <head><meta property="og:title" content="Buy Desk Lamp | Lamp Shop">
                <script type="application/ld+json">{"@graph":[{"@type":"WebPage","name":"Lamps"},
                  {"@type":["Product","Thing"],"name":"Desk Lamp","offers":{"@type":"Offer","name":"New"}}]}</script></head>
                """)).isEqualTo("Desk Lamp");
        assertThat(find("""
                <script type="application/ld+json">{"@type":"ProductGroup","name":"Linen Shirt"}</script>
                """)).isEqualTo("Linen Shirt");
    }

    @Test
    void ignoresNamesOfNonProductJsonLd() {
        assertThat(find("""
                <head><title>Desk Lamp</title>
                <script type="application/ld+json">{"@type":"Organization","name":"Lamp Shop"}</script>
                <script type="application/ld+json">{ not json</script></head>
                """)).isEqualTo("Desk Lamp");
    }

    @Test
    void cleansStructuredNamesToo() {
        assertThat(find("""
                <head><meta property="og:site_name" content="Star Tech Ltd">
                <script type="application/ld+json">{"@type":"Product","name":"BenQ GW2490 Price in Bangladesh"}</script></head>
                """, STARTECH)).isEqualTo("BenQ GW2490");
    }

    // ---- Site names ---------------------------------------------------------------------------

    @Test
    void stripsATrailingSiteNameFromOgSiteName() {
        assertThat(find("""
                <head><meta property="og:title" content="Ergonomic Office Chair – Seatly Furniture">
                <meta property="og:site_name" content="Seatly Furniture"></head>
                """)).isEqualTo("Ergonomic Office Chair");
    }

    @Test
    void stripsASiteNameMatchingTheHost() {
        assertThat(find("<title>Ergonomic Office Chair | Seatly</title>", "https://www.seatly.co.uk/chair"))
                .isEqualTo("Ergonomic Office Chair");
        assertThat(find("<title>Ergonomic Office Chair | Office Chairs | Seatly.co.uk</title>", "https://seatly.co.uk/c"))
                .isEqualTo("Ergonomic Office Chair");
    }

    @Test
    void prefersOgTitleOverTheTitleTagAndFallsBackWhenItIsEmpty() {
        assertThat(find("<head><title>Page</title><meta property=\"og:title\" content=\"Desk Lamp\"></head>"))
                .isEqualTo("Desk Lamp");
        assertThat(find("<head><title>Desk Lamp</title><meta property=\"og:title\" content=\" \"></head>"))
                .isEqualTo("Desk Lamp");
    }

    // ---- Category ----------------------------------------------------------------------------

    @Test
    void keepsTheLastPartWhenNoSiteNameWasStripped() {
        assertThat(find("<title>Sony WH-1000XM5 Headphones | Black</title>"))
                .isEqualTo("Sony WH-1000XM5 Headphones | Black");
    }

    @Test
    void keepsTheLastPartWhenItIsNotLikeACategory() {
        // Digits mark a variant, not a department.
        assertThat(find("<title>Amazon.com: Apple iPhone 15 Pro : 256GB</title>", AMAZON))
                .isEqualTo("Apple iPhone 15 Pro : 256GB");
        // Longer than what would be left.
        assertThat(find("<title>Nike | Air Force Ones | Kicks</title>", "https://kicks.test/p"))
                .isEqualTo("Nike | Air Force Ones");
        // A different separator from the one before the site name.
        assertThat(find("<title>Wireless Headphones - Black | Kicks</title>", "https://kicks.test/p"))
                .isEqualTo("Wireless Headphones - Black");
    }

    @Test
    void keepsAColonThatIsPartOfTheName() {
        assertThat(find("<title>Amazon.com: Star Wars: The Mandalorian</title>", AMAZON))
                .isEqualTo("Star Wars: The Mandalorian");
        assertThat(find("<title>Amazon.com: Star Wars: The Mandalorian : Movies &amp; TV</title>", AMAZON))
                .isEqualTo("Star Wars: The Mandalorian");
    }

    // ---- SEO tails ---------------------------------------------------------------------------

    @Test
    void stripsBestPriceTails() {
        assertThat(find("<title>Buy BenQ GW2490 Online at Best Price in India</title>")).isEqualTo("BenQ GW2490");
        assertThat(find("<title>BenQ GW2490 - Best Price in BD 2026</title>")).isEqualTo("BenQ GW2490");
        assertThat(find("<title>BenQ GW2490 Online at Lowest Price</title>")).isEqualTo("BenQ GW2490");
        assertThat(find("<title>Buy BenQ GW2490 Online | Kicks</title>", "https://kicks.test/p")).isEqualTo("BenQ GW2490");
    }

    @Test
    void keepsWordsThatOnlyLookLikeTails() {
        assertThat(find("<title>Superprice in BD</title>")).isEqualTo("Superprice in BD");
        assertThat(find("<title>Buy Me a Coffee Mug</title>")).isEqualTo("Buy Me a Coffee Mug");
    }

    // ---- Fallbacks ---------------------------------------------------------------------------

    @Test
    void leavesUndecoratedNamesUnchanged() {
        assertThat(find("<head><meta property=\"og:title\" content=\"Office Chair\"></head>")).isEqualTo("Office Chair");
        assertThat(find("<title>Canon EOS R7 Body - Mirrorless Camera</title>"))
                .isEqualTo("Canon EOS R7 Body - Mirrorless Camera");
        assertThat(find("<title>AMD Ryzen 7 8700F</title>")).isEqualTo("AMD Ryzen 7 8700F");
    }

    @Test
    void keepsTheOriginalWhenCleaningLeavesNothing() {
        assertThat(find("<title>Price in Bangladesh</title>")).isEqualTo("Price in Bangladesh");
        assertThat(find("<title>Amazon.com</title>", AMAZON)).isEqualTo("Amazon.com");
    }

    @Test
    void collapsesWhitespaceAndCapsTheLength() {
        assertThat(find("<head><meta property=\"og:title\" content=\"  Desk \n  Lamp  \"></head>")).isEqualTo("Desk Lamp");
        assertThat(find("<title>" + "a".repeat(300) + "</title>")).hasSize(255);
        assertThat(find("<p>No title</p>")).isEmpty();
    }
}
