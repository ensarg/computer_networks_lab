/**
 * ServerTCP.java — Java TCP Chat Server (entry point)
 * Computer Networks Lab
 *
 * Compile:  javac *.java
 * Run:      java ServerTCP
 */

import java.io.IOException;

public class ServerTCP {
    public static void main(String[] args) throws IOException {
        new ChatServer(5556).start();
    }
}
