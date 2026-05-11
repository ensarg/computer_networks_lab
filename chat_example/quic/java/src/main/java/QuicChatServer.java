/**
 * QuicChatServer.java
 * Builds and starts the QUIC server.
 * Generates a TLS certificate, registers the "chat" ALPN protocol,
 * then waits for clients indefinitely.
 */

import tech.kwik.core.log.NullLogger;
import tech.kwik.core.server.ServerConnector;

import java.security.KeyStore;

class QuicChatServer {

    private final int port;
    private final SessionRegistry registry = new SessionRegistry();

    QuicChatServer(int port) {
        this.port = port;
    }

    void start() throws Exception {
        KeyStore keyStore = new CertificateGenerator().generate();

        ServerConnector server = ServerConnector.builder()
                .withPort(port)
                .withKeyStore(keyStore, "chat-server", "password".toCharArray())
                .withLogger(new NullLogger())
                .build();

        server.registerApplicationProtocol("chat", new ChatProtocolFactory(registry));
        server.start();

        System.out.println("[server] listening on port " + port + "  (QUIC / UDP)");
        System.out.println("[server] waiting for clients ...\n");

        Thread.currentThread().join();   // keep main thread alive
    }
}
