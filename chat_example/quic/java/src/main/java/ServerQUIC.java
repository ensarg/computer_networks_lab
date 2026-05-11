/**
 * ServerQUIC.java — Java QUIC Chat Server
 * Computer Networks Lab
 *
 * Listens for QUIC client connections on UDP port 4434.
 * Every message received from one client is broadcast to all other clients.
 *
 * Build:  cd quic_java && mvn package -q
 * Run:    java -cp target/quic-chat-1.0-jar-with-dependencies.jar ServerQUIC
 */

import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicStream;
import tech.kwik.core.log.NullLogger;
import tech.kwik.core.server.ApplicationProtocolConnection;
import tech.kwik.core.server.ApplicationProtocolConnectionFactory;
import tech.kwik.core.server.ServerConnector;

import java.io.*;
import java.net.SocketException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerQUIC {

    static final int PORT = 4434;

    // ALPN (Application Layer Protocol Negotiation) label.
    // QUIC requires both sides to agree on a protocol name during the TLS handshake.
    static final String ALPN = "chat";

    // Thread-safe registry: connection id → ChatSession
    static final Map<Integer, ChatSession> sessions = new ConcurrentHashMap<>();
    static final AtomicInteger idCounter = new AtomicInteger(0);

    // ── broadcast to every session except the sender ─────────────────────────
    static void broadcast(String message, int excludeId) {
        System.out.println("[server] " + message);
        for (Map.Entry<Integer, ChatSession> entry : sessions.entrySet()) {
            if (entry.getKey() == excludeId) continue;
            entry.getValue().send(message);
        }
    }

    // ── entry point ───────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {

        // Generate a temporary TLS certificate so QUIC can do its handshake.
        // Students: this block is protocol plumbing — you can ignore it.
        KeyStore keyStore = generateKeyStore();

        ServerConnector server = ServerConnector.builder()
                .withPort(PORT)
                .withKeyStore(keyStore, "chat-server", "password".toCharArray())
                .withLogger(new NullLogger())   // suppress kwik's internal log output
                .build();

        // Register our "chat" ALPN — the factory creates one ChatSession per connection
        server.registerApplicationProtocol(ALPN, new ChatProtocolFactory());
        server.start();

        System.out.println("[server] listening on port " + PORT + "  (QUIC / UDP)");
        System.out.println("[server] waiting for clients …\n");

        // Keep the main thread alive
        Thread.currentThread().join();
    }

    // ── certificate generation (keytool, bundled in every JDK) ───────────────
    private static KeyStore generateKeyStore() throws Exception {
        File ksFile = new File("chat-server.p12");

        if (!ksFile.exists()) {
            // keytool ships with every JDK — no extra dependency needed
            ProcessBuilder pb = new ProcessBuilder(
                    "keytool", "-genkeypair",
                    "-keystore", ksFile.getAbsolutePath(),
                    "-storetype", "PKCS12",
                    "-storepass", "password",
                    "-keypass",   "password",
                    "-alias",     "chat-server",
                    "-keyalg",    "EC",
                    "-groupname", "secp256r1",
                    "-dname",     "CN=localhost",
                    "-validity",  "1"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // Drain output so the process doesn't block
            new BufferedReader(new InputStreamReader(p.getInputStream()))
                    .lines().forEach(l -> {});
            int exitCode = p.waitFor();
            if (exitCode != 0) throw new RuntimeException("keytool failed (exit " + exitCode + ")");
        }

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(ksFile)) {
            ks.load(fis, "password".toCharArray());
        }
        return ks;
    }


    // ── ApplicationProtocolConnectionFactory ─────────────────────────────────
    // kwik calls createConnection() once per incoming QUIC connection.

    static class ChatProtocolFactory implements ApplicationProtocolConnectionFactory {

        @Override
        public ApplicationProtocolConnection createConnection(String protocol, QuicConnection quicConnection) {
            int id = idCounter.incrementAndGet();
            ChatSession session = new ChatSession(id, quicConnection);
            sessions.put(id, session);
            System.out.println("[server] client #" + id + " connected  (active=" + sessions.size() + ")");
            return session;
        }

        // Tell kwik we accept up to 10 concurrent client-initiated bidirectional streams
        @Override
        public int maxConcurrentPeerInitiatedBidirectionalStreams() { return 10; }
    }


    // ── per-connection chat handler ───────────────────────────────────────────

    /**
     * One ChatSession per connected client.
     *
     * acceptPeerInitiatedStream() is called by kwik when the client opens a
     * new bidirectional stream — that is where we receive chat messages.
     *
     * send() opens a server-initiated unidirectional stream and pushes a
     * broadcast message to this client.
     */
    static class ChatSession implements ApplicationProtocolConnection {

        private final int connId;
        private final QuicConnection connection;   // the underlying QUIC connection
        private String username = null;

        ChatSession(int connId, QuicConnection connection) {
            this.connId = connId;
            this.connection = connection;

            // Called when the QUIC connection closes (client disconnected)
            connection.setConnectionListener(new tech.kwik.core.ConnectionListener() {
                @Override
                public void disconnected(tech.kwik.core.ConnectionTerminatedEvent event) {
                    sessions.remove(connId);
                    String name = (username != null) ? username : "#" + connId;
                    System.out.println("[server] " + name
                            + " disconnected  (active=" + sessions.size() + ")");
                    broadcast("*** " + name + " left the chat ***", connId);
                }
            });
        }

        /**
         * kwik calls this when the CLIENT opens a new stream toward the server.
         * We read lines from it and broadcast them to all other clients.
         */
        @Override
        public void acceptPeerInitiatedStream(QuicStream stream) {
            // Handle this stream on a separate thread so we don't block kwik's internals
            Thread t = new Thread(() -> {
                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(stream.getInputStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) continue;

                        if (username == null) {
                            // First message is the display name
                            username = line;
                            System.out.println("[server] client #" + connId
                                    + " → name '" + username + "'");
                            broadcast("*** " + username + " joined the chat ***", connId);
                        } else {
                            broadcast(username + ": " + line, connId);
                        }
                    }
                } catch (IOException e) {
                    // Stream closed — normal on disconnect
                }
            });
            t.setDaemon(true);
            t.start();
        }

        /**
         * Send a broadcast message to THIS client by opening a new
         * server-initiated unidirectional stream.
         */
        void send(String message) {
            try {
                // false = unidirectional (server → client only)
                QuicStream stream = connection.createStream(false);
                OutputStream out = stream.getOutputStream();
                out.write((message + "\n").getBytes());
                out.close();   // signals end-of-stream to the client
            } catch (IOException e) {
                // Client may have disconnected; ignore
            }
        }
    }
}
