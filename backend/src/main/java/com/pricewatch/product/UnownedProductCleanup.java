package com.pricewatch.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes products (and their price history) left over from before PriceWatch had accounts: they
 * belong to no account and no guest, so nobody could see them. New products always have one of the
 * two, so after the first start with accounts this finds nothing.
 */
@Component
public class UnownedProductCleanup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UnownedProductCleanup.class);

    private final ProductRepository products;
    private final PriceRecordRepository records;

    public UnownedProductCleanup(ProductRepository products, PriceRecordRepository records) {
        this.products = products;
        this.records = records;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int history = records.deleteForUnownedProducts();
        int removed = products.deleteUnowned();
        if (removed > 0) {
            log.info("Removed {} product(s) and {} price record(s) from before accounts existed", removed, history);
        }
    }
}
