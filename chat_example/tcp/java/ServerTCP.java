/**
 * ServerTCP.java — Java TCP Chat Server
 * Computer Networks Lab
 *
 * Compile:  javac ServerTCP.java
 * Run:      java ServerTCP
 */

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// ── Entry point ───────────────────────────────────────────────────────────────
public class ServerTCP {
    public static void main(String[] args) throws IOException {
        new ChatServer(5556).start();
    }
}

// ── ChatServer ────────────────────────────────────────────────────────────────
/**
 * Opens a TCP server socket and accepts incoming client connections.
 * Each client is handed off to a ClientHandler running on its own thread.
 */
class ChatServer {

    private final int port;
    private final ClientRegistry registry = new ClientRegistry();

    ChatServer(int port) {
        this.port = port;
    }

    void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("[server] listening on port " + port + "  (TCP)");
        System.out.println("[server] waiting for clients ...\n");

        while (true) {
            Socket socket = serverSocket.accept();   // blocks until a client connects
            int id = registry.register(socket);
            System.out.println("[server] client #" + id + " connected from "
                    + socket.getRemoteSocketAddress()
                    + "  (active=" + registry.size() + ")");
            new Thread(new ClientHandler(socket, id, registry)).start();
        }
    }
}

// ── ClientRegistry ────────────────────────────────────────────────────────────
/**
 * Thread-safe registry of all connected clients.
 * Provides register / unregister / broadcast operations.
 */
class ClientRegistry {

    private final ConcurrentHashMap<Integer, OutputStream> outputs = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);

    /** Register a new client and return its assigned id. */
    int register(Socket socket) throws IOException {
        int id = idCounter.incrementAndGet();
        outputs.put(id, socket.getOutputStream());
        return id;
    }

    void unregister(int id) {
        outputs.remove(id);
    }

    int size() {
        return outputs.size();
    }

    /** Send message to every client except the one with the given id. */
    void broadcast(String message, int excludeId) {
        System.out.println("[server] " + message);
        byte[] data = (message + "\n").getBytes();
        for (Map.Entry<Integer, OutputStream> entry : outputs.entrySet()) {
            if (entry.getKey() == excludeId) continue;
            try {
                entry.getValue().write(data);
                entry.getValue().flush();
            } catch (IOException e) {
                System.out.println("[server] could not reach #" + entry.getKey());
            }
        }
    }
}

// ── ClientHandler ─────────────────────────────────────────────────────────────
/**
 * Handles one client connection on its own thread.
 *
 * Flow:
 *   1. Read the first line  -> treat it as the client's display name.
 *   2. Read subsequent lines -> broadcast to all other clients.
 *   3. When the socket closes -> unregister and notify others.
 */
class ClientHandler implements Runnable {

    private final Socket socket;
    private final int connId;
    private final ClientRegistry registry;
    private String username = null;

    ClientHandler(Socket socket, int connId, ClientRegistry registry) {
        this.socket   = socket;
        this.connId   = connId;
        this.registry = registry;
    }

    @Override
    public void run() {
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {   // blocks until '\n'
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
            // Client disconnected abruptly — normal
        } finally {
            registry.unregister(connId);
            String name = (username != null) ? username : "#" + connId;
            System.out.println("[server] " + name
                    + " disconnected  (active=" + registry.size() + ")");
            registry.broadcast("*** " + name + " left the chat ***", connId);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
