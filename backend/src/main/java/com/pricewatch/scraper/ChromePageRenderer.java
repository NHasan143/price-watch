package com.pricewatch.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Headless Chrome, started on first use and reused for later pages. Images are not loaded, and the
 * page is re-read every {@value #POLL_MS} ms until the price shows up, so fast pages return quickly.
 * One page at a time: Selenium drivers are not thread-safe.
 */
@Component
public class ChromePageRenderer implements PageRenderer, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ChromePageRenderer.class);
    private static final long POLL_MS = 750;

    private final boolean enabled;
    private final long timeoutMs;
    private final String userAgent;
    private WebDriver driver;

    public ChromePageRenderer(
            @Value("${pricewatch.renderer.enabled:true}") boolean enabled,
            @Value("${pricewatch.renderer.timeout-ms:30000}") long timeoutMs,
            @Value("${pricewatch.scraper.user-agent:Mozilla/5.0 (compatible; PriceWatch/1.0)}") String userAgent) {
        this.enabled = enabled;
        this.timeoutMs = timeoutMs;
        this.userAgent = userAgent;
    }

    @Override
    public synchronized Optional<Document> render(String url, Predicate<Document> ready) {
        if (!enabled) {
            return Optional.empty();
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        try {
            WebDriver browser = driver();
            browser.get(url);
            Document page;
            do {
                page = Jsoup.parse(browser.getPageSource(), browser.getCurrentUrl());
                if (ready.test(page)) {
                    break;
                }
                Thread.sleep(POLL_MS);
            } while (System.currentTimeMillis() < deadline);
            browser.get("about:blank");
            return Optional.of(page);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (WebDriverException e) {
            log.warn("Headless Chrome could not load {}: {}", url, e.getMessage().lines().findFirst().orElse(""));
            quit();
            return Optional.empty();
        }
    }

    private WebDriver driver() {
        if (driver == null) {
            ChromeOptions options = new ChromeOptions();
            options.addArguments(
                    "--headless=new",
                    "--disable-gpu",
                    "--disable-dev-shm-usage",
                    "--no-sandbox",
                    "--window-size=1366,900",
                    "--lang=en-US",
                    "--blink-settings=imagesEnabled=false",
                    "--user-agent=" + userAgent);
            // Hand the page over once the DOM is ready; polling above waits for the price itself.
            options.setPageLoadStrategy(PageLoadStrategy.EAGER);
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofMillis(timeoutMs));
            log.info("Started headless Chrome for JavaScript-rendered pages");
        }
        return driver;
    }

    private void quit() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (WebDriverException e) {
                log.debug("Chrome was already gone: {}", e.getMessage());
            }
            driver = null;
        }
    }

    @Override
    public synchronized void destroy() {
        quit();
    }
}
