package com.pricewatch.product;

import com.pricewatch.alert.AlertService;
import com.pricewatch.scraper.PriceScraper;
import com.pricewatch.scraper.ScrapeFailedException;
import com.pricewatch.scraper.ScrapeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * Core workflow: visit the product page, store the price, and decide whether to send an alert.
 * The network call happens outside any database transaction; only the bookkeeping is transactional.
 */
@Service
public class PriceCheckService {

    private static final Logger log = LoggerFactory.getLogger(PriceCheckService.class);

    private final PriceScraper scraper;
    private final ProductRepository products;
    private final PriceRecordRepository records;
    private final AlertService alerts;
    private final TransactionTemplate transaction;

    public PriceCheckService(
            PriceScraper scraper,
            ProductRepository products,
            PriceRecordRepository records,
            AlertService alerts,
            TransactionTemplate transaction) {
        this.scraper = scraper;
        this.products = products;
        this.records = records;
        this.alerts = alerts;
        this.transaction = transaction;
    }

    private record Outcome(Product product, boolean sendAlert) {
    }

    /**
     * Scrapes the page first (so a bad URL is rejected before anything is saved), then stores the
     * product together with its first price point.
     *
     * @throws ScrapeFailedException if no price can be read from the page
     */
    public Product createProduct(String name, String url, String cssSelector, BigDecimal targetPrice) {
        String selector = cssSelector == null || cssSelector.isBlank() ? null : cssSelector.trim();
        ScrapeResult result = scraper.scrape(url, selector);

        Product product = new Product(resolveName(name, result, url), url, selector, targetPrice);
        Outcome outcome = Objects.requireNonNull(transaction.execute(status -> {
            boolean alert = applyResult(product, result);
            Product saved = products.save(product);
            records.save(new PriceRecord(saved, result.price(), saved.getLastCheckedAt()));
            return new Outcome(saved, alert);
        }));

        if (outcome.sendAlert()) {
            alerts.sendPriceDrop(outcome.product());
        }
        return outcome.product();
    }

    /**
     * Re-checks one product. A scraping failure does not throw: it is stored on the product
     * ({@code lastError}) so the UI can show it, and the previous price is kept.
     */
    public Product checkNow(Long id) {
        Product product = products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        try {
            ScrapeResult result = scraper.scrape(product.getUrl(), product.getCssSelector());
            return storeSuccess(id, result);
        } catch (ScrapeFailedException e) {
            log.warn("Price check failed for product {} ({}): {}", id, product.getUrl(), e.getMessage());
            return storeFailure(id, e.getMessage());
        }
    }

    private Product storeSuccess(Long id, ScrapeResult result) {
        Outcome outcome = Objects.requireNonNull(transaction.execute(status -> {
            Product product = products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
            boolean alert = applyResult(product, result);
            Product saved = products.save(product);
            records.save(new PriceRecord(saved, result.price(), saved.getLastCheckedAt()));
            return new Outcome(saved, alert);
        }));
        if (outcome.sendAlert()) {
            alerts.sendPriceDrop(outcome.product());
        }
        return outcome.product();
    }

    private Product storeFailure(Long id, String message) {
        return Objects.requireNonNull(transaction.execute(status -> {
            Product product = products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
            product.setLastError(message);
            product.setLastCheckedAt(Instant.now());
            return products.save(product);
        }));
    }

    /**
     * Copies a scrape result onto the product and updates the alert flag.
     *
     * @return true if this check crossed below the target price and an alert should be sent
     */
    private boolean applyResult(Product product, ScrapeResult result) {
        product.setCurrentPrice(result.price());
        product.setLastCheckedAt(Instant.now());
        product.setLastError(null);
        if (result.currency() != null) {
            product.setCurrency(result.currency());
        }
        if (result.imageUrl() != null) {
            product.setImageUrl(result.imageUrl());
        }

        if (!product.isBelowTarget()) {
            product.setAlertSent(false);
            return false;
        }
        if (product.isAlertSent()) {
            return false;
        }
        product.setAlertSent(true);
        return true;
    }

    private static String resolveName(String requested, ScrapeResult result, String url) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        if (result.title() != null && !result.title().isBlank()) {
            return result.title();
        }
        try {
            String host = URI.create(url).getHost();
            if (host != null && !host.isBlank()) {
                return host;
            }
        } catch (IllegalArgumentException ignored) {
            // fall through to the generic name
        }
        return "Untitled product";
    }
}
