/**
 * ClientQUIC.java — Java QUIC Chat Client
 * Computer Networks Lab
 *
 * Connects to the QUIC chat server on port 4434.
 *
 * Build:  cd quic_java && mvn package -q
 * Run:    java -cp target/quic-chat-1.0-jar-with-dependencies.jar ClientQUIC
 */

import tech.kwik.core.QuicClientConnection;
import tech.kwik.core.QuicStream;

import java.io.*;
import java.net.URI;

public class ClientQUIC {

    static final String HOST = "localhost";
    static final int    PORT = 4434;
    static final String ALPN = "chat";   // must match the server's registered protocol

    public static void main(String[] args) throws Exception {

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Enter your name: ");
        String username = stdin.readLine().trim();
        if (username.isEmpty()) username = "anonymous";

        System.out.println("[client] connecting to " + HOST + ":" + PORT + " …");

        // ── build and open the QUIC connection ────────────────────────────────
        QuicClientConnection connection = QuicClientConnection.newBuilder()
                .host(HOST)
                .port(PORT)
                .applicationProtocol(ALPN)   // ALPN tells the server which app we speak
                .noServerCertificateCheck()  // skip cert validation — OK for classroom demo
                // Allow the server to push up to 100 unidirectional streams (broadcasts)
                .maxOpenPeerInitiatedUnidirectionalStreams(100)
                .build();

        // Register a callback: called whenever the server opens a new stream toward us.
        // The server uses these unidirectional streams to push broadcast messages.
        connection.setPeerInitiatedStreamCallback(stream -> {
            Thread t = new Thread(() -> {
                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(stream.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isEmpty()) {
                            System.out.print("\r" + line + "\n> ");
                        }
                    }
                } catch (IOException e) {
                    // stream closed
                }
            });
            t.setDaemon(true);
            t.start();
        });

        // Perform the QUIC handshake (includes TLS 1.3 — faster than TCP + TLS)
        connection.connect();

        System.out.println("[client] connected!  You are '" + username + "'");
        System.out.println("[client] type a message and press Enter.  'quit' to exit.\n");

        // ── open a bidirectional stream for sending messages to the server ────
        QuicStream stream = connection.createStream(true);
        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(stream.getOutputStream())), true);

        // First message registers our display name on the server
        writer.println(username);

        // ── read stdin and send lines ─────────────────────────────────────────
        while (true) {
            System.out.print("> ");
            String line = stdin.readLine();
            if (line == null) break;   // EOF (Ctrl-D)
            line = line.trim();
            if (line.equalsIgnoreCase("quit") ||
                line.equalsIgnoreCase("exit") ||
                line.equalsIgnoreCase("q")) break;
            if (!line.isEmpty()) {
                writer.println(line);
            }
        }

        connection.closeAndWait();
        System.out.println("\n[client] disconnected.");
    }
}
