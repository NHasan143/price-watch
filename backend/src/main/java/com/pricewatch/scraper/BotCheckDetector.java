package com.pricewatch.scraper;

import org.jsoup.nodes.Document;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recognises the pages bot-protection services show instead of the product (Cloudflare's "Just a
 * moment...", DataDome, PerimeterX, Imperva, Akamai, Amazon's robot check). Only used to explain a
 * page that had no price: a normal product page that happens to load a CAPTCHA script for its
 * newsletter form is never treated as blocked, because its price is read first.
 */
final class BotCheckDetector {

    private static final Pattern GENERIC_TITLE = Pattern.compile(
            "captcha|robot check|are you a (human|robot)|verify you are human|security check|bot verification"
                    + "|access denied|pardon our interruption");

    private BotCheckDetector() {
    }

    /** @return who put up the bot check, for the message ("Cloudflare"), or "a bot check" when unknown */
    static Optional<String> detect(Document page) {
        if (page == null) {
            return Optional.empty();
        }
        String title = page.title().toLowerCase(Locale.ROOT).trim();
        String text = page.body() == null ? "" : page.body().text();

        if (title.startsWith("just a moment") || title.contains("attention required! | cloudflare")
                || page.selectFirst("#challenge-form, #challenge-running, #cf-challenge-running, .cf-browser-verification")
                        != null
                || text.contains("Sorry, you have been blocked")
                || text.contains("needs to review the security of your connection")) {
            return Optional.of("Cloudflare");
        }
        if (page.selectFirst("iframe[src*=captcha-delivery.com], script[src*=captcha-delivery.com]") != null) {
            return Optional.of("DataDome");
        }
        if (page.selectFirst("#px-captcha, [id^=px-captcha]") != null || text.contains("Press & Hold to confirm")) {
            return Optional.of("PerimeterX");
        }
        if (text.contains("Incapsula incident ID") || page.selectFirst("iframe[src*=_Incapsula_Resource]") != null) {
            return Optional.of("Imperva");
        }
        if (title.equals("access denied") && text.contains("Reference #")) {
            return Optional.of("Akamai");
        }
        if (page.selectFirst("form[action*=validateCaptcha]") != null || GENERIC_TITLE.matcher(title).find()) {
            return Optional.of("");
        }
        return Optional.empty();
    }

    /** "The shop showed a Cloudflare bot check instead of the product page." */
    static String describe(String provider) {
        String check = provider.isEmpty() ? "a bot check (CAPTCHA)" : "a " + provider + " bot check";
        return "The shop showed " + check + " instead of the product page.";
    }
}
