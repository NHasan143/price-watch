package com.pricewatch.scraper;

import com.pricewatch.scraper.ScrapeFailedException.Reason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A minimal SOCKS5 proxy on the loopback interface that headless Chrome sends all its traffic through.
 * Chrome hands over the host name, the proxy resolves it with {@link AddressGuard} and connects only to
 * the addresses it checked. So a rendered page cannot reach the server's own network: not by redirecting,
 * not from a script, an iframe or a WebSocket, and not with a name that answers differently the second
 * time it is looked up.
 */
final class GuardedProxy implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GuardedProxy.class);
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int IDLE_TIMEOUT_MS = 120_000;

    private static final int VERSION = 5;
    private static final int NO_AUTHENTICATION = 0;
    private static final int CONNECT = 1;
    private static final int IPV4 = 1;
    private static final int DOMAIN = 3;
    private static final int IPV6 = 4;
    private static final int SUCCEEDED = 0;
    private static final int NOT_ALLOWED = 2;
    private static final int HOST_UNREACHABLE = 4;
    private static final int CONNECTION_REFUSED = 5;
    private static final int COMMAND_NOT_SUPPORTED = 7;
    private static final int ADDRESS_TYPE_NOT_SUPPORTED = 8;

    private final AddressGuard guard;
    private final ServerSocket server;
    private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();

    GuardedProxy(AddressGuard guard) throws IOException {
        this.guard = guard;
        this.server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        threads.execute(this::acceptConnections);
    }

    int port() {
        return server.getLocalPort();
    }

    private void acceptConnections() {
        while (!server.isClosed()) {
            try {
                Socket client = server.accept();
                threads.execute(() -> serve(client));
            } catch (IOException e) {
                if (!server.isClosed()) {
                    log.debug("Proxy could not accept a connection: {}", e.getMessage());
                }
            }
        }
    }

    private void serve(Socket client) {
        try (client) {
            client.setSoTimeout(CONNECT_TIMEOUT_MS);
            DataInputStream in = new DataInputStream(client.getInputStream());
            OutputStream out = client.getOutputStream();

            // Greeting: version, then the authentication methods the client offers.
            if (in.readUnsignedByte() != VERSION) {
                return;
            }
            byte[] methods = new byte[in.readUnsignedByte()];
            in.readFully(methods);
            if (!offers(methods, NO_AUTHENTICATION)) {
                out.write(new byte[] {VERSION, (byte) 0xff});
                return;
            }
            out.write(new byte[] {VERSION, NO_AUTHENTICATION});

            // Request: version, command, reserved, address type, address, port.
            int version = in.readUnsignedByte();
            int command = in.readUnsignedByte();
            in.readUnsignedByte();
            String host = readHost(in, in.readUnsignedByte());
            if (host == null) {
                reply(out, ADDRESS_TYPE_NOT_SUPPORTED);
                return;
            }
            int port = in.readUnsignedShort();
            if (version != VERSION || command != CONNECT) {
                reply(out, COMMAND_NOT_SUPPORTED);
                return;
            }

            InetAddress[] addresses;
            try {
                addresses = guard.resolve(host);
            } catch (ScrapeFailedException e) {
                reply(out, e.getReason() == Reason.INVALID ? NOT_ALLOWED : HOST_UNREACHABLE);
                return;
            }
            try (Socket upstream = connect(addresses, port)) {
                if (upstream == null) {
                    reply(out, CONNECTION_REFUSED);
                    return;
                }
                reply(out, SUCCEEDED);
                client.setSoTimeout(IDLE_TIMEOUT_MS);
                upstream.setSoTimeout(IDLE_TIMEOUT_MS);
                threads.execute(() -> relay(client, upstream));
                relay(upstream, client); // closing both sockets when the site is done ends the other direction
            }
        } catch (IOException e) {
            log.debug("Proxy connection ended: {}", e.getMessage());
        }
    }

    /** The destination as a host name or address literal; null for an address type this proxy does not know. */
    private static String readHost(DataInputStream in, int type) throws IOException {
        byte[] bytes;
        switch (type) {
            case IPV4 -> bytes = new byte[4];
            case IPV6 -> bytes = new byte[16];
            case DOMAIN -> {
                bytes = new byte[in.readUnsignedByte()];
                in.readFully(bytes);
                return new String(bytes, StandardCharsets.US_ASCII);
            }
            default -> {
                return null;
            }
        }
        in.readFully(bytes);
        return InetAddress.getByAddress(bytes).getHostAddress();
    }

    private static Socket connect(InetAddress[] addresses, int port) {
        for (InetAddress address : addresses) {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
                return socket;
            } catch (IOException e) {
                closeQuietly(socket);
            }
        }
        return null;
    }

    private static void relay(Socket from, Socket to) {
        try {
            from.getInputStream().transferTo(to.getOutputStream());
            to.shutdownOutput();
        } catch (IOException e) {
            // one side closed or went idle: the connection is over
        }
    }

    private static void reply(OutputStream out, int status) throws IOException {
        // The bound address is not used by browsers, so it is reported as 0.0.0.0:0.
        out.write(new byte[] {VERSION, (byte) status, 0, IPV4, 0, 0, 0, 0, 0, 0});
    }

    private static boolean offers(byte[] methods, int method) {
        for (byte offered : methods) {
            if (offered == method) {
                return true;
            }
        }
        return false;
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }

    @Override
    public void close() {
        try {
            server.close();
        } catch (IOException ignored) {
            // already closed
        }
        threads.shutdownNow();
    }
}
