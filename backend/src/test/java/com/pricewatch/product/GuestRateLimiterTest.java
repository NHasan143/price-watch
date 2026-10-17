package com.pricewatch.product;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuestRateLimiterTest {

    private Instant now = Instant.parse("2026-10-18T10:00:00Z");
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };
    private final GuestRateLimiter limiter = new GuestRateLimiter(3, clock);

    @Test
    void refusesTheAttemptAfterTheLimitAndSaysWhenToTryAgain() {
        limiter.acquire("203.0.113.7");
        now = now.plus(Duration.ofMinutes(20));
        limiter.acquire("203.0.113.7");
        limiter.acquire("203.0.113.7");

        assertThatThrownBy(() -> limiter.acquire("203.0.113.7"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofMinutes(40)))
                .hasMessageContaining("Try again in 40 minutes");
    }

    @Test
    void allowsMoreOnceAnHourHasPassed() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire("203.0.113.7");
        }
        now = now.plus(Duration.ofHours(1));

        assertThatCode(() -> limiter.acquire("203.0.113.7")).doesNotThrowAnyException();
    }

    @Test
    void countsEachAddressSeparately() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire("203.0.113.7");
        }

        assertThatCode(() -> limiter.acquire("203.0.113.8")).doesNotThrowAnyException();
    }

    @Test
    void countsAnIpv6NetworkAsOneAddress() {
        limiter.acquire("2001:db8:1234:5678::1");
        limiter.acquire("2001:db8:1234:5678:aaaa::2");
        limiter.acquire("2001:db8:1234:5678:ffff:ffff:ffff:ffff");

        assertThatThrownBy(() -> limiter.acquire("2001:db8:1234:5678::9"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.acquire("2001:db8:1234:5679::1")).doesNotThrowAnyException();
    }

    @Test
    void keysIpv4ByAddressAndIpv6ByNetwork() {
        assertThat(GuestRateLimiter.key("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(GuestRateLimiter.key("2001:db8:1234:5678::1")).isEqualTo("20010db812345678/64");
    }
}
