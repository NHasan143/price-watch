package com.pricewatch.scheduler;

import com.pricewatch.product.PriceCheckService;
import com.pricewatch.product.Product;
import com.pricewatch.product.ProductRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceCheckSchedulerTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final PriceCheckService priceChecks = mock(PriceCheckService.class);
    private final PriceCheckScheduler scheduler = new PriceCheckScheduler(products, priceChecks, 0);

    private static Product productWithId(long id) {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(id);
        return product;
    }

    @Test
    void checksProductsWhoseRetryIsDue() {
        Product first = productWithId(1);
        Product second = productWithId(2);
        when(products.findByNextRetryAtLessThanEqualOrderByNextRetryAtAsc(any())).thenReturn(List.of(first, second));

        scheduler.retryFailedChecks();

        verify(priceChecks).checkNow(1L);
        verify(priceChecks).checkNow(2L);
    }

    @Test
    void doesNothingWhenNoRetryIsDue() {
        when(products.findByNextRetryAtLessThanEqualOrderByNextRetryAtAsc(any())).thenReturn(List.of());

        scheduler.retryFailedChecks();

        verify(priceChecks, never()).checkNow(any());
    }

    @Test
    void keepsGoingWhenOneCheckBlowsUp() {
        Product first = productWithId(1);
        Product second = productWithId(2);
        when(products.findByNextRetryAtLessThanEqualOrderByNextRetryAtAsc(any())).thenReturn(List.of(first, second));
        when(priceChecks.checkNow(1L)).thenThrow(new IllegalStateException("boom"));

        scheduler.retryFailedChecks();

        verify(priceChecks).checkNow(2L);
    }
}
