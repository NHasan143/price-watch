package com.pricewatch.account;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Looks up a user's primary email address with Clerk's Backend API, for alert emails. Needs the
 * secret key ({@code pricewatch.auth.clerk.secret-key}); without it, emails come only from an
 * {@code email} claim in the session token (see README).
 */
@Component
public class ClerkUserDirectory {

    private static final Logger log = LoggerFactory.getLogger(ClerkUserDirectory.class);

    private final RestClient client;
    private final boolean configured;

    public ClerkUserDirectory(
            RestClient.Builder builder,
            @Value("${pricewatch.auth.clerk.secret-key:}") String secretKey,
            @Value("${pricewatch.auth.clerk.api-url:https://api.clerk.com/v1}") String apiUrl) {
        this.configured = !secretKey.isBlank();
        this.client = builder.baseUrl(apiUrl).defaultHeader("Authorization", "Bearer " + secretKey).build();
    }

    public Optional<String> primaryEmail(String clerkUserId) {
        if (!configured) {
            return Optional.empty();
        }
        try {
            JsonNode user = client.get().uri("/users/{id}", clerkUserId).retrieve().body(JsonNode.class);
            if (user == null) {
                return Optional.empty();
            }
            String primaryId = user.path("primary_email_address_id").asText("");
            for (JsonNode address : user.path("email_addresses")) {
                if (address.path("id").asText().equals(primaryId)) {
                    return Optional.of(address.path("email_address").asText()).filter(email -> !email.isBlank());
                }
            }
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("Could not look up the email of Clerk user {}: {}", clerkUserId, e.getMessage());
            return Optional.empty();
        }
    }
}
