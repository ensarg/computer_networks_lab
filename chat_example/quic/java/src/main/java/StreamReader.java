/**
 * StreamReader.java
 * Reads lines from one client-initiated QUIC stream.
 * The first line is treated as the client's display name;
 * subsequent lines are broadcast to all other sessions.
 */

import tech.kwik.core.QuicStream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

class StreamReader implements Runnable {

    private final QuicStream      stream;
    private final ChatSession     session;
    private final SessionRegistry registry;

    StreamReader(QuicStream stream, ChatSession session, SessionRegistry registry) {
        this.stream   = stream;
        this.session  = session;
        this.registry = registry;
    }

    @Override
    public void run() {
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(stream.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (session.getUsername() == null) {
                    session.setUsername(line);
                    System.out.println("[server] client #" + session.getConnId()
                            + " -> name '" + session.getUsername() + "'");
                    registry.broadcast("*** " + session.getUsername() + " joined the chat ***",
                            session.getConnId());
                } else {
                    registry.broadcast(session.getUsername() + ": " + line, session.getConnId());
                }
            }
        } catch (IOException e) {
            // Stream closed — normal on disconnect
        }
    }
}
