package com.pricewatch.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A person using PriceWatch. Sign-in itself is handled by Clerk; this row links Clerk's user id to
 * the products the person tracks and remembers the email their alerts go to.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Clerk's user id ({@code user_...}), the {@code sub} claim of the session token. */
    @Column(nullable = false, unique = true, length = 64)
    private String clerkId;

    /** Primary email address at Clerk; null until known. Alerts for this account's products go here. */
    @Column(length = 320)
    private String email;

    /** Last time the email was looked up at Clerk, so a missing one is not fetched on every request. */
    private Instant emailLookedUpAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Account() {
        // required by JPA
    }

    public Account(String clerkId, String email) {
        this.clerkId = clerkId;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public String getClerkId() {
        return clerkId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getEmailLookedUpAt() {
        return emailLookedUpAt;
    }

    public void setEmailLookedUpAt(Instant emailLookedUpAt) {
        this.emailLookedUpAt = emailLookedUpAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
