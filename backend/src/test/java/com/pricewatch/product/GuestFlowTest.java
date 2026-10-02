package com.pricewatch.product;

import com.pricewatch.account.AccountRepository;
import com.pricewatch.alert.AlertService;
import com.pricewatch.scraper.Availability;
import com.pricewatch.scraper.PriceScraper;
import com.pricewatch.scraper.ScrapeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A visitor adds products as a guest, then signs up and keeps them. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:guest-test;DB_CLOSE_DELAY=-1",
        "pricewatch.scheduler.enabled=false",
        "pricewatch.auth.clerk.issuer=https://clerk.test",
        "pricewatch.guest.max-products=2"
})
class GuestFlowTest {

    private static final String GUEST = "6f1c2d3e-4b5a-4c6d-8e7f-9a0b1c2d3e4f";
    private static final String OTHER_GUEST = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d";
    private static final String BODY = """
            {"url":"https://shop.test/p/1","targetPrice":100}
            """;

    @Autowired
    WebApplicationContext context;
    @Autowired
    ProductRepository products;
    @Autowired
    PriceRecordRepository records;
    @Autowired
    AccountRepository accounts;
    @Autowired
    GuestProductService guestProducts;
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @MockitoBean
    PriceScraper scraper;
    @MockitoBean
    AlertService alerts;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(scraper, alerts);
        records.deleteAll();
        products.deleteAll();
        accounts.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // already below the target: an account would get an alert straight away
        when(scraper.scrape(any(), any()))
                .thenReturn(new ScrapeResult(new BigDecimal("90.00"), "USD", Availability.IN_STOCK, "Lamp", null));
    }

    private static MockHttpServletRequestBuilder asGuest(MockHttpServletRequestBuilder request, String guestId) {
        return request.header("X-Guest-Id", guestId);
    }

    private long addAsGuest(String guestId) throws Exception {
        String body = mvc.perform(asGuest(post("/api/products"), guestId)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tracked").value(false))
                .andExpect(jsonPath("$.currentPrice").value(90.0))
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void aGuestSeesThePriceOnceWithoutAnAlert() throws Exception {
        addAsGuest(GUEST);

        mvc.perform(asGuest(get("/api/products"), GUEST))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tracked").value(false));
        verify(alerts, never()).sendPriceDrop(any());
    }

    @Test
    void guestsOnlySeeTheirOwnProducts() throws Exception {
        long id = addAsGuest(GUEST);

        mvc.perform(asGuest(get("/api/products"), OTHER_GUEST)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(asGuest(get("/api/products/" + id), OTHER_GUEST)).andExpect(status().isNotFound());
    }

    @Test
    void checkingAgainNeedsAnAccount() throws Exception {
        long id = addAsGuest(GUEST);

        mvc.perform(asGuest(post("/api/products/" + id + "/check"), GUEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("SIGN_UP_REQUIRED"));
    }

    @Test
    void guestsCanAddOnlyAFewProducts() throws Exception {
        addAsGuest(GUEST);
        addAsGuest(GUEST);

        mvc.perform(asGuest(post("/api/products"), GUEST).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("up to 2 products")));
    }

    @Test
    void signingUpMovesTheGuestsProductsToTheAccountAndStartsTrackingThem() throws Exception {
        long id = addAsGuest(GUEST);
        var alice = jwt().jwt(token -> token.subject("user_alice").claim("email", "alice@example.test"));

        mvc.perform(asGuest(post("/api/guest/claim"), GUEST).with(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(1));

        mvc.perform(get("/api/products").with(alice))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].tracked").value(true));
        mvc.perform(asGuest(get("/api/products"), GUEST)).andExpect(jsonPath("$.length()").value(0));

        Product claimed = products.findById(id).orElseThrow();
        assertThat(claimed.isAlertSent()).as("the owner still gets the alert the guest never got").isFalse();
        assertThat(claimed.getNextRetryAt()).as("due for its first tracked check now").isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void claimingNeedsAnAccount() throws Exception {
        addAsGuest(GUEST);

        mvc.perform(asGuest(post("/api/guest/claim"), GUEST)).andExpect(status().isForbidden());
    }

    @Test
    void unclaimedGuestProductsAreRemovedAfterAWhile() throws Exception {
        long id = addAsGuest(GUEST);
        // pretend it was added long ago (createdAt is not updatable through JPA)
        jdbc.update("update products set created_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")), id);

        guestProducts.removeExpiredGuestProducts();

        assertThat(products.findById(id)).isEmpty();
    }
}
