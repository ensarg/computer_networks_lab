/**
 * ClientTCP.java — Java TCP Chat Client
 * Computer Networks Lab
 *
 * Connects to the chat server over TCP (port 5556).
 * Two threads run concurrently:
 *   - receiver thread: prints messages pushed by the server
 *   - main thread:     reads stdin and sends messages to the server
 *
 * Compile:  javac ClientTCP.java
 * Run:      java ClientTCP
 */

import java.io.*;
import java.net.*;

public class ClientTCP {

    static final String HOST = "localhost";
    static final int    PORT = 5556;

    public static void main(String[] args) throws IOException {

        // Get display name from the user
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Enter your name: ");
        String username = stdin.readLine().trim();
        if (username.isEmpty()) username = "anonymous";

        System.out.println("[client] connecting to " + HOST + ":" + PORT + " …");

        // Perform the TCP three-way handshake
        Socket socket = new Socket(HOST, PORT);

        PrintWriter writer =
                new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream())), true);
        BufferedReader serverReader =
                new BufferedReader(new InputStreamReader(socket.getInputStream()));

        System.out.println("[client] connected!  You are '" + username + "'");
        System.out.println("[client] type a message and press Enter.  'quit' to exit.\n");

        // First message registers our display name on the server
        writer.println(username);

        // ── receiver thread: print anything the server sends ──────────────────
        Thread receiverThread = new Thread(() -> {
            try {
                String line;
                while ((line = serverReader.readLine()) != null) {
                    // \r moves cursor to start of line so incoming messages
                    // don't collide visually with what the user is typing
                    System.out.print("\r" + line + "\n> ");
                }
            } catch (IOException e) {
                // Server closed the connection
            }
            System.out.println("\n[client] server closed the connection.");
        });
        receiverThread.setDaemon(true);   // exits automatically when main thread ends
        receiverThread.start();

        // ── main thread: read stdin and send to server ────────────────────────
        while (true) {
            System.out.print("> ");
            String line = stdin.readLine();
            if (line == null) break;              // EOF (Ctrl-D)
            line = line.trim();
            if (line.equalsIgnoreCase("quit") ||
                line.equalsIgnoreCase("exit") ||
                line.equalsIgnoreCase("q")) break;
            if (!line.isEmpty()) {
                writer.println(line);             // sends line + '\n' to server
            }
        }

        socket.close();
        System.out.println("\n[client] disconnected.");
    }
}
