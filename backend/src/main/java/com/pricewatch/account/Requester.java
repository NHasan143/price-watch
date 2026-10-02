package com.pricewatch.account;

/**
 * Who is calling the API: a signed-in account, or a guest identified only by the random id their
 * browser keeps. Exactly one of {@code account} and {@code guestId} is set.
 */
public record Requester(Account account, String guestId) {

    public static Requester of(Account account) {
        return new Requester(account, null);
    }

    public static Requester guest(String guestId) {
        return new Requester(null, guestId);
    }

    public boolean isGuest() {
        return account == null;
    }
}
