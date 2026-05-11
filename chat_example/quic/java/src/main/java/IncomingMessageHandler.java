/**
 * IncomingMessageHandler.java
 * Called by kwik whenever the server opens a new unidirectional stream toward
 * this client. Spawns a StreamPrinter daemon thread for each incoming stream.
 */

import tech.kwik.core.QuicStream;

import java.util.function.Consumer;

class IncomingMessageHandler implements Consumer<QuicStream> {

    @Override
    public void accept(QuicStream stream) {
        Thread t = new Thread(new StreamPrinter(stream));
        t.setDaemon(true);
        t.start();
    }
}
