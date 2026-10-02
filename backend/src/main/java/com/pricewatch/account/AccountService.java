package com.pricewatch.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Creates the account row the first time a Clerk user calls the API, and keeps its email current. */
@Service
public class AccountService {

    /** How long to wait before asking Clerk again for an email it could not give. */
    private static final Duration EMAIL_LOOKUP_INTERVAL = Duration.ofHours(1);

    private final AccountRepository accounts;
    private final ClerkUserDirectory directory;

    public AccountService(AccountRepository accounts, ClerkUserDirectory directory) {
        this.accounts = accounts;
        this.directory = directory;
    }

    /**
     * Runs in its own transaction: the caller may be inside a read-only one (listing products),
     * and a first request must still be able to create the account.
     *
     * @param emailClaim the token's {@code email} claim, when the Clerk session token is customised to carry it
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Account provision(String clerkId, String emailClaim) {
        Account account = accounts.findByClerkId(clerkId).orElseGet(() -> new Account(clerkId, null));
        if (emailClaim != null && !emailClaim.isBlank()) {
            account.setEmail(emailClaim.trim());
        } else if (account.getEmail() == null && lookupDue(account)) {
            account.setEmailLookedUpAt(Instant.now());
            directory.primaryEmail(clerkId).ifPresent(account::setEmail);
        }
        return accounts.save(account);
    }

    private static boolean lookupDue(Account account) {
        Instant last = account.getEmailLookedUpAt();
        return last == null || last.plus(EMAIL_LOOKUP_INTERVAL).isBefore(Instant.now());
    }
}
