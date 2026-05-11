/**
 * ChatClient.java
 * Connects to the chat server over TCP and manages the session.
 *
 * Two concurrent activities run after connect():
 *   - MessageReceiver (daemon thread): prints broadcasts from the server.
 *   - sendLoop (main thread):          reads stdin and sends messages.
 */

import java.io.*;
import java.net.Socket;

class ChatClient {

    private final String host;
    private final int    port;

    ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    void connect(String username) throws IOException {
        System.out.println("[client] connecting to " + host + ":" + port + " ...");

        Socket socket = new Socket(host, port);   // TCP three-way handshake

        PrintWriter    writer       = new PrintWriter(new BufferedWriter(
                                          new OutputStreamWriter(socket.getOutputStream())), true);
        BufferedReader serverReader = new BufferedReader(
                                          new InputStreamReader(socket.getInputStream()));

        System.out.println("[client] connected!  You are '" + username + "'");
        System.out.println("[client] type a message and press Enter.  'quit' to exit.\n");

        writer.println(username);   // first message registers the display name

        Thread receiver = new Thread(new MessageReceiver(serverReader));
        receiver.setDaemon(true);   // exits when the main thread exits
        receiver.start();

        sendLoop(writer);

        socket.close();
        System.out.println("\n[client] disconnected.");
    }

    private void sendLoop(PrintWriter writer) throws IOException {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("> ");
            String line = stdin.readLine();
            if (line == null) break;              // EOF (Ctrl-D)
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
