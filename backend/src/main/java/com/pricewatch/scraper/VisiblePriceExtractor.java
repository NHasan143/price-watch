package com.pricewatch.scraper;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Last resort for shops that publish no structured data: reads the first price shown after the
 * product title ({@code <h1>}), which is where almost every product page puts it. Skips crossed-out
 * "was" prices and instalment/EMI amounts, and only accepts text that carries a currency marker, so
 * it never guesses from a bare number.
 */
@Component
@Order(3)
public class VisiblePriceExtractor implements PriceExtractor {

    /** How far past the title to look, in elements; the price sits in the title's own block. */
    private static final int MAX_ELEMENTS_AFTER_TITLE = 150;
    private static final int MAX_PRICE_TEXT_LENGTH = 40;

    /** A currency symbol or code next to a number. */
    private static final Pattern PRICE_TEXT = Pattern.compile(
            "(?:[€£₹৳₩₺₽₫¥$]|\\b[A-Z]{3}\\b|\\b(?:Tk|Rs)\\.?)\\s*\\d|\\d[\\d.,\\s]*\\s*(?:[€£₹৳₩₺₽₫¥$]|\\b[A-Z]{3}\\b)");

    private static final Pattern TAKA_WORD = Pattern.compile("(?i)\\bTk\\.?");
    private static final Pattern RUPEE_WORD = Pattern.compile("\\bRs\\.?");

    /** Prices that are not the current price: old/compare-at prices, instalments, savings. */
    private static final Pattern NOT_CURRENT_PRICE_TEXT = Pattern.compile(
            "(?i)\\b(?:emi|/\\s*mo(?:nth)?|per\\s+month|monthly|save|saving|off|was|before)\\b");
    /** Matched as whole words of a class or id ("old-price", "price_was"), never inside "font-bold". */
    private static final Pattern NOT_CURRENT_PRICE_CLASS = Pattern.compile(
            "(?i)(?:^|[-_\\s])(?:old|was|compare|strike|through|deleted|del|emi|instal\\w*|monthly|saving|save)(?=$|[-_\\s])");

    @Override
    public boolean supports(ScrapeTarget target) {
        return !target.hasSelector();
    }

    @Override
    public Optional<ExtractedPrice> extract(Document document, ScrapeTarget target) {
        Element title = document.body() == null ? null : document.body().selectFirst("h1");
        if (title == null) {
            return Optional.empty();
        }

        Elements all = document.body().getAllElements();
        int start = all.indexOf(title);
        int end = Math.min(all.size(), start + 1 + MAX_ELEMENTS_AFTER_TITLE);
        for (int i = start + 1; i < end; i++) {
            Element element = all.get(i);
            if (isPriceCandidate(element, title)) {
                String text = element.text();
                Optional<BigDecimal> price = PriceParser.parse(text);
                if (price.isPresent()) {
                    return Optional.of(new ExtractedPrice(price.get(), currencyOf(text)));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isPriceCandidate(Element element, Element title) {
        String text = element.text();
        if (text.isEmpty() || text.length() > MAX_PRICE_TEXT_LENGTH || !PRICE_TEXT.matcher(text).find()
                || !hasCurrencyMarker(text)) {
            return false;
        }
        // Take the innermost element holding the price, so its own classes describe it.
        for (Element child : element.children()) {
            if (PRICE_TEXT.matcher(child.text()).find()) {
                return false;
            }
        }
        if (NOT_CURRENT_PRICE_TEXT.matcher(text).find()) {
            return false;
        }
        for (Element node = element; node != null && !node.equals(title.parent()); node = node.parent()) {
            String tag = node.normalName();
            if (tag.equals("del") || tag.equals("s") || tag.equals("strike") || tag.equals("button")
                    || tag.equals("script") || tag.equals("style") || tag.equals("option")) {
                return false;
            }
            if (NOT_CURRENT_PRICE_CLASS.matcher(node.className() + " " + node.id()).find()) {
                return false;
            }
            if (node.attr("style").toLowerCase(Locale.ROOT).contains("line-through")) {
                return false;
            }
        }
        return true;
    }

    /** A real currency symbol or ISO code, or "Tk"/"Rs"; rules out things like "SKU 12". */
    private static boolean hasCurrencyMarker(String text) {
        return currencyOf(text) != null || RUPEE_WORD.matcher(text).find();
    }

    /** "Tk" is always the taka; "Rs" is shared by several rupees, so its currency stays unknown. */
    private static String currencyOf(String text) {
        return PriceParser.detectCurrency(TAKA_WORD.matcher(text).replaceAll("BDT")).orElse(null);
    }
}
