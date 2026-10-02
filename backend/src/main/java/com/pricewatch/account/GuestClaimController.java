package com.pricewatch.account;

import com.pricewatch.product.GuestProductService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Called by the app right after a guest signs up or signs in, with both the session token and the
 * guest id: the products the guest added move to the account and start being tracked.
 */
@RestController
@RequestMapping("/api/guest")
public class GuestClaimController {

    private final CurrentRequester requester;
    private final GuestProductService guestProducts;

    public GuestClaimController(CurrentRequester requester, GuestProductService guestProducts) {
        this.requester = requester;
        this.guestProducts = guestProducts;
    }

    @PostMapping("/claim")
    public Map<String, Integer> claim() {
        Account account = requester.requireAccount();
        int claimed = requester.guestId().map(guestId -> guestProducts.claim(guestId, account)).orElse(0);
        return Map.of("claimed", claimed);
    }
}
