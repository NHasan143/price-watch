package com.pricewatch.account;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against a local stand-in for Clerk's Backend API. */
class ClerkUserDirectoryTest {

    private HttpServer server;
    private String apiUrl;
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/users/user_1", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = """
                    {"id":"user_1","primary_email_address_id":"idn_2","email_addresses":[
                      {"id":"idn_1","email_address":"old@example.test"},
                      {"id":"idn_2","email_address":"primary@example.test"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        apiUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void readsThePrimaryEmailWithTheSecretKey() {
        ClerkUserDirectory directory = new ClerkUserDirectory(RestClient.builder(), "sk_test_123", apiUrl);

        assertThat(directory.primaryEmail("user_1")).contains("primary@example.test");
        assertThat(authorization.get()).isEqualTo("Bearer sk_test_123");
    }

    @Test
    void unknownUserGivesNoEmail() {
        ClerkUserDirectory directory = new ClerkUserDirectory(RestClient.builder(), "sk_test_123", apiUrl);

        assertThat(directory.primaryEmail("user_missing")).isEmpty();
    }

    @Test
    void withoutASecretKeyItDoesNotCallClerk() {
        ClerkUserDirectory directory = new ClerkUserDirectory(RestClient.builder(), "", apiUrl);

        assertThat(directory.primaryEmail("user_1")).isEmpty();
        assertThat(authorization.get()).isNull();
    }
}
