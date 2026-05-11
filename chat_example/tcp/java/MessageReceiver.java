/**
 * MessageReceiver.java
 * Runs on a daemon thread.
 * Reads lines pushed by the server and prints them to the terminal.
 */

import java.io.BufferedReader;
import java.io.IOException;

class MessageReceiver implements Runnable {

    private final BufferedReader serverReader;

    MessageReceiver(BufferedReader serverReader) {
        this.serverReader = serverReader;
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = serverReader.readLine()) != null) {
                System.out.print("\r" + line + "\n> ");
            }
        } catch (IOException e) {
            // Server closed the connection
        }
        System.out.println("\n[client] server closed the connection.");
    }
}
