/**
 * ClientTCP.java — Java TCP Chat Client (entry point)
 * Computer Networks Lab
 *
 * Compile:  javac *.java
 * Run:      java ClientTCP
 */

import java.io.IOException;

public class ClientTCP {
    public static void main(String[] args) throws IOException {
        String username = new UserInputReader().readUsername();
        new ChatClient("localhost", 5556).connect(username);
    }
}
