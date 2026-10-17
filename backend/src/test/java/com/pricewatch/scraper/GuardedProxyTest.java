package com.pricewatch.scraper;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Connects through the proxy the way Chrome does: SOCKS5, handing over the host name. */
class GuardedProxyTest {

    private HttpServer shop;
    private int shopPort;
    private final AtomicInteger visits = new AtomicInteger();
    private GuardedProxy proxy;

    @BeforeEach
    void startShop() throws IOException {
        shop = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        shop.createContext("/", exchange -> {
            visits.incrementAndGet();
            byte[] body = "<html><title>Lamp</title></html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        shop.start();
        shopPort = shop.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        shop.stop(0);
        if (proxy != null) {
            proxy.close();
        }
    }

    /** 127.0.0.1 plays a public shop; every other address counts as private. */
    private void startProxy(AddressGuard.Resolver dns) throws IOException {
        proxy = new GuardedProxy(new AddressGuard(address -> address.getHostAddress().equals("127.0.0.1"), dns));
    }

    private String get(InetSocketAddress destination) throws IOException {
        try (Socket socket = new Socket(new Proxy(Proxy.Type.SOCKS,
                new InetSocketAddress(InetAddress.getLoopbackAddress(), proxy.port())))) {
            socket.setSoTimeout(5000);
            socket.connect(destination, 5000);
            socket.getOutputStream().write(("GET / HTTP/1.1\r\nHost: shop\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static InetAddress[] address(String literal) throws UnknownHostException {
        return new InetAddress[] {InetAddress.getByName(literal)};
    }

    @Test
    void relaysToAnAllowedHost() throws IOException {
        startProxy(host -> host.equals("shop.test") ? address("127.0.0.1") : address("10.0.0.1"));

        String response = get(InetSocketAddress.createUnresolved("shop.test", shopPort));

        assertThat(response).startsWith("HTTP/1.1 200").contains("<title>Lamp</title>");
    }

    @Test
    void refusesANameThatResolvesToAPrivateAddress() throws IOException {
        startProxy(host -> address("169.254.169.254"));

        assertThatThrownBy(() -> get(InetSocketAddress.createUnresolved("metadata.test", 80)))
                .isInstanceOf(SocketException.class);
    }

    @Test
    void refusesAPrivateAddressLiteral() throws IOException {
        startProxy(InetAddress::getAllByName);

        assertThatThrownBy(() -> get(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80)))
                .isInstanceOf(SocketException.class);
        assertThatThrownBy(() -> get(InetSocketAddress.createUnresolved("[::1]", shopPort)))
                .isInstanceOf(SocketException.class);
        assertThat(visits).hasValue(0);
    }

    @Test
    void connectsToTheAddressItCheckedEvenIfTheNameChangesItsAnswer() throws IOException {
        AtomicInteger lookups = new AtomicInteger();
        // A rebinding name: public on the first lookup, private on every later one.
        startProxy(host -> lookups.incrementAndGet() == 1 ? address("127.0.0.1") : address("10.0.0.1"));

        String response = get(InetSocketAddress.createUnresolved("rebind.test", shopPort));

        assertThat(response).startsWith("HTTP/1.1 200");
        assertThat(lookups).hasValue(1);
    }
}
