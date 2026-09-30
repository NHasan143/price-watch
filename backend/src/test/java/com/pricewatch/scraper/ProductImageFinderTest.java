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
