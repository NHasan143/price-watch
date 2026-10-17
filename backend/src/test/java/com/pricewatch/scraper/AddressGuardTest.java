package com.pricewatch.scraper;

import com.pricewatch.scraper.ScrapeFailedException.Reason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddressGuardTest {

    /** A guard whose DNS answers come from the map instead of the network. */
    private static AddressGuard guard(Map<String, String[]> dns) {
        return new AddressGuard(AddressGuard::isPublic, host -> {
            String[] answer = dns.get(host);
            if (answer == null) {
                throw new UnknownHostException(host);
            }
            InetAddress[] addresses = new InetAddress[answer.length];
            for (int i = 0; i < answer.length; i++) {
                addresses[i] = InetAddress.getByName(answer[i]);
            }
            return addresses;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0.0.0.0", "0.1.2.3",                       // this network
            "127.0.0.1", "127.255.255.254",             // loopback
            "10.0.0.1", "10.255.255.255",               // private
            "172.16.0.1", "172.31.255.255",             // private
            "192.168.0.1", "192.168.255.255",           // private
            "169.254.169.254", "169.254.0.1",           // link-local, cloud metadata
            "100.64.0.1", "100.100.100.200",            // carrier-grade NAT (Alibaba Cloud metadata)
            "192.0.0.192",                              // protocol assignments (Oracle Cloud metadata)
            "198.18.0.1", "192.0.2.1", "203.0.113.5",   // benchmarking, documentation
            "224.0.0.1", "240.0.0.1", "255.255.255.255", // multicast, reserved, broadcast
            "::", "::1",                                // unspecified, loopback
            "fe80::1",                                  // link-local
            "fc00::1", "fd00:ec2::254",                 // unique local (AWS metadata over IPv6)
            "fec0::1",                                  // old site-local
            "ff02::1",                                  // multicast
            "::ffff:127.0.0.1", "::ffff:10.0.0.1",      // IPv4-mapped
            "::169.254.169.254",                        // IPv4-compatible
            "64:ff9b::a9fe:a9fe",                       // NAT64 of 169.254.169.254
            "2002:c0a8:0101::1",                        // 6to4 of 192.168.1.1
            "2001:db8::1"                               // documentation
    })
    void refusesEveryNonPublicRange(String address) throws Exception {
        assertThat(AddressGuard.isPublic(InetAddress.getByName(address))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "8.8.8.8", "93.184.215.14", "1.1.1.1",
            "172.15.255.255", "172.32.0.1",             // just outside 172.16.0.0/12
            "100.63.255.255", "100.128.0.1",            // just outside 100.64.0.0/10
            "169.253.0.1", "192.169.0.1", "11.0.0.1",
            "2606:4700:4700::1111", "2a00:1450:4001::200e",
            "64:ff9b::808:808",                         // NAT64 of 8.8.8.8
            "2002:0808:0808::1"                         // 6to4 of 8.8.8.8
    })
    void allowsPublicAddresses(String address) throws Exception {
        assertThat(AddressGuard.isPublic(InetAddress.getByName(address))).isTrue();
    }

    @Test
    void checksTheAddressANameResolvesTo() {
        AddressGuard guard = guard(Map.of(
                "shop.example", new String[] {"93.184.215.14"},
                "intranet.example", new String[] {"10.0.0.5"}));

        assertThatCode(() -> guard.check("https://shop.example/p/1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.check("https://intranet.example/p/1"))
                .isInstanceOfSatisfying(ScrapeFailedException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.INVALID))
                .hasMessage(AddressGuard.PRIVATE_ADDRESS);
    }

    @Test
    void refusesANameWhenAnyOfItsAddressesIsPrivate() {
        AddressGuard guard = guard(Map.of("mixed.example", new String[] {"93.184.215.14", "127.0.0.1"}));

        assertThatThrownBy(() -> guard.check("http://mixed.example/"))
                .hasMessage(AddressGuard.PRIVATE_ADDRESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:8080/actuator", "http://169.254.169.254/latest/meta-data/",
            "http://[::1]/", "http://[::ffff:7f00:1]/", "HTTP://192.168.1.1/", "http://user@10.0.0.1/"})
    void refusesAddressLiteralsInAnyNotation(String url) {
        assertThatThrownBy(() -> new AddressGuard(false).check(url))
                .isInstanceOfSatisfying(ScrapeFailedException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.INVALID))
                .hasMessage(AddressGuard.PRIVATE_ADDRESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "ftp://shop.example/", "jar:http://shop.example/a.jar!/", "not a url"})
    void refusesLinksThatAreNotHttp(String url) {
        assertThatThrownBy(() -> guard(Map.of()).check(url))
                .isInstanceOfSatisfying(ScrapeFailedException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.INVALID));
    }

    @Test
    void reportsAnUnknownHostAsTemporary() {
        assertThatThrownBy(() -> guard(Map.of()).check("https://gone.example/"))
                .isInstanceOfSatisfying(ScrapeFailedException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.TEMPORARY))
                .hasMessageContaining("Could not find the server gone.example");
    }

    @Test
    void allowsPrivateAddressesOnlyWhenConfiguredTo() {
        assertThatCode(() -> new AddressGuard(true).check("http://127.0.0.1:8080/p/1")).doesNotThrowAnyException();
    }
}
