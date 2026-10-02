package com.pricewatch.product;

import com.pricewatch.alert.AlertService;
import com.pricewatch.scraper.Availability;
import com.pricewatch.scraper.PriceScraper;
import com.pricewatch.scraper.ScrapeResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Without a Clerk issuer the app still starts: accounts are off and everyone is a guest. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:guest-only-test;DB_CLOSE_DELAY=-1",
        "pricewatch.scheduler.enabled=false",
        "pricewatch.auth.clerk.issuer="
})
class GuestOnlyModeTest {

    @Autowired
    WebApplicationContext context;

    @MockitoBean
    PriceScraper scraper;
    @MockitoBean
    AlertService alerts;

    @Test
    void startsWithoutClerkAndServesGuests() throws Exception {
        when(scraper.scrape(any(), any()))
                .thenReturn(new ScrapeResult(new BigDecimal("12.00"), "EUR", Availability.UNKNOWN, "Mug", null));

        MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
                .perform(post("/api/products").header("X-Guest-Id", "6f1c2d3e-4b5a-4c6d-8e7f-9a0b1c2d3e4f")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://shop.test/m\",\"targetPrice\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tracked").value(false));
    }
}
