/**
 * ChatSession.java
 * Represents one connected QUIC client.
 *
 * acceptPeerInitiatedStream() — called by kwik when the client opens a stream;
 *                               delegates to StreamReader on a daemon thread.
 * send()                      — opens a server-initiated unidirectional stream
 *                               and pushes a broadcast message to this client.
 */

import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicStream;
import tech.kwik.core.server.ApplicationProtocolConnection;

import java.io.IOException;
import java.io.OutputStream;

class ChatSession implements ApplicationProtocolConnection {

    private final QuicConnection  connection;
    private final SessionRegistry registry;
    private int    connId;
    private String username = null;

    ChatSession(QuicConnection connection, SessionRegistry registry) {
        this.connection = connection;
        this.registry   = registry;
        connection.setConnectionListener(new DisconnectListener(this, registry));
    }

    void   setConnId(int id)        { this.connId   = id;   }
    int    getConnId()              { return connId;         }
    String getUsername()            { return username;       }
    void   setUsername(String name) { this.username = name;  }

    /** Called by kwik when the client opens a new stream toward the server. */
    @Override
    public void acceptPeerInitiatedStream(QuicStream stream) {
        Thread t = new Thread(new StreamReader(stream, this, registry));
        t.setDaemon(true);
        t.start();
    }

    /** Open a server-initiated unidirectional stream and write the message. */
    void send(String message) {
        try {
            QuicStream stream = connection.createStream(false);   // unidirectional
            OutputStream out = stream.getOutputStream();
            out.write((message + "\n").getBytes());
            out.close();
        } catch (IOException e) {
            // Client may have already disconnected
        }
    }
}
