/**
 * StreamPrinter.java
 * Reads one server-initiated QUIC stream and prints its content to the terminal.
 */

import tech.kwik.core.QuicStream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

class StreamPrinter implements Runnable {

    private final QuicStream stream;

    StreamPrinter(QuicStream stream) {
        this.stream = stream;
    }

    @Override
    public void run() {
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(stream.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    System.out.print("\r" + line + "\n> ");
                }
            }
        } catch (IOException e) {
            // Stream closed
        }
    }
}
