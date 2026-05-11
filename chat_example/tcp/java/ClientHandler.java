/**
 * ClientHandler.java
 * Handles one client connection on its own thread.
 *
 * Flow:
 *   1. Read the first line  -> treat it as the client's display name.
 *   2. Read subsequent lines -> broadcast to all other clients.
 *   3. When the socket closes -> unregister and notify others.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

class ClientHandler implements Runnable {

    private final Socket         socket;
    private final int            connId;
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
