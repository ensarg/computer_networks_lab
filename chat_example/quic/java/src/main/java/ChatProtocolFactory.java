/**
 * ChatProtocolFactory.java
 * kwik calls createConnection() once for every incoming QUIC connection.
 * Returns a ChatSession that handles that connection.
 */

import tech.kwik.core.QuicConnection;
import tech.kwik.core.server.ApplicationProtocolConnection;
import tech.kwik.core.server.ApplicationProtocolConnectionFactory;

class ChatProtocolFactory implements ApplicationProtocolConnectionFactory {

    private final SessionRegistry registry;

    ChatProtocolFactory(SessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ApplicationProtocolConnection createConnection(String protocol,
                                                          QuicConnection quicConnection) {
        ChatSession session = new ChatSession(quicConnection, registry);
        int id = registry.register(session);
        session.setConnId(id);
        System.out.println("[server] client #" + id
                + " connected  (active=" + registry.size() + ")");
        return session;
    }

    @Override
    public int maxConcurrentPeerInitiatedBidirectionalStreams() { return 10; }
}
