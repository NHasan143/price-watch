package com.pricewatch.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account-test;DB_CLOSE_DELAY=-1",
        "pricewatch.scheduler.enabled=false",
        "pricewatch.auth.clerk.issuer=https://clerk.test"
})
class AccountServiceTest {

    @Autowired
    AccountService service;
    @Autowired
    AccountRepository accounts;

    @MockitoBean
    ClerkUserDirectory directory;

    @BeforeEach
    void clean() {
        reset(directory);
        accounts.deleteAll();
    }

    @Test
    void createsTheAccountOnceAndKeepsTheEmailFromTheToken() {
        Account first = service.provision("user_1", "old@example.test");
        Account again = service.provision("user_1", "new@example.test");

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(again.getEmail()).isEqualTo("new@example.test");
        assertThat(accounts.count()).isEqualTo(1);
        verify(directory, never()).primaryEmail("user_1");
    }

    @Test
    void looksTheEmailUpAtClerkWhenTheTokenHasNone() {
        when(directory.primaryEmail("user_2")).thenReturn(Optional.of("two@example.test"));

        assertThat(service.provision("user_2", null).getEmail()).isEqualTo("two@example.test");
    }

    @Test
    void doesNotAskClerkOnEveryRequestWhenItHasNoEmail() {
        when(directory.primaryEmail("user_3")).thenReturn(Optional.empty());

        service.provision("user_3", null);
        service.provision("user_3", null);
        service.provision("user_3", null);

        verify(directory, times(1)).primaryEmail("user_3");
    }
}
