package com.pricewatch.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PriceRecordRepository extends JpaRepository<PriceRecord, Long> {

    List<PriceRecord> findByProductIdOrderByCheckedAtAsc(Long productId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PriceRecord r where r.product.id = :productId")
    void deleteAllForProduct(@Param("productId") Long productId);

    @Query("""
            select r.product.id as productId, min(r.price) as minPrice, max(r.price) as maxPrice
            from PriceRecord r
            group by r.product.id
            """)
    List<PriceStats> summarizeAll();

    @Query("""
            select r.product.id as productId, min(r.price) as minPrice, max(r.price) as maxPrice
            from PriceRecord r
            where r.product.id = :productId
            group by r.product.id
            """)
    Optional<PriceStats> summarizeProduct(@Param("productId") Long productId);

    /** Lowest / highest price ever seen for a product. */
    interface PriceStats {
        Long getProductId();

        BigDecimal getMinPrice();

        BigDecimal getMaxPrice();
    }
}
