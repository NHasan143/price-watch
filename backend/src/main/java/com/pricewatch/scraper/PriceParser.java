package com.pricewatch.scraper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns human-written price text ("$1,299.99", "1.299,99 EUR", "Rs. 1,200") into numbers.
 * Pure Java with no dependencies, so it is easy to unit test.
 */
public final class PriceParser {

    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");

    /** First number in the text; may contain thousands/decimal separators and (narrow) no-break spaces. */
    private static final Pattern NUMBER = Pattern.compile("\\d(?:[\\d.,\\u00A0\\u202F]*\\d)?");

    private static final Pattern ISO_CODE = Pattern.compile("(?<![A-Za-z])[A-Z]{3}(?![A-Za-z])");

    private static final Map<String, String> SYMBOLS = new LinkedHashMap<>();

    static {
        SYMBOLS.put("€", "EUR"); // euro
        SYMBOLS.put("£", "GBP"); // pound
        SYMBOLS.put("₹", "INR"); // rupee
        SYMBOLS.put("৳", "BDT"); // taka
        SYMBOLS.put("₩", "KRW"); // won
        SYMBOLS.put("₺", "TRY"); // lira
        SYMBOLS.put("₽", "RUB"); // ruble
        SYMBOLS.put("₫", "VND"); // dong
        SYMBOLS.put("¥", "JPY"); // yen
        SYMBOLS.put("$", "USD");
    }

    private PriceParser() {
    }

    /**
     * Reads the first number in {@code raw} as a price rounded to 2 decimals.
     *
     * <p>A single separator followed by exactly three digits ("1,234" or "1.234") is treated as a
     * thousands separator; anything else is a decimal separator. When both "." and "," appear, the
     * last one is the decimal separator.
     */
    public static Optional<BigDecimal> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = NUMBER.matcher(raw);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String number = matcher.group().replace(" ", "").replace(" ", "");

        int lastDot = number.lastIndexOf('.');
        int lastComma = number.lastIndexOf(',');
        int separatorIndex = Math.max(lastDot, lastComma);

        String normalized;
        if (separatorIndex < 0) {
            normalized = number;
        } else {
            char separator = number.charAt(separatorIndex);
            char other = separator == '.' ? ',' : '.';
            int digitsAfter = number.length() - separatorIndex - 1;
            boolean otherPresent = number.indexOf(other) >= 0;
            boolean separatorRepeated = number.indexOf(separator) != separatorIndex;
            String integerPart = number.substring(0, separatorIndex);
            boolean decimal = otherPresent
                    || integerPart.equals("0")
                    || (!separatorRepeated && digitsAfter != 3);
            if (decimal) {
                normalized = integerPart.replace(".", "").replace(",", "")
                        + "." + number.substring(separatorIndex + 1);
            } else {
                normalized = number.replace(".", "").replace(",", "");
            }
        }

        try {
            BigDecimal value = new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
            if (value.signum() <= 0 || value.compareTo(MAX_PRICE) > 0) {
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Finds an ISO currency code ("USD") or a well-known symbol ("$") in the text. */
    public static Optional<String> detectCurrency(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = ISO_CODE.matcher(raw);
        while (matcher.find()) {
            if (isCurrencyCode(matcher.group())) {
                return Optional.of(matcher.group());
            }
        }
        for (Map.Entry<String, String> symbol : SYMBOLS.entrySet()) {
            if (raw.contains(symbol.getKey())) {
                return Optional.of(symbol.getValue());
            }
        }
        return Optional.empty();
    }

    /** Returns the upper-cased ISO code if {@code code} is a real currency, otherwise null. */
    public static String normalizeCurrency(String code) {
        if (code == null) {
            return null;
        }
        String upper = code.trim().toUpperCase(Locale.ROOT);
        return isCurrencyCode(upper) ? upper : null;
    }

    private static boolean isCurrencyCode(String code) {
        try {
            Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
