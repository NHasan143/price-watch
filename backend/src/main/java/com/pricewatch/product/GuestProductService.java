package com.pricewatch.product;

import com.pricewatch.account.Account;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Moves a guest's products to the account they sign up with, and removes guest products nobody claimed. */
@Service
public class GuestProductService {

    private static final Logger log = LoggerFactory.getLogger(GuestProductService.class);

    private final ProductRepository products;
    private final PriceRecordRepository records;
    private final Duration keepGuestProducts;

    public GuestProductService(
            ProductRepository products,
            PriceRecordRepository records,
            @Value("${pricewatch.guest.keep-days:7}") long keepDays) {
        this.products = products;
        this.records = records;
        this.keepGuestProducts = Duration.ofDays(keepDays);
    }

    /** @return how many products moved to the account */
    @Transactional
    public int claim(String guestId, Account account) {
        int moved = products.claimGuestProducts(guestId, account, Instant.now());
        if (moved > 0) {
            log.info("Account {} claimed {} guest product(s)", account.getId(), moved);
        }
        return moved;
    }

    @Scheduled(cron = "${pricewatch.guest.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void removeExpiredGuestProducts() {
        Instant cutoff = Instant.now().minus(keepGuestProducts);
        records.deleteForGuestProductsBefore(cutoff);
        int removed = products.deleteGuestProductsBefore(cutoff);
        if (removed > 0) {
            log.info("Removed {} guest product(s) older than {} days that nobody signed up for",
                    removed, keepGuestProducts.toDays());
        }
    }
}
