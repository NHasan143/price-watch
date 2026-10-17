package com.pricewatch.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.JavascriptExecutor;
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

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Headless Chrome, started on first use and reused for later pages. The page is re-read every
 * {@value #POLL_MS} ms until the price shows up, so fast pages return quickly. Before handing the
 * page over, the largest image near the top is marked as the product photo for
 * {@link ProductImageFinder}. One page at a time: Selenium drivers are not thread-safe.
 *
 * <p>All of Chrome's traffic goes through {@link GuardedProxy}, so neither the page nor anything it loads
 * can reach the server's own network.
 */
@Component
public class ChromePageRenderer implements PageRenderer, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ChromePageRenderer.class);
    private static final long POLL_MS = 750;

    /** Marks the biggest photo in the first screens: what a shopper sees as the product image. */
    private static final String MARK_HERO_IMAGE = """
            let best = null, bestArea = 0;
            for (const img of document.images) {
              const src = img.currentSrc || img.src;
              if (!/^https?:/.test(src) || /\\.svg(\\?|$)/i.test(src)) continue;
              const box = img.getBoundingClientRect();
              if (box.top + window.scrollY > 1400 || box.width < 120 || box.height < 120) continue;
              const area = box.width * box.height;
              if (area > bestArea) { best = img; bestArea = area; }
            }
            if (best) {
              best.setAttribute('%s', 'true');
              best.setAttribute('src', best.currentSrc || best.src);
            }
            """.formatted(ProductImageFinder.HERO_ATTRIBUTE);

    private final boolean enabled;
    private final long timeoutMs;
    private final AddressGuard guard;
    private GuardedProxy proxy;
    private WebDriver driver;

    public ChromePageRenderer(
            @Value("${pricewatch.renderer.enabled:true}") boolean enabled,
            @Value("${pricewatch.renderer.timeout-ms:30000}") long timeoutMs,
            AddressGuard guard) {
        this.enabled = enabled;
        this.timeoutMs = timeoutMs;
        this.guard = guard;
    }

    @Override
    public synchronized Optional<Document> render(String url, Predicate<Document> ready) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            guard.check(url);
        } catch (ScrapeFailedException e) {
            log.warn("Headless Chrome did not open {}: {}", url, e.getMessage());
            return Optional.empty();
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        try {
            WebDriver browser = driver();
            browser.get(url);
            // Chrome shows its own error page when the shop refuses the connection (bot protection,
            // network errors). There is no product on it, so report the failure instead.
            if (read(browser).selectFirst("#main-frame-error, body.neterror") != null) {
                log.warn("Headless Chrome could not load {}: the site refused the connection", url);
                browser.get("about:blank");
                return Optional.empty();
            }
            while (!ready.test(read(browser)) && System.currentTimeMillis() < deadline) {
                Thread.sleep(POLL_MS);
            }
            ((JavascriptExecutor) browser).executeScript(MARK_HERO_IMAGE);
            Document page = read(browser);
            browser.get("about:blank");
            return Optional.of(page);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (WebDriverException e) {
            log.warn("Headless Chrome could not load {}: {}", url, e.getMessage().lines().findFirst().orElse(""));
            quit();
            return Optional.empty();
        } catch (IOException e) {
            log.warn("Headless Chrome could not start its proxy: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static Document read(WebDriver browser) {
        return Jsoup.parse(browser.getPageSource(), browser.getCurrentUrl());
    }

    private WebDriver driver() throws IOException {
        if (proxy == null) {
            proxy = new GuardedProxy(guard);
        }
        if (driver == null) {
            // Shops with bot protection (Best Buy, for example) reject a browser whose user agent says
            // "HeadlessChrome" or claims a different Chrome version than the one actually running.
            // So start once to read the real user agent, then restart with only "Headless" removed.
            WebDriver probe = launch(null);
            String realAgent = String.valueOf(((JavascriptExecutor) probe).executeScript("return navigator.userAgent"));
            if (realAgent.contains("HeadlessChrome")) {
                probe.quit();
                driver = launch(realAgent.replace("HeadlessChrome", "Chrome"));
            } else {
                driver = probe;
            }
            log.info("Started headless Chrome for JavaScript-rendered pages");
        }
        return driver;
    }

    private WebDriver launch(String userAgent) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new",
                "--disable-gpu",
                "--disable-dev-shm-usage",
                "--no-sandbox",
                "--window-size=1366,900",
                "--lang=en-US",
                // Every connection, loopback included, goes through the proxy that refuses private addresses,
                // and Chrome looks up no names itself (SOCKS5 sends the host name to the proxy).
                "--proxy-server=socks5://127.0.0.1:" + proxy.port(),
                "--proxy-bypass-list=<-loopback>",
                "--host-resolver-rules=MAP * ~NOTFOUND , EXCLUDE 127.0.0.1",
                "--force-webrtc-ip-handling-policy=disable_non_proxied_udp");
        if (userAgent != null) {
            options.addArguments("--user-agent=" + userAgent);
        }
        // Hand the page over once the DOM is ready; polling in render() waits for the price itself.
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        WebDriver browser = new ChromeDriver(options);
        browser.manage().timeouts().pageLoadTimeout(Duration.ofMillis(timeoutMs));
        return browser;
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
        if (proxy != null) {
            proxy.close();
            proxy = null;
        }
    }
}
