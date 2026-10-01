package com.pricewatch.product;

import com.pricewatch.scraper.Availability;
import com.pricewatch.scraper.ScrapeFailedException.Reason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;

/** A product page the user wants to keep an eye on. */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    /** Optional CSS selector that points at the price element (for sites without structured data). */
    @Column(length = 255)
    private String cssSelector;

    @Column(length = 2048)
    private String imageUrl;

    @Column(length = 8)
    private String currency;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal targetPrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal currentPrice;

    private Instant lastCheckedAt;

    /** As of the last successful check; null for products added before availability was tracked. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Availability availability;

    @Column(length = 500)
    private String lastError;

    /** Why the last check failed; null after a successful check. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Reason lastErrorReason;

    /** Checks that failed in a row since the last successful one. */
    @Column(nullable = false)
    @ColumnDefault("0") // lets the column be added to an existing database
    private int failedChecks;

    /** When a temporarily failed check is tried again, ahead of the regular schedule; null if not planned. */
    private Instant nextRetryAt;

    /** True once an alert was sent for the current "below target" streak, so we do not spam. */
    @Column(nullable = false)
    private boolean alertSent;

    /**
     * True once the product was seen out of stock, until it is seen in stock again (which sends a
     * back-in-stock alert). Checks that cannot read availability leave it unchanged.
     */
    @Column(nullable = false)
    @ColumnDefault("false") // lets the column be added to an existing database
    private boolean awaitingRestock;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Product() {
        // required by JPA
    }

    public Product(String name, String url, String cssSelector, BigDecimal targetPrice) {
        this.name = name;
        this.url = url;
        this.cssSelector = cssSelector;
        this.targetPrice = targetPrice;
    }

    public boolean isBelowTarget() {
        return currentPrice != null && currentPrice.compareTo(targetPrice) <= 0;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public String getCssSelector() {
        return cssSelector;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getTargetPrice() {
        return targetPrice;
    }

    public void setTargetPrice(BigDecimal targetPrice) {
        this.targetPrice = targetPrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(Instant lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        if (lastError != null && lastError.length() > 500) {
            lastError = lastError.substring(0, 500);
        }
        this.lastError = lastError;
    }

    public Availability getAvailability() {
        return availability;
    }

    public void setAvailability(Availability availability) {
        this.availability = availability;
    }

    public Reason getLastErrorReason() {
        return lastErrorReason;
    }

    public int getFailedChecks() {
        return failedChecks;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    /** Records a failed check and when (if at all) to try again before the regular schedule. */
    public void recordFailure(String message, Reason reason, Instant checkedAt, Instant nextRetryAt) {
        setLastError(message);
        this.lastErrorReason = reason;
        this.lastCheckedAt = checkedAt;
        this.failedChecks++;
        this.nextRetryAt = nextRetryAt;
    }

    /** Clears the failure state after a check that read a price. */
    public void clearFailure() {
        this.lastError = null;
        this.lastErrorReason = null;
        this.failedChecks = 0;
        this.nextRetryAt = null;
    }

    public boolean isAlertSent() {
        return alertSent;
    }

    public void setAlertSent(boolean alertSent) {
        this.alertSent = alertSent;
    }

    public boolean isAwaitingRestock() {
        return awaitingRestock;
    }

    public void setAwaitingRestock(boolean awaitingRestock) {
        this.awaitingRestock = awaitingRestock;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
