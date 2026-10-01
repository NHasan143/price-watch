package com.pricewatch.product;

import com.pricewatch.scraper.Availability;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** One observed price (and availability) of a product at a point in time. */
@Entity
@Table(name = "price_records")
public class PriceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Null for records saved before availability was tracked. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Availability availability;

    @Column(nullable = false)
    private Instant checkedAt;

    protected PriceRecord() {
        // required by JPA
    }

    public PriceRecord(Product product, BigDecimal price, Availability availability, Instant checkedAt) {
        this.product = product;
        this.price = price;
        this.availability = availability;
        this.checkedAt = checkedAt;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Availability getAvailability() {
        return availability;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }
}
