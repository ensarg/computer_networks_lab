/**
 * DisconnectListener.java
 * Handles QUIC connection close events.
 * Unregisters the session and broadcasts a departure notice.
 */

import tech.kwik.core.ConnectionListener;
import tech.kwik.core.ConnectionTerminatedEvent;

class DisconnectListener implements ConnectionListener {

    private final ChatSession     session;
    private final SessionRegistry registry;

    DisconnectListener(ChatSession session, SessionRegistry registry) {
        this.session  = session;
        this.registry = registry;
    }

    @Override
    public void disconnected(ConnectionTerminatedEvent event) {
        registry.unregister(session.getConnId());
        String name = (session.getUsername() != null)
                ? session.getUsername()
                : "#" + session.getConnId();
        System.out.println("[server] " + name
                + " disconnected  (active=" + registry.size() + ")");
        registry.broadcast("*** " + name + " left the chat ***", session.getConnId());
    }
}
