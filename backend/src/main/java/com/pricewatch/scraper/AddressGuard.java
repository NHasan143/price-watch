package com.pricewatch.scraper;

import com.pricewatch.scraper.ScrapeFailedException.Reason;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.function.Predicate;

/**
 * Keeps product links from reaching the server's own network (server-side request forgery). A link may
 * only lead to a public internet address: loopback, private (RFC 1918), link-local (which includes cloud
 * metadata at 169.254.169.254), unique-local, carrier-grade NAT and other special-purpose ranges are
 * refused, whatever name or notation the link uses for them, because the check is made on the resolved
 * addresses. A name that resolves to several addresses must resolve to public ones only.
 *
 * <p>Java caches a lookup for 30 seconds, so a download right after {@link #check(URL)} connects to the
 * addresses checked here, even if the name would answer differently a moment later. Headless Chrome
 * resolves names itself, so it is sent through {@link GuardedProxy}, which connects only to the addresses
 * it checked.
 *
 * <p>Set {@code pricewatch.scraper.allow-private-addresses=true} to try PriceWatch against a test shop
 * running on your own machine or network. Never on a server others can reach.
 */
@Component
public class AddressGuard {

    private static final Logger log = LoggerFactory.getLogger(AddressGuard.class);

    static final String PRIVATE_ADDRESS = "That link leads to a private or local network address, which PriceWatch "
            + "does not visit. Use the shop's public product link.";

    /** Looks up a host name; replaced in tests. */
    @FunctionalInterface
    interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final Predicate<InetAddress> allowed;
    private final Resolver resolver;

    @Autowired
    public AddressGuard(@Value("${pricewatch.scraper.allow-private-addresses:false}") boolean allowPrivateAddresses) {
        this(allowPrivateAddresses ? address -> true : AddressGuard::isPublic, InetAddress::getAllByName);
    }

    AddressGuard(Predicate<InetAddress> allowed, Resolver resolver) {
        this.allowed = allowed;
        this.resolver = resolver;
    }

    /**
     * Checks a link as the plain download will read it.
     *
     * @throws ScrapeFailedException {@link Reason#INVALID} for a malformed link, one that is not http(s) or
     *         one that leads to a non-public address; {@link Reason#TEMPORARY} when the host cannot be found
     */
    public void check(String url) {
        URL parsed;
        try {
            parsed = Jsoup.connect(url).request().url();
        } catch (IllegalArgumentException e) {
            throw new ScrapeFailedException(Reason.INVALID, "That does not look like a valid URL.");
        }
        check(parsed);
    }

    /** @see #check(String) */
    public void check(URL url) {
        String protocol = url.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            throw new ScrapeFailedException(Reason.INVALID, "Only http and https links can be checked.");
        }
        if (url.getHost() == null || url.getHost().isEmpty()) {
            throw new ScrapeFailedException(Reason.INVALID, "That does not look like a valid URL.");
        }
        resolve(url.getHost());
    }

    /**
     * Resolves a host and returns its addresses, all of them allowed.
     *
     * @throws ScrapeFailedException {@link Reason#INVALID} when any address is not allowed,
     *         {@link Reason#TEMPORARY} when the host cannot be found
     */
    public InetAddress[] resolve(String host) {
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new ScrapeFailedException(Reason.TEMPORARY,
                    "Could not find the server " + host + ". Check the link and your internet connection.");
        }
        if (addresses.length == 0 || !Arrays.stream(addresses).allMatch(allowed)) {
            log.warn("Refused to visit {}: it resolves to {}, which is not a public address", host,
                    Arrays.toString(addresses));
            throw new ScrapeFailedException(Reason.INVALID, PRIVATE_ADDRESS);
        }
        return addresses;
    }

    /** True for an address on the public internet; false for every local, private and special-purpose range. */
    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            return isPublicIpv4(b);
        }
        if ((b[0] & 0xfe) == 0xfc) {
            return false; // fc00::/7 unique local (AWS metadata is fd00:ec2::254)
        }
        if (b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0d && (b[3] & 0xff) == 0xb8) {
            return false; // 2001:db8::/32 documentation
        }
        if (b[0] == 0x20 && b[1] == 0x02) {
            return isPublicIpv4(Arrays.copyOfRange(b, 2, 6)); // 2002::/16 6to4 carries an IPv4 address
        }
        if (isZero(b, 0, 10) && ((b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff || isZero(b, 10, 12))) {
            return isPublicIpv4(Arrays.copyOfRange(b, 12, 16)); // ::ffff:a.b.c.d mapped, ::a.b.c.d compatible
        }
        if (b[0] == 0 && b[1] == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b && isZero(b, 4, 12)) {
            return isPublicIpv4(Arrays.copyOfRange(b, 12, 16)); // 64:ff9b::/96 NAT64
        }
        return true;
    }

    private static boolean isPublicIpv4(byte[] b) {
        int first = b[0] & 0xff;
        int second = b[1] & 0xff;
        int third = b[2] & 0xff;
        return !(first == 0                                     // 0.0.0.0/8 "this network"
                || first == 10                                  // 10.0.0.0/8 private
                || first == 127                                 // 127.0.0.0/8 loopback
                || first == 100 && (second & 0xc0) == 64        // 100.64.0.0/10 carrier-grade NAT
                || first == 169 && second == 254                // 169.254.0.0/16 link-local, cloud metadata
                || first == 172 && (second & 0xf0) == 16        // 172.16.0.0/12 private
                || first == 192 && second == 0 && third == 0    // 192.0.0.0/24 protocol assignments
                || first == 192 && second == 0 && third == 2    // 192.0.2.0/24 documentation
                || first == 192 && second == 168                // 192.168.0.0/16 private
                || first == 198 && (second & 0xfe) == 18        // 198.18.0.0/15 benchmarking
                || first == 198 && second == 51 && third == 100 // 198.51.100.0/24 documentation
                || first == 203 && second == 0 && third == 113  // 203.0.113.0/24 documentation
                || first >= 224);                               // multicast, reserved, broadcast
    }

    private static boolean isZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
