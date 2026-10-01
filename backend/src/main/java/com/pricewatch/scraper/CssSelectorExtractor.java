package com.pricewatch.scraper;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Reads the price from the element matched by the CSS selector the user supplied. */
@Component
@Order(1)
public class CssSelectorExtractor implements PriceExtractor {

    @Override
    public boolean supports(ScrapeTarget target) {
        return target.hasSelector();
    }

    @Override
    public Optional<ExtractedPrice> extract(Document document, ScrapeTarget target) {
        Element element;
        try {
            element = document.selectFirst(target.cssSelector());
        } catch (RuntimeException e) {
            throw new ScrapeFailedException(ScrapeFailedException.Reason.INVALID, "The CSS selector '" + target.cssSelector() + "' is not valid.", e);
        }
        if (element == null) {
            return Optional.empty();
        }
        String raw = element.hasAttr("content") ? element.attr("content") : element.text();
        return PriceParser.parse(raw)
                .map(price -> new ExtractedPrice(price, PriceParser.detectCurrency(raw).orElse(null)));
    }
}
