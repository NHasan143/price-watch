package com.pricewatch.product;

import com.pricewatch.scraper.ScrapeFailedException.Reason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Guests cannot make the server visit its own network, nor add products without limit by changing guest id. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:guest-safety-test;DB_CLOSE_DELAY=-1",
        "pricewatch.scheduler.enabled=false",
        "pricewatch.renderer.enabled=false",
        "pricewatch.guest.max-adds-per-hour=3"
})
class GuestAddressSafetyTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    ProductRepository products;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    /** Adds a product as a brand-new guest, from the given network address. */
    private ResultActions addAsNewGuest(String url, String fromAddress) throws Exception {
        return mvc.perform(post("/api/products")
                .header("X-Guest-Id", UUID.randomUUID().toString())
                .with(request -> {
                    request.setRemoteAddr(fromAddress);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + url + "\",\"targetPrice\":1}"));
    }

    @Test
    void refusesALinkToTheServersOwnNetwork() throws Exception {
        long before = products.count();

        addAsNewGuest("http://127.0.0.1:8080/actuator", "198.51.100.1")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value(Reason.INVALID.name()))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(
                        "private or local network address")));

        assertThat(products.count()).isEqualTo(before);
    }

    @Test
    void limitsGuestsPerNetworkAddressWhateverTheirGuestId() throws Exception {
        String address = "198.51.100.2";
        for (int i = 0; i < 3; i++) {
            // Refused links still count: each attempt would have made the server go and look.
            addAsNewGuest("http://169.254.169.254/latest/meta-data/", address)
                    .andExpect(status().isUnprocessableEntity());
        }

        addAsNewGuest("http://169.254.169.254/latest/meta-data/", address)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.reason").value("RATE_LIMITED"));

        addAsNewGuest("http://169.254.169.254/latest/meta-data/", "198.51.100.3")
                .andExpect(status().isUnprocessableEntity());
    }
}
