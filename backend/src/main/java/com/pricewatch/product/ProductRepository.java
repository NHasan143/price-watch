package com.pricewatch.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Products whose temporarily failed check is due to be tried again. */
    List<Product> findByNextRetryAtLessThanEqualOrderByNextRetryAtAsc(Instant now);
}
