package com.pricewatch.account;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies real RS256 session tokens end to end: a local stand-in for Clerk serves the public key
 * at /.well-known/jwks.json, and the API accepts or refuses tokens signed with it.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:token-test;DB_CLOSE_DELAY=-1",
        "pricewatch.scheduler.enabled=false",
        "pricewatch.auth.clerk.authorized-parties=http://localhost:5173"
})
class ClerkTokenVerificationTest {

    private static final RSAKey KEY = generateKey("clerk-test-key");
    private static final RSAKey OTHER_KEY = generateKey("someone-else");
    private static final HttpServer CLERK = startClerk();
    private static final String ISSUER = "http://127.0.0.1:" + CLERK.getAddress().getPort();

    @Autowired
    WebApplicationContext context;

    @DynamicPropertySource
    static void clerk(DynamicPropertyRegistry registry) {
        registry.add("pricewatch.auth.clerk.issuer", () -> ISSUER);
    }

    @AfterAll
    static void stopClerk() {
        CLERK.stop(0);
    }

    private static RSAKey generateKey(String id) {
        try {
            return new RSAKeyGenerator(2048).keyID(id).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer startClerk() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/.well-known/jwks.json", exchange -> {
                byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String token(RSAKey key, String issuer, String azp, long expiresInSeconds) throws JOSEException {
        long now = System.currentTimeMillis();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user_token_test")
                .issuer(issuer)
                .claim("azp", azp)
                .issueTime(new Date(now - 1000))
                .notBeforeTime(new Date(now - 1000))
                .expirationTime(new Date(now + expiresInSeconds * 1000))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private int statusWith(String token) throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        return mvc.perform(get("/api/products").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void acceptsAValidClerkSessionToken() throws Exception {
        org.assertj.core.api.Assertions.assertThat(statusWith(token(KEY, ISSUER, "http://localhost:5173", 60)))
                .isEqualTo(200);
    }

    @Test
    void refusesForgedExpiredOrMisdirectedTokens() throws Exception {
        org.assertj.core.api.Assertions.assertThat(statusWith(token(OTHER_KEY, ISSUER, "http://localhost:5173", 60)))
                .as("signed with a key Clerk does not publish").isEqualTo(401);
        org.assertj.core.api.Assertions.assertThat(statusWith(token(KEY, ISSUER, "http://localhost:5173", -120)))
                .as("expired").isEqualTo(401);
        org.assertj.core.api.Assertions.assertThat(statusWith(token(KEY, "https://other.clerk.test", "http://localhost:5173", 60)))
                .as("another Clerk instance").isEqualTo(401);
        org.assertj.core.api.Assertions.assertThat(statusWith(token(KEY, ISSUER, "https://evil.example", 60)))
                .as("minted for another site").isEqualTo(401);
    }

    @Test
    void withoutATokenOrGuestIdTheCallerIsAskedToSignUp() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/products")).andExpect(status().isForbidden());
    }
}
