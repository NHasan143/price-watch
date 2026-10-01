package com.pricewatch.product;

import com.pricewatch.alert.AlertService;
import com.pricewatch.scraper.Availability;
import com.pricewatch.scraper.PriceScraper;
import com.pricewatch.scraper.ScrapeFailedException;
import com.pricewatch.scraper.ScrapeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pricecheck-test;DB_CLOSE_DELAY=-1",
        "pricewatch.scheduler.enabled=false"
})
class PriceCheckServiceTest {

    @Autowired
    PriceCheckService service;
    @Autowired
    ProductRepository products;
    @Autowired
    PriceRecordRepository records;

    @MockitoBean
    PriceScraper scraper;
    @MockitoBean
    AlertService alerts;

    @BeforeEach
    void cleanDatabase() {
        reset(scraper, alerts);
        records.deleteAll();
        products.deleteAll();
    }

    private static ScrapeResult priced(String price) {
        return priced(price, Availability.UNKNOWN);
    }

    private static ScrapeResult priced(String price, Availability availability) {
        return new ScrapeResult(new BigDecimal(price), "USD", availability, "Test Headphones", null);
    }

    @Test
    void createProductStoresFirstPricePoint() {
        when(scraper.scrape(any(), any())).thenReturn(priced("120.00"));

        Product product = service.createProduct(null, "https://shop.test/p/1", null, new BigDecimal("100.00"));

        assertThat(product.getName()).isEqualTo("Test Headphones");
        assertThat(product.getCurrentPrice()).isEqualByComparingTo("120.00");
        assertThat(product.getCurrency()).isEqualTo("USD");
        assertThat(records.findByProductIdOrderByCheckedAtAsc(product.getId())).hasSize(1);
        verify(alerts, never()).sendPriceDrop(any());
    }

    @Test
    void alertsOnceWhenPriceCrossesBelowTargetAndAgainAfterItRecovers() {
        when(scraper.scrape(any(), any())).thenReturn(priced("120.00"));
        Product product = service.createProduct("Headphones", "https://shop.test/p/1", null, new BigDecimal("100.00"));
        Long id = product.getId();

        when(scraper.scrape(any(), any())).thenReturn(priced("95.00"));
        service.checkNow(id);
        verify(alerts, times(1)).sendPriceDrop(any());

        // still below target: no second email
        when(scraper.scrape(any(), any())).thenReturn(priced("94.00"));
        service.checkNow(id);
        verify(alerts, times(1)).sendPriceDrop(any());

        // price recovers, then drops again: new alert
        when(scraper.scrape(any(), any())).thenReturn(priced("130.00"));
        service.checkNow(id);
        when(scraper.scrape(any(), any())).thenReturn(priced("90.00"));
        service.checkNow(id);
        verify(alerts, times(2)).sendPriceDrop(any());

        assertThat(records.findByProductIdOrderByCheckedAtAsc(id)).hasSize(5);
    }

    @Test
    void alertsImmediatelyWhenNewProductIsAlreadyBelowTarget() {
        when(scraper.scrape(any(), any())).thenReturn(priced("80.00"));

        service.createProduct("Headphones", "https://shop.test/p/1", null, new BigDecimal("100.00"));

        verify(alerts, times(1)).sendPriceDrop(any());
    }

    @Test
    void alertsOnceWhenProductComesBackInStock() {
        when(scraper.scrape(any(), any())).thenReturn(priced("120.00", Availability.IN_STOCK));
        Long id = service.createProduct("Headphones", "https://shop.test/p/1", null, new BigDecimal("100.00")).getId();

        when(scraper.scrape(any(), any())).thenReturn(priced("120.00", Availability.OUT_OF_STOCK));
        assertThat(service.checkNow(id).getAvailability()).isEqualTo(Availability.OUT_OF_STOCK);
        service.checkNow(id);
        verify(alerts, never()).sendBackInStock(any());

        when(scraper.scrape(any(), any())).thenReturn(priced("120.00", Availability.IN_STOCK));
        service.checkNow(id);
        verify(alerts, times(1)).sendBackInStock(any());

        // still in stock: no second email
        service.checkNow(id);
        verify(alerts, times(1)).sendBackInStock(any());
        verify(alerts, never()).sendPriceDrop(any());

        assertThat(records.findByProductIdOrderByCheckedAtAsc(id))
                .extracting(PriceRecord::getAvailability)
                .containsExactly(Availability.IN_STOCK, Availability.OUT_OF_STOCK, Availability.OUT_OF_STOCK,
                        Availability.IN_STOCK, Availability.IN_STOCK);
    }

    @Test
    void restockIsNotLostWhenAvailabilityIsUnreadableInBetween() {
        when(scraper.scrape(any(), any())).thenReturn(priced("120.00", Availability.OUT_OF_STOCK));
        Long id = service.createProduct("Headphones", "https://shop.test/p/1", null, new BigDecimal("100.00")).getId();

        when(scraper.scrape(any(), any())).thenReturn(priced("120.00", Availability.UNKNOWN));
        service.checkNow(id);
        when(scraper.scrape(any(), any())).thenReturn(priced("120.00", Availability.IN_STOCK));
        service.checkNow(id);

        verify(alerts, times(1)).sendBackInStock(any());
    }

    @Test
    void restockAndDropBelowTargetOnTheSameCheckSendOneAlert() {
        when(scraper.scrape(any(), any())).thenReturn(priced("120.00", Availability.OUT_OF_STOCK));
        Long id = service.createProduct("Headphones", "https://shop.test/p/1", null, new BigDecimal("100.00")).getId();

        when(scraper.scrape(any(), any())).thenReturn(priced("95.00", Availability.IN_STOCK));
        Product checked = service.checkNow(id);

        assertThat(checked.isBelowTarget()).isTrue();
        verify(alerts, times(1)).sendBackInStock(any());
        verify(alerts, never()).sendPriceDrop(any());

        // the drop was reported with the restock, so it is not sent again on the next check
        when(scraper.scrape(any(), any())).thenReturn(priced("94.00", Availability.IN_STOCK));
        service.checkNow(id);
        verify(alerts, never()).sendPriceDrop(any());
    }

    @Test
    void newProductThatIsInStockDoesNotSendRestockAlert() {
        when(scraper.scrape(any(), any())).thenReturn(priced("120.00", Availability.IN_STOCK));

        service.createProduct("Headphones", "https://shop.test/p/1", null, new BigDecimal("100.00"));

        verify(alerts, never()).sendBackInStock(any());
    }

    @Test
    void scrapeFailureIsStoredAndKeepsPreviousPrice() {
        when(scraper.scrape(any(), any())).thenReturn(priced("120.00"));
        Product product = service.createProduct("Headphones", "https://shop.test/p/1", null, new BigDecimal("100.00"));

        when(scraper.scrape(any(), any())).thenThrow(new ScrapeFailedException("Store is down"));
        Product checked = service.checkNow(product.getId());

        assertThat(checked.getLastError()).isEqualTo("Store is down");
        assertThat(checked.getCurrentPrice()).isEqualByComparingTo("120.00");
        assertThat(records.findByProductIdOrderByCheckedAtAsc(product.getId())).hasSize(1);
    }

    @Test
    void createProductPropagatesScrapeFailureAndSavesNothing() {
        when(scraper.scrape(any(), any())).thenThrow(new ScrapeFailedException("No price"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.createProduct("x", "https://shop.test/p/1", null, new BigDecimal("10")))
                .isInstanceOf(ScrapeFailedException.class);

        assertThat(products.count()).isZero();
    }
}
