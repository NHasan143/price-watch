package com.pricewatch.account;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private static Jwt token(String azp) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "RS256").subject("user_1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (azp != null) {
            builder.claim("azp", azp);
        }
        return builder.build();
    }

    @Test
    void acceptsTokensMintedForTheAppsOwnOrigin() {
        var validator = SecurityConfig.authorizedParty(List.of("http://localhost:5173", " https://prices.example "));

        assertThat(validator.validate(token("http://localhost:5173")).hasErrors()).isFalse();
        assertThat(validator.validate(token("https://prices.example")).hasErrors()).isFalse();
        assertThat(validator.validate(token(null)).hasErrors()).isFalse();
    }

    @Test
    void refusesTokensMintedForAnotherSite() {
        var validator = SecurityConfig.authorizedParty(List.of("http://localhost:5173"));

        assertThat(validator.validate(token("https://evil.example")).hasErrors()).isTrue();
    }
}
