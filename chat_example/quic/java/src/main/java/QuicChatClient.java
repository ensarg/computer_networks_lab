/**
 * QuicChatClient.java
 * Connects to the QUIC chat server and manages the session.
 *
 * After connect():
 *   - IncomingMessageHandler handles server-pushed broadcasts (one thread per stream).
 *   - sendLoop() reads stdin and writes to the outgoing bidirectional stream.
 */

import tech.kwik.core.QuicClientConnection;
import tech.kwik.core.QuicStream;

import java.io.*;

class QuicChatClient {

    private final String host;
    private final int    port;
    private final String alpn;

    QuicChatClient(String host, int port, String alpn) {
        this.host = host;
        this.port = port;
        this.alpn = alpn;
    }

    void connect(String username) throws Exception {
        System.out.println("[client] connecting to " + host + ":" + port + " ...");

        QuicClientConnection connection = QuicClientConnection.newBuilder()
                .host(host)
                .port(port)
                .applicationProtocol(alpn)
                .noServerCertificateCheck()                     // OK for classroom demo
                .maxOpenPeerInitiatedUnidirectionalStreams(100)  // allow server broadcasts
                .build();

        connection.setPeerInitiatedStreamCallback(new IncomingMessageHandler());
        connection.connect();   // QUIC + TLS 1.3 handshake

        System.out.println("[client] connected!  You are '" + username + "'");
        System.out.println("[client] type a message and press Enter.  'quit' to exit.\n");

        QuicStream stream = connection.createStream(true);   // bidirectional
        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(stream.getOutputStream())), true);

        writer.println(username);   // first message registers the display name

        sendLoop(writer);

        connection.closeAndWait();
        System.out.println("\n[client] disconnected.");
    }

    private void sendLoop(PrintWriter writer) throws IOException {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("> ");
            String line = stdin.readLine();
            if (line == null) break;
            line = line.trim();
            if (line.equalsIgnoreCase("quit") ||
                line.equalsIgnoreCase("exit") ||
                line.equalsIgnoreCase("q")) break;
            if (!line.isEmpty()) {
                writer.println(line);
            }
        }
    }
}
