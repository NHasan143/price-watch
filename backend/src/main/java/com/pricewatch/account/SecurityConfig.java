package com.pricewatch.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Arrays;
import java.util.List;

/**
 * Guests and signed-in users both use the API; {@link CurrentRequester} tells them apart. A request
 * that carries a Clerk session token ({@code Authorization: Bearer ...}) must carry a valid one: it is
 * verified locally against Clerk's public keys (signature, expiry, issuer = your Clerk Frontend API
 * URL, authorized party = the origin the app is opened from). Without {@code pricewatch.auth.clerk.issuer}
 * accounts are off and everyone is a guest.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ObjectProvider<JwtDecoder> clerk) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Bearer tokens and the guest id are not sent automatically by the browser: no CSRF to defend against.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults());
        if (clerk.getIfAvailable() != null) {
            http.oauth2ResourceServer(server -> server.jwt(Customizer.withDefaults()));
        } else {
            log.warn("Accounts are off: pricewatch.auth.clerk.issuer (CLERK_ISSUER) is not set. Everyone is a guest; "
                    + "products are read once and nobody gets alerts. See README, Set up accounts.");
        }
        return http.build();
    }

    @Bean
    @ConditionalOnExpression("!'${pricewatch.auth.clerk.issuer:}'.isBlank()")
    JwtDecoder clerkJwtDecoder(
            @Value("${pricewatch.auth.clerk.issuer}") String issuer,
            @Value("${pricewatch.auth.clerk.authorized-parties:http://localhost:5173}") String[] authorizedParties) {
        String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(base + "/.well-known/jwks.json").build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(base), authorizedParty(List.of(authorizedParties))));
        return decoder;
    }

    /** Clerk puts the page's origin in {@code azp}; refuse tokens minted for some other site. */
    static OAuth2TokenValidator<Jwt> authorizedParty(List<String> allowed) {
        List<String> origins = allowed.stream().map(String::trim).filter(origin -> !origin.isEmpty()).toList();
        return jwt -> {
            String azp = jwt.getClaimAsString("azp");
            if (azp == null || origins.contains(azp)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                    "Token was issued for " + azp + ", which is not one of " + Arrays.toString(origins.toArray()), null));
        };
    }
}
