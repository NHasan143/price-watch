package com.pricewatch.scraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductImageFinderTest {

    private final ProductImageFinder finder = new ProductImageFinder(new ObjectMapper());

    private String find(String html) {
        return finder.find(Jsoup.parse(html, "https://shop.test/p/1"));
    }

    @Test
    void prefersOpenGraphImage() {
        assertThat(find("""
                <head><meta property="og:image" content="/img/og.jpg">
                <meta name="twitter:image" content="https://cdn.test/tw.jpg"></head>
                """)).isEqualTo("https://shop.test/img/og.jpg");
    }

    @Test
    void readsAmazonsFullSizeLandingImage() {
        // Trimmed from amazon.com/dp/B00593T928: no og:image, JSON-LD or microdata on the page.
        assertThat(find("""
                <div id="imgTagWrapperId" class="imgTagWrapper">
                <img alt="UGG Men's Neumel Chukka Boots"
                     src="https://m.media-amazon.com/images/I/61OkMZ8AVsL._AC_SY395_SX395_QL70_ML2_.jpg"
                     data-old-hires="https://m.media-amazon.com/images/I/61OkMZ8AVsL._AC_SL1500_.jpg"
                     data-a-dynamic-image="{&quot;https://m.media-amazon.com/images/I/61OkMZ8AVsL._AC_SY395_.jpg&quot;:[395,223]}"
                     id="landingImage"></div>
                """)).isEqualTo("https://m.media-amazon.com/images/I/61OkMZ8AVsL._AC_SL1500_.jpg");
    }

    @Test
    void picksTheLargestAmazonImageWhenThereIsNoHiRes() {
        assertThat(find("""
                <img id="landingImage" data-old-hires=""
                     src="data:image/gif;base64,R0lGODlhAQABAAAAACw="
                     data-a-dynamic-image="{&quot;https://m.media-amazon.com/images/I/a._SY355_.jpg&quot;:[355,277],
                       &quot;https://m.media-amazon.com/images/I/a._SX679_.jpg&quot;:[870,679],
                       &quot;https://m.media-amazon.com/images/I/a._SY450_.jpg&quot;:[450,351]}">
                """)).isEqualTo("https://m.media-amazon.com/images/I/a._SX679_.jpg");
    }

    @Test
    void readsAmazonBookCover() {
        assertThat(find("""
                <img id="imgBlkFront" src="https://m.media-amazon.com/images/I/book._SY160_.jpg"
                     data-a-dynamic-image="{&quot;https://m.media-amazon.com/images/I/book._SY466_.jpg&quot;:[300,466]}">
                """)).isEqualTo("https://m.media-amazon.com/images/I/book._SY466_.jpg");
    }

    @Test
    void readsWooCommerceGalleryImage() {
        assertThat(find("""
                <div class="woocommerce-product-gallery__image">
                <img src="/wp-content/uploads/lamp-600x600.jpg" data-large_image="/wp-content/uploads/lamp.jpg"></div>
                """)).isEqualTo("https://shop.test/wp-content/uploads/lamp.jpg");
    }

    @Test
    void skipsAnEmptyImageTagInsteadOfUsingThePageUrl() {
        assertThat(find("""
                <head><meta property="og:image" content="">
                <meta name="twitter:image" content="https://cdn.test/tw.jpg"></head>
                """)).isEqualTo("https://cdn.test/tw.jpg");
    }

    @Test
    void readsTwitterCardImage() {
        assertThat(find("<head><meta name=\"twitter:image\" content=\"https://cdn.test/tw.jpg\"></head>"))
                .isEqualTo("https://cdn.test/tw.jpg");
    }

    @Test
    void readsJsonLdProductImageInEveryShape() {
        assertThat(find("""
                <script type="application/ld+json">{"@type":"Product","image":"https://cdn.test/a.jpg"}</script>
                """)).isEqualTo("https://cdn.test/a.jpg");
        assertThat(find("""
                <script type="application/ld+json">{"@graph":[{"@type":"WebPage"},
                  {"@type":"Product","image":["/b.jpg","/c.jpg"]}]}</script>
                """)).isEqualTo("https://shop.test/b.jpg");
        assertThat(find("""
                <script type="application/ld+json">{"@type":"Product","image":{"@type":"ImageObject","url":"https://cdn.test/d.jpg"}}</script>
                """)).isEqualTo("https://cdn.test/d.jpg");
    }

    @Test
    void ignoresImagesOfNonProductJsonLd() {
        assertThat(find("""
                <script type="application/ld+json">{"@type":"Organization","image":"https://cdn.test/logo.png"}</script>
                """)).isNull();
    }

    @Test
    void readsMicrodataImage() {
        assertThat(find("<div itemscope><img itemprop=\"image\" src=\"/m.jpg\"></div>"))
                .isEqualTo("https://shop.test/m.jpg");
    }

    @Test
    void fallsBackToThePhotoMarkedByTheBrowser() {
        // Best Buy publishes no preview or structured image; the renderer marks the big gallery photo.
        assertThat(find("""
                <img src="https://cdn.test/thumb.jpg;maxWidth=56" alt="thumbnail">
                <img src="https://cdn.test/main.jpg;maxWidth=900" alt="Dehumidifier" data-pricewatch-hero="true">
                """)).isEqualTo("https://cdn.test/main.jpg;maxWidth=900");
    }

    @Test
    void rejectsNonWebUrls() {
        assertThat(find("<head><meta property=\"og:image\" content=\"data:image/png;base64,AAAA\"></head>")).isNull();
        assertThat(find("<p>No images here</p>")).isNull();
    }
}
