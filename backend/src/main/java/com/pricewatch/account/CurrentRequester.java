package com.pricewatch.account;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the caller of the current API request: the signed-in account (created on its first
 * request) when a valid Clerk session token was sent, otherwise the guest named by the
 * {@value #GUEST_HEADER} header.
 */
@Component
public class CurrentRequester {

    /** Random UUID the browser generates once and keeps; it is the guest's only credential. */
    public static final String GUEST_HEADER = "X-Guest-Id";

    private final AccountService service;
    private final AccountRepository accounts;

    public CurrentRequester(AccountService service, AccountRepository accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    /** @throws SignUpRequiredException when the request carries neither a session token nor a guest id */
    public Requester require() {
        Optional<Account> account = signedInAccount();
        if (account.isPresent()) {
            return Requester.of(account.get());
        }
        return guestId().map(Requester::guest)
                .orElseThrow(() -> new SignUpRequiredException("Sign in, or allow the app to keep a guest id."));
    }

    /** @throws SignUpRequiredException for guests */
    public Account requireAccount() {
        return signedInAccount().orElseThrow(() -> new SignUpRequiredException("Sign up or sign in first."));
    }

    /** The guest id sent with this request, also when a signed-in user sends theirs to claim guest products. */
    public Optional<String> guestId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Optional.empty();
        }
        HttpServletRequest request = attributes.getRequest();
        String raw = request.getHeader(GUEST_HEADER);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()).toString());
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    private Optional<Account> signedInAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return Optional.empty();
        }
        Jwt jwt = token.getToken();
        try {
            return Optional.of(service.provision(jwt.getSubject(), jwt.getClaimAsString("email")));
        } catch (DataIntegrityViolationException raced) {
            // The app's first requests arrive together; another one created the account first.
            return Optional.of(accounts.findByClerkId(jwt.getSubject()).orElseThrow(() -> raced));
        }
    }
}
