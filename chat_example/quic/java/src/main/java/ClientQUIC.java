/**
 * ClientQUIC.java — Java QUIC Chat Client
 * Computer Networks Lab
 *
 * Build:  cd quic_java && mvn package -q
 * Run:    java -cp target/quic-chat-1.0-jar-with-dependencies.jar ClientQUIC
 */

import tech.kwik.core.QuicClientConnection;
import tech.kwik.core.QuicStream;

import java.io.*;

// ── Entry point ───────────────────────────────────────────────────────────────
public class ClientQUIC {
    public static void main(String[] args) throws Exception {
        String username = new UserInputReader().readUsername();
        new QuicChatClient("localhost", 4434, "chat").connect(username);
    }
}

// ── UserInputReader ───────────────────────────────────────────────────────────
/**
 * Prompts the user for a display name on stdin.
 */
class UserInputReader {

    String readUsername() throws IOException {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Enter your name: ");
        String name = stdin.readLine().trim();
        return name.isEmpty() ? "anonymous" : name;
    }
}

// ── QuicChatClient ────────────────────────────────────────────────────────────
/**
 * Connects to the QUIC chat server and manages the session.
 *
 * After connect():
 *   - IncomingMessageHandler handles server-pushed broadcasts (one thread per stream).
 *   - sendLoop() reads stdin and writes to the outgoing bidirectional stream.
 */
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

        // Register handler for server-initiated broadcast streams
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

// ── IncomingMessageHandler ────────────────────────────────────────────────────
/**
 * Called by kwik whenever the server opens a new unidirectional stream toward
 * this client. Reads the message and prints it to the terminal.
 */
class IncomingMessageHandler implements java.util.function.Consumer<QuicStream> {

    @Override
    public void accept(QuicStream stream) {
        Thread t = new Thread(new StreamPrinter(stream));
        t.setDaemon(true);
        t.start();
    }

    // ── inner: reads one stream and prints its content ────────────────────────
    private static class StreamPrinter implements Runnable {

        private final QuicStream stream;

        StreamPrinter(QuicStream stream) { this.stream = stream; }

        @Override
        public void run() {
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(stream.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isEmpty()) {
                        System.out.print("\r" + line + "\n> ");
                    }
                }
            } catch (IOException e) {
                // Stream closed
            }
        }
    }
}
