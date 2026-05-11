/**
 * SessionRegistry.java
 * Thread-safe registry of all active QUIC sessions.
 * Provides register / unregister / broadcast operations.
 */

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
