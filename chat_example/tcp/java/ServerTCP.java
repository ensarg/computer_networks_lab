/**
 * ServerTCP.java — Java TCP Chat Server
 * Computer Networks Lab
 *
 * Listens for TCP client connections on port 5556.
 * Every message received from one client is broadcast to all other clients.
 *
 * Compile:  javac ServerTCP.java
 * Run:      java ServerTCP
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ServerTCP {

    static final int PORT = 5556;

    // Thread-safe map: connection id → client handler
    static final ConcurrentHashMap<Integer, ClientHandler> clients = new ConcurrentHashMap<>();
    static int nextId = 0;

    static synchronized int allocId() {
        return ++nextId;
    }

    // ── broadcast to every client except the sender ───────────────────────────
    static void broadcast(String message, int excludeId) {
        System.out.println("[server] " + message);
        byte[] data = (message + "\n").getBytes();
        for (Map.Entry<Integer, ClientHandler> entry : clients.entrySet()) {
            if (entry.getKey() == excludeId) continue;
            try {
                entry.getValue().out.write(data);
                entry.getValue().out.flush();
            } catch (IOException e) {
                System.out.println("[server] could not reach #" + entry.getKey());
            }
        }
    }

    // ── entry point ───────────────────────────────────────────────────────────
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("[server] listening on port " + PORT + "  (TCP)");
        System.out.println("[server] waiting for clients …\n");

        // Accept loop: one new thread per incoming connection
        while (true) {
            Socket socket = serverSocket.accept();   // blocks until a client connects
            int id = allocId();
            ClientHandler handler = new ClientHandler(socket, id);
            clients.put(id, handler);
            new Thread(handler).start();             // handle client on its own thread
        }
    }


    // ── per-client thread ─────────────────────────────────────────────────────

    /**
     * ClientHandler runs on its own thread and manages one client connection.
     *
     * Flow:
     *   1. Read the first line → treat it as the client's display name.
     *   2. Read subsequent lines → broadcast to all other clients.
     *   3. When the socket closes → unregister and notify others.
     */
    static class ClientHandler implements Runnable {

        final Socket socket;
        final int connId;
        final OutputStream out;       // kept for broadcasting back to this client
        String username = null;

        ClientHandler(Socket socket, int connId) throws IOException {
            this.socket = socket;
            this.connId = connId;
            this.out = socket.getOutputStream();
            System.out.println("[server] client #" + connId + " connected from "
                    + socket.getRemoteSocketAddress()
                    + "  (active=" + clients.size() + ")");
        }

        @Override
        public void run() {
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {   // readLine() blocks until '\n'
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (username == null) {
                        // First message sets the display name
                        username = line;
                        System.out.println("[server] client #" + connId
                                + " → name '" + username + "'");
                        broadcast("*** " + username + " joined the chat ***", connId);
                    } else {
                        broadcast(username + ": " + line, connId);
                    }
                }

            } catch (IOException e) {
                // Client disconnected abruptly — normal, not an error
            } finally {
                clients.remove(connId);
                String name = (username != null) ? username : "#" + connId;
                System.out.println("[server] " + name
                        + " disconnected  (active=" + clients.size() + ")");
                broadcast("*** " + name + " left the chat ***", connId);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }
}
