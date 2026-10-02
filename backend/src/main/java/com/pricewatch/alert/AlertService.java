package com.pricewatch.alert;

import com.pricewatch.product.Product;
import com.pricewatch.scraper.Availability;
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
 * Tells the owner of a product that it dropped to (or below) their target price, or is back in
 * stock. Always logs the alert; also emails it when SMTP ({@code spring.mail.*}) is set, to the
 * owner's account email, or to {@code pricewatch.alert.to} when that email is not known.
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
        String body = product.getName() + " dropped to " + current + " (your target: " + target + ").\n"
                + (product.getAvailability() == Availability.OUT_OF_STOCK
                        ? "It is sold out right now; you will get another alert when it is back in stock.\n"
                        : "")
                + "\n" + product.getUrl() + "\n";
        send(recipient(product), subject, body);
    }

    public void sendBackInStock(Product product) {
        String current = format(product.getCurrentPrice(), product.getCurrency());
        String target = format(product.getTargetPrice(), product.getCurrency());
        String subject;
        String body;
        if (product.isBelowTarget()) {
            subject = "Back in stock at your price: " + product.getName() + " is " + current;
            body = product.getName() + " is back in stock at " + current + ", at or below your target of "
                    + target + ".\n\n" + product.getUrl() + "\n";
        } else {
            subject = "Back in stock: " + product.getName() + " at " + current;
            body = product.getName() + " is back in stock at " + current + " (your target: " + target + ").\n\n"
                    + product.getUrl() + "\n";
        }
        send(recipient(product), subject, body);
    }

    /** The owner's account email, else the configured fallback address; null when neither is known. */
    String recipient(Product product) {
        if (product.getOwner() != null && product.getOwner().getEmail() != null) {
            return product.getOwner().getEmail();
        }
        return to == null || to.isBlank() ? null : to;
    }

    private void send(String to, String subject, String body) {
        log.info("PRICE ALERT - {}", subject);

        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || to == null) {
            log.info("Email is not configured (set spring.mail.*, and pricewatch.alert.to for owners without a known "
                    + "email) - alert was only logged.");
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
