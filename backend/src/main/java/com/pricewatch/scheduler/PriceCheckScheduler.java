package com.pricewatch.scheduler;

import com.pricewatch.product.PriceCheckService;
import com.pricewatch.product.Product;
import com.pricewatch.product.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** Re-checks every tracked product on a schedule (see pricewatch.scheduler.* in application.properties). */
@Component
@ConditionalOnProperty(name = "pricewatch.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class PriceCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceCheckScheduler.class);

    private final ProductRepository products;
    private final PriceCheckService priceChecks;
    private final long delayBetweenRequestsMs;

    public PriceCheckScheduler(
            ProductRepository products,
            PriceCheckService priceChecks,
            @Value("${pricewatch.scheduler.delay-between-requests-ms:1500}") long delayBetweenRequestsMs) {
        this.products = products;
        this.priceChecks = priceChecks;
        this.delayBetweenRequestsMs = delayBetweenRequestsMs;
    }

    @Scheduled(cron = "${pricewatch.scheduler.cron:0 0 */6 * * *}")
    public void checkAllProducts() {
        List<Product> all = products.findAll();
        log.info("Scheduled price check started for {} product(s)", all.size());
        for (Product product : all) {
            try {
                priceChecks.checkNow(product.getId());
                Thread.sleep(delayBetweenRequestsMs); // be polite to the shops
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Scheduled price check interrupted");
                return;
            } catch (RuntimeException e) {
                log.error("Unexpected error while checking product {}", product.getId(), e);
            }
        }
        log.info("Scheduled price check finished");
    }
}
