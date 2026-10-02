package com.pricewatch.alert;

import com.pricewatch.account.Account;
import com.pricewatch.product.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertServiceTest {

    private final JavaMailSender mail = mock(JavaMailSender.class);

    @SuppressWarnings("unchecked")
    private AlertService service(String fallback) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mail);
        return new AlertService(provider, fallback, "pricewatch@localhost");
    }

    private static Product productOf(Account owner) {
        Product product = new Product(owner, null, "Lamp", "https://shop.test/1", null, new BigDecimal("50"));
        product.setCurrentPrice(new BigDecimal("45"));
        return product;
    }

    @Test
    void emailsTheOwnersAccountAddress() {
        service("fallback@example.test").sendPriceDrop(productOf(new Account("user_1", "alice@example.test")));

        var sent = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("alice@example.test");
    }

    @Test
    void fallsBackToTheConfiguredAddressWhenTheOwnersEmailIsUnknown() {
        AlertService alerts = service("fallback@example.test");

        assertThat(alerts.recipient(productOf(new Account("user_1", null)))).isEqualTo("fallback@example.test");
    }

    @Test
    void onlyLogsWhenNoAddressIsKnown() {
        service("").sendBackInStock(productOf(new Account("user_1", null)));

        verify(mail, never()).send(any(SimpleMailMessage.class));
    }
}
