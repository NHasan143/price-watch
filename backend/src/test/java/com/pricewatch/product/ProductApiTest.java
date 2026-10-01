package com.pricewatch.product;

import com.pricewatch.alert.AlertService;
import com.pricewatch.scraper.Availability;
import com.pricewatch.scraper.PriceScraper;
import com.pricewatch.scraper.ScrapeFailedException;
import com.pricewatch.scraper.ScrapeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:api-test;DB_CLOSE_DELAY=-1",
        "pricewatch.scheduler.enabled=false"
})
@AutoConfigureMockMvc
class ProductApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ProductRepository products;
    @Autowired
    PriceRecordRepository records;

    @MockitoBean
    PriceScraper scraper;
    @MockitoBean
    AlertService alerts;

    @BeforeEach
    void setUp() {
        reset(scraper, alerts);
        records.deleteAll();
        products.deleteAll();
        when(scraper.scrape(any(), any()))
                .thenReturn(new ScrapeResult(new BigDecimal("129.99"), "USD", Availability.IN_STOCK, "Test Headphones", null));
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
        when(scraper.scrape(any(), any())).thenThrow(new ScrapeFailedException("Could not find a price"));

        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Could not find a price"));
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
}
