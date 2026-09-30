package com.pricewatch.scraper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PriceParserTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "$1,299.99       | 1299.99",
            "1.299,99 € | 1299.99",
            "Rs. 1,200       | 1200.00",
            "12,50           | 12.50",
            "1,234           | 1234.00",
            "1,234,567.89    | 1234567.89",
            "USD 49.5        | 49.50",
            "Price: $19.99.  | 19.99",
            "1299.99         | 1299.99",
            "0.999           | 1.00",
            "From $5 to $10  | 5.00"
    })
    void parsesCommonPriceFormats(String text, String expected) {
        assertThat(PriceParser.parse(text)).contains(new BigDecimal(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "free", "Sold out", "0", "0.00", "99999999999999"})
    void rejectsTextWithoutAUsablePrice(String text) {
        assertThat(PriceParser.parse(text)).isEmpty();
    }

    @Test
    void nullIsEmpty() {
        assertThat(PriceParser.parse(null)).isEmpty();
    }

    @Test
    void detectsCurrencyFromCodeOrSymbol() {
        assertThat(PriceParser.detectCurrency("USD1,299")).contains("USD");
        assertThat(PriceParser.detectCurrency("5 BDT")).contains("BDT");
        assertThat(PriceParser.detectCurrency("€5")).contains("EUR");
        assertThat(PriceParser.detectCurrency("$5")).contains("USD");
        assertThat(PriceParser.detectCurrency("THE 5")).isEmpty();
    }

    @Test
    void normalizesCurrencyCodes() {
        assertThat(PriceParser.normalizeCurrency(" usd ")).isEqualTo("USD");
        assertThat(PriceParser.normalizeCurrency("nope")).isNull();
        assertThat(PriceParser.normalizeCurrency(null)).isNull();
    }
}
