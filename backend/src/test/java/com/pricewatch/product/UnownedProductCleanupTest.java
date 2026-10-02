package com.pricewatch.product;

import com.pricewatch.account.Account;
import com.pricewatch.account.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cleanup-test;DB_CLOSE_DELAY=-1",
        "pricewatch.scheduler.enabled=false",
        "pricewatch.auth.clerk.issuer=https://clerk.test"
})
class UnownedProductCleanupTest {

    @Autowired
    UnownedProductCleanup cleanup;
    @Autowired
    ProductRepository products;
    @Autowired
    PriceRecordRepository records;
    @Autowired
    AccountRepository accounts;

    @Test
    void removesProductsFromBeforeAccountsAndKeepsOwnedAndGuestOnes() {
        Account alice = accounts.save(new Account("user_alice", null));
        Product legacy = products.save(new Product(null, null, "Old lamp", "https://shop.test/1", null, BigDecimal.TEN));
        Product owned = products.save(new Product(alice, null, "New lamp", "https://shop.test/2", null, BigDecimal.TEN));
        records.save(new PriceRecord(legacy, BigDecimal.ONE, null, Instant.now()));
        records.save(new PriceRecord(owned, BigDecimal.ONE, null, Instant.now()));
        products.save(new Product(null, "0b6f8f3e-2f7a-4c1e-9a52-3d1f2f0a9c11", "Guest lamp", "https://shop.test/3", null,
                BigDecimal.TEN));

        cleanup.run(null);

        assertThat(products.findAll()).extracting(Product::getName).containsExactlyInAnyOrder("New lamp", "Guest lamp");
        assertThat(records.findByProductIdOrderByCheckedAtAsc(legacy.getId())).isEmpty();
        assertThat(records.findByProductIdOrderByCheckedAtAsc(owned.getId())).hasSize(1);
    }
}
