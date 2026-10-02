package com.pricewatch.product;

import com.pricewatch.account.AccountRepository;
import com.pricewatch.alert.AlertService;
import com.pricewatch.scraper.Availability;
import com.pricewatch.scraper.PriceScraper;
import com.pricewatch.scraper.ScrapeFailedException;
import com.pricewatch.scraper.ScrapeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:api-test;DB_CLOSE_DELAY=-1",
        "pricewatch.scheduler.enabled=false",
        "pricewatch.auth.clerk.issuer=https://clerk.test"
})
class ProductApiTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    ProductRepository products;
    @Autowired
    PriceRecordRepository records;
    @Autowired
    AccountRepository accounts;

    /** Requests are signed in as Alice unless a test says otherwise. */
    MockMvc mvc;

    @MockitoBean
    PriceScraper scraper;
    @MockitoBean
    AlertService alerts;

    @BeforeEach
    void setUp() {
        reset(scraper, alerts);
        records.deleteAll();
        products.deleteAll();
        accounts.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(signedIn("user_alice")))
                .build();
        when(scraper.scrape(any(), any()))
                .thenReturn(new ScrapeResult(new BigDecimal("129.99"), "USD", Availability.IN_STOCK, "Test Headphones", null));
    }

    /** A Clerk session token for this user, as the frontend sends it. */
    static JwtRequestPostProcessor signedIn(String clerkUserId) {
        return jwt().jwt(token -> token.subject(clerkUserId).claim("email", clerkUserId + "@example.test"));
    }

    private static final String VALID_BODY = """
            {"url":"https://shop.test/p/1","targetPrice":100}
            """;

    private long createProduct() throws Exception {
        String body = mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void createReturnsProductWithScrapedData() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Headphones"))
                .andExpect(jsonPath("$.currentPrice").value(129.99))
                .andExpect(jsonPath("$.targetPrice").value(100.0))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.belowTarget").value(false))
                .andExpect(jsonPath("$.availability").value("IN_STOCK"));
    }

    @Test
    void listReturnsCreatedProducts() throws Exception {
        createProduct();

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lowestPrice").value(129.99))
                .andExpect(jsonPath("$[0].highestPrice").value(129.99));
    }

    @Test
    void rejectsInvalidUrlAndTargetPrice() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"not-a-url\",\"targetPrice\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void reportsScrapeFailureAs422() throws Exception {
        when(scraper.scrape(any(), any()))
                .thenThrow(new ScrapeFailedException(ScrapeFailedException.Reason.NO_PRICE, "Could not find a price"));

        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Could not find a price"))
                .andExpect(jsonPath("$.reason").value("NO_PRICE"));
    }

    @Test
    void saysPlainlyWhenTheShopBlocksAutomatedChecks() throws Exception {
        when(scraper.scrape(any(), any())).thenThrow(new ScrapeFailedException(
                ScrapeFailedException.Reason.BLOCKED, "The shop showed a Cloudflare bot check instead of the product page."));

        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("The shop blocks automated price checks"))
                .andExpect(jsonPath("$.reason").value("BLOCKED"));
    }

    @Test
    void exposesFailureStateOfAProduct() throws Exception {
        long id = createProduct();
        when(scraper.scrape(any(), any())).thenThrow(new ScrapeFailedException(
                ScrapeFailedException.Reason.TEMPORARY, "The shop did not respond within 10 seconds."));

        mvc.perform(post("/api/products/" + id + "/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastErrorReason").value("TEMPORARY"))
                .andExpect(jsonPath("$.failedChecks").value(1))
                .andExpect(jsonPath("$.nextRetryAt").isNotEmpty());
    }

    @Test
    void updatesTargetPrice() throws Exception {
        long id = createProduct();

        mvc.perform(put("/api/products/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"targetPrice\":140}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.targetPrice").value(140.0))
                .andExpect(jsonPath("$.belowTarget").value(true));
    }

    @Test
    void exposesPriceHistory() throws Exception {
        long id = createProduct();

        mvc.perform(get("/api/products/" + id + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].price").value(129.99));
    }

    @Test
    void checkNowAddsAnotherPricePoint() throws Exception {
        long id = createProduct();
        when(scraper.scrape(any(), any()))
                .thenReturn(new ScrapeResult(new BigDecimal("119.00"), "USD", Availability.IN_STOCK, "Test Headphones", null));

        mvc.perform(post("/api/products/" + id + "/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPrice").value(119.0))
                .andExpect(jsonPath("$.lowestPrice").value(119.0))
                .andExpect(jsonPath("$.highestPrice").value(129.99));
    }

    @Test
    void deleteRemovesProductAndHistory() throws Exception {
        long id = createProduct();

        mvc.perform(delete("/api/products/" + id)).andExpect(status().isNoContent());

        mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/" + id + "/history")).andExpect(status().isNotFound());
    }

    @Test
    void unknownProductIs404() throws Exception {
        mvc.perform(get("/api/products/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product not found"));
    }

    // ---- accounts --------------------------------------------------------------------------

    @Test
    void asksToSignUpWhenThereIsNeitherASessionNorAGuestId() throws Exception {
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        anonymous.perform(get("/api/products"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("SIGN_UP_REQUIRED"));
    }

    @Test
    void createsTheAccountOnItsFirstRequestWithItsEmail() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(accounts.findByClerkId("user_alice"))
                .hasValueSatisfying(account -> org.assertj.core.api.Assertions.assertThat(account.getEmail())
                        .isEqualTo("user_alice@example.test"));
    }

    @Test
    void eachAccountSeesOnlyItsOwnShelf() throws Exception {
        long alicesProduct = createProduct();
        String bob = "user_bob";

        mvc.perform(get("/api/products").with(signedIn(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/products").with(signedIn("user_alice")))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void anotherAccountsProductLooksLikeItDoesNotExist() throws Exception {
        long id = createProduct();
        var bob = signedIn("user_bob");

        mvc.perform(get("/api/products/" + id).with(bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/" + id + "/history").with(bob)).andExpect(status().isNotFound());
        mvc.perform(post("/api/products/" + id + "/check").with(bob)).andExpect(status().isNotFound());
        mvc.perform(put("/api/products/" + id).with(bob).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetPrice\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/products/" + id).with(bob)).andExpect(status().isNotFound());

        // still there, unchanged, for its owner
        mvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetPrice").value(100.0));
    }
}
