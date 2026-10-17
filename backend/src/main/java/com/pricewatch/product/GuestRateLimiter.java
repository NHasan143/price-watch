package com.pricewatch.product;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits how many products guests can add from one network address per hour. The per-guest product limit
 * is not enough on its own: a new random guest id gives a fresh quota, and every product added makes the
 * server download a page. Attempts count whether or not the page could be read. IPv6 addresses are
 * counted per /64, the block a single connection is usually given.
 */
@Component
public class GuestRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);
    /** Above this many addresses, ones with no recent attempts are dropped. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int maxPerHour;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    @Autowired
    public GuestRateLimiter(@Value("${pricewatch.guest.max-adds-per-hour:20}") int maxPerHour) {
        this(maxPerHour, Clock.systemUTC());
    }

    GuestRateLimiter(int maxPerHour, Clock clock) {
        this.maxPerHour = maxPerHour;
        this.clock = clock;
    }

    /**
     * Counts one attempt from this address.
     *
     * @throws TooManyRequestsException when the address has used up its attempts for the past hour
     */
    public void acquire(String clientAddress) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(WINDOW);
        Instant[] oldest = new Instant[1];
        attempts.compute(key(clientAddress), (key, times) -> {
            Deque<Instant> recent = times == null ? new ArrayDeque<>() : times;
            dropBefore(recent, cutoff);
            if (recent.size() >= maxPerHour) {
                oldest[0] = recent.peekFirst();
            } else {
                recent.addLast(now);
            }
            return recent;
        });
        if (attempts.size() > CLEANUP_THRESHOLD) {
            attempts.keySet().forEach(key -> attempts.computeIfPresent(key, (k, times) -> {
                dropBefore(times, cutoff);
                return times.isEmpty() ? null : times;
            }));
        }
        if (oldest[0] != null) {
            Duration retryAfter = Duration.between(now, oldest[0].plus(WINDOW));
            long minutes = Math.max(1, (retryAfter.toSeconds() + 59) / 60);
            throw new TooManyRequestsException("Too many products were added from your network in the last hour. "
                    + "Try again in " + minutes + (minutes == 1 ? " minute" : " minutes")
                    + ", or sign up to add more.", retryAfter);
        }
    }

    private static void dropBefore(Deque<Instant> times, Instant cutoff) {
        while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
            times.pollFirst();
        }
    }

    /** The address itself for IPv4, its /64 network for IPv6. */
    static String key(String clientAddress) {
        if (clientAddress == null || !clientAddress.contains(":")) {
            return String.valueOf(clientAddress);
        }
        try {
            // A string with a colon is parsed as an IPv6 literal, never looked up in DNS.
            if (InetAddress.getByName(clientAddress) instanceof Inet6Address address) {
                return HexFormat.of().formatHex(address.getAddress(), 0, 8) + "/64";
            }
        } catch (UnknownHostException notAnAddress) {
            // fall through: count the raw value
        }
        return clientAddress;
    }
}
