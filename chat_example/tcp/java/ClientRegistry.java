/**
 * ClientRegistry.java
 * Thread-safe registry of all connected clients.
 * Provides register / unregister / broadcast operations.
 */

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
