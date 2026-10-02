package com.pricewatch.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.pricewatch.account.Account;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Optional<Product> findByIdAndOwnerId(Long id, Long ownerId);

    List<Product> findByGuestIdOrderByCreatedAtDesc(String guestId);

    Optional<Product> findByIdAndGuestId(Long id, String guestId);

    long countByGuestId(String guestId);

    /** Everything the scheduler re-checks: guest products are read once only. */
    List<Product> findByOwnerIsNotNull();

    /**
     * Hands a guest's products to the account they signed up with. They are due for a check right
     * away, so the retry poller starts tracking them within a minute instead of at the next schedule.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    // alertSent is reset: a guest product already at its target never sent an alert, so the owner gets one now.
    @Query("update Product p set p.owner = :owner, p.guestId = null, p.alertSent = false, p.nextRetryAt = :now "
            + "where p.guestId = :guestId")
    int claimGuestProducts(@Param("guestId") String guestId, @Param("owner") Account owner, @Param("now") Instant now);

    /** Products from before accounts existed: no owner and no guest. */
    @Modifying
    @Query("delete from Product p where p.owner is null and p.guestId is null")
    int deleteUnowned();

    /** Guest products nobody signed up for. */
    @Modifying
    @Query("delete from Product p where p.guestId is not null and p.createdAt < :cutoff")
    int deleteGuestProductsBefore(@Param("cutoff") Instant cutoff);

    /** Products whose temporarily failed check is due to be tried again. */
    List<Product> findByNextRetryAtLessThanEqualOrderByNextRetryAtAsc(Instant now);
}
