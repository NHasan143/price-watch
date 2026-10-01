package com.pricewatch.scraper;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BotCheckDetectorTest {

    private static Optional<String> detect(String html) {
        return BotCheckDetector.detect(Jsoup.parse(html, "https://shop.test/p/1"));
    }

    @Test
    void recognisesCloudflare() {
        assertThat(detect("<title>Just a moment...</title><body>Enable JavaScript and cookies to continue</body>"))
                .contains("Cloudflare");
        assertThat(detect("<title>Attention Required! | Cloudflare</title><body>Sorry, you have been blocked</body>"))
                .contains("Cloudflare");
    }

    @Test
    void recognisesDataDomePerimeterXImpervaAndAkamai() {
        assertThat(detect("<body><iframe src=\"https://geo.captcha-delivery.com/captcha/?x=1\"></iframe></body>"))
                .contains("DataDome");
        assertThat(detect("<body><div id=\"px-captcha\"></div></body>")).contains("PerimeterX");
        assertThat(detect("<body>Request unsuccessful. Incapsula incident ID: 123</body>")).contains("Imperva");
        assertThat(detect("<title>Access Denied</title><body>You don't have permission. Reference #18.abc</body>"))
                .contains("Akamai");
    }

    @Test
    void recognisesAGenericCaptchaPage() {
        assertThat(detect("<title>Robot Check</title><body><form action=\"/errors/validateCaptcha\"></form></body>"))
                .contains("");
        assertThat(BotCheckDetector.describe("")).contains("a bot check (CAPTCHA)");
    }

    @Test
    void ignoresAnOrdinaryPageThatLoadsACaptchaWidget() {
        assertThat(detect("""
                <title>Desk Lamp | Shop</title>
                <body><h1>Desk Lamp</h1>
                <form class="newsletter"><div class="g-recaptcha" data-sitekey="x"></div></form>
                <script src="/cdn-cgi/challenge-platform/scripts/jsd/main.js"></script></body>
                """)).isEmpty();
    }
}
