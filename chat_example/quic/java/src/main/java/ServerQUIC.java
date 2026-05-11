/**
 * ServerQUIC.java — Java QUIC Chat Server
 * Computer Networks Lab
 *
 * Build:  cd quic_java && mvn package -q
 * Run:    java -cp target/quic-chat-1.0-jar-with-dependencies.jar ServerQUIC
 */

import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicStream;
import tech.kwik.core.ConnectionListener;
import tech.kwik.core.ConnectionTerminatedEvent;
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

// ── Entry point ───────────────────────────────────────────────────────────────
public class ServerQUIC {
    public static void main(String[] args) throws Exception {
        new QuicChatServer(4434).start();
    }
}

// ── QuicChatServer ────────────────────────────────────────────────────────────
/**
 * Builds and starts the QUIC server.
 * Generates a TLS certificate, registers the "chat" ALPN protocol,
 * then waits for clients indefinitely.
 */
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

        // ALPN: both client and server must agree on the same protocol name
        server.registerApplicationProtocol("chat", new ChatProtocolFactory(registry));
        server.start();

        System.out.println("[server] listening on port " + port + "  (QUIC / UDP)");
        System.out.println("[server] waiting for clients ...\n");

        Thread.currentThread().join();   // keep main thread alive
    }
}

// ── CertificateGenerator ──────────────────────────────────────────────────────
/**
 * Generates a temporary self-signed TLS certificate using keytool (bundled
 * with every JDK). QUIC mandates TLS 1.3 — this is protocol plumbing.
 */
class CertificateGenerator {

    KeyStore generate() throws Exception {
        File ksFile = new File("chat-server.p12");

        if (!ksFile.exists()) {
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
            new BufferedReader(new InputStreamReader(p.getInputStream()))
                    .lines().forEach(l -> {});   // drain output
            int exit = p.waitFor();
            if (exit != 0) throw new RuntimeException("keytool failed (exit " + exit + ")");
        }

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(ksFile)) {
            ks.load(fis, "password".toCharArray());
        }
        return ks;
    }
}

// ── SessionRegistry ───────────────────────────────────────────────────────────
/**
 * Thread-safe registry of all active QUIC sessions.
 * Provides register / unregister / broadcast operations.
 */
class SessionRegistry {

    private final ConcurrentHashMap<Integer, ChatSession> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);

    int register(ChatSession session) {
        int id = idCounter.incrementAndGet();
        sessions.put(id, session);
        return id;
    }

    void unregister(int id) {
        sessions.remove(id);
    }

    int size() {
        return sessions.size();
    }

    /** Push message to every session except the sender. */
    void broadcast(String message, int excludeId) {
        System.out.println("[server] " + message);
        for (Map.Entry<Integer, ChatSession> entry : sessions.entrySet()) {
            if (entry.getKey() == excludeId) continue;
            entry.getValue().send(message);
        }
    }
}

// ── ChatProtocolFactory ───────────────────────────────────────────────────────
/**
 * kwik calls createConnection() once for every incoming QUIC connection.
 * Returns a ChatSession that handles that connection.
 */
class ChatProtocolFactory implements ApplicationProtocolConnectionFactory {

    private final SessionRegistry registry;

    ChatProtocolFactory(SessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ApplicationProtocolConnection createConnection(String protocol,
                                                          QuicConnection quicConnection) {
        ChatSession session = new ChatSession(quicConnection, registry);
        int id = registry.register(session);
        session.setConnId(id);
        System.out.println("[server] client #" + id
                + " connected  (active=" + registry.size() + ")");
        return session;
    }

    @Override
    public int maxConcurrentPeerInitiatedBidirectionalStreams() { return 10; }
}

// ── ChatSession ───────────────────────────────────────────────────────────────
/**
 * Represents one connected QUIC client.
 *
 * acceptPeerInitiatedStream() — called by kwik when the client opens a stream;
 *                               reads lines and broadcasts them.
 * send()                      — opens a server-initiated unidirectional stream
 *                               and pushes a broadcast message to this client.
 */
class ChatSession implements ApplicationProtocolConnection {

    private final QuicConnection connection;
    private final SessionRegistry registry;
    private int    connId;
    private String username = null;

    ChatSession(QuicConnection connection, SessionRegistry registry) {
        this.connection = connection;
        this.registry   = registry;
        connection.setConnectionListener(new DisconnectListener());
    }

    void setConnId(int id) {
        this.connId = id;
    }

    /** Called by kwik when the client opens a new stream toward the server. */
    @Override
    public void acceptPeerInitiatedStream(QuicStream stream) {
        Thread t = new Thread(new StreamReader(stream));
        t.setDaemon(true);
        t.start();
    }

    /** Open a server-initiated unidirectional stream and write the message. */
    void send(String message) {
        try {
            QuicStream stream = connection.createStream(false);   // unidirectional
            OutputStream out = stream.getOutputStream();
            out.write((message + "\n").getBytes());
            out.close();
        } catch (IOException e) {
            // Client may have already disconnected
        }
    }

    // ── inner: reads lines from one client stream ─────────────────────────────
    private class StreamReader implements Runnable {

        private final QuicStream stream;

        StreamReader(QuicStream stream) { this.stream = stream; }

        @Override
        public void run() {
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(stream.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (username == null) {
                        username = line;
                        System.out.println("[server] client #" + connId
                                + " -> name '" + username + "'");
                        registry.broadcast("*** " + username + " joined the chat ***", connId);
                    } else {
                        registry.broadcast(username + ": " + line, connId);
                    }
                }
            } catch (IOException e) {
                // Stream closed — normal on disconnect
            }
        }
    }

    // ── inner: handles QUIC connection close events ───────────────────────────
    private class DisconnectListener implements ConnectionListener {

        @Override
        public void disconnected(ConnectionTerminatedEvent event) {
            registry.unregister(connId);
            String name = (username != null) ? username : "#" + connId;
            System.out.println("[server] " + name
                    + " disconnected  (active=" + registry.size() + ")");
            registry.broadcast("*** " + name + " left the chat ***", connId);
        }
    }
}
