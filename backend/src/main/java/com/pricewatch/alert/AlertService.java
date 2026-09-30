package com.pricewatch.alert;

import com.pricewatch.product.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Tells the user that a product dropped to (or below) their target price. Always logs the alert;
 * also sends an email when SMTP ({@code spring.mail.*}) and {@code pricewatch.alert.to} are set.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String to;
    private final String from;

    public AlertService(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${pricewatch.alert.to:}") String to,
            @Value("${pricewatch.alert.from:pricewatch@localhost}") String from) {
        this.mailSender = mailSender;
        this.to = to;
        this.from = from;
    }

    public void sendPriceDrop(Product product) {
        String current = format(product.getCurrentPrice(), product.getCurrency());
        String target = format(product.getTargetPrice(), product.getCurrency());
        String subject = "Price drop: " + product.getName() + " is now " + current;
        String body = product.getName() + " dropped to " + current + " (your target: " + target + ").\n\n"
                + product.getUrl() + "\n";

        log.info("PRICE ALERT - {}", subject);

        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || to == null || to.isBlank()) {
            log.info("Email is not configured (set spring.mail.* and pricewatch.alert.to) - alert was only logged.");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            log.info("Alert email sent to {}", to);
        } catch (MailException e) {
            log.warn("Could not send alert email: {}", e.getMessage());
        }
    }

    private static String format(BigDecimal amount, String currency) {
        String value = String.format(Locale.ROOT, "%.2f", amount);
        return currency == null ? value : currency + " " + value;
    }
}
