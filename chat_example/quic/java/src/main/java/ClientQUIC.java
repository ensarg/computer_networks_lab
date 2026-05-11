/**
 * ClientQUIC.java — Java QUIC Chat Client (entry point)
 * Computer Networks Lab
 *
 * Build:  mvn package -q
 * Run:    java -cp target/quic-chat-1.0-jar-with-dependencies.jar ClientQUIC
 */

public class ClientQUIC {
    public static void main(String[] args) throws Exception {
        String username = new UserInputReader().readUsername();
        new QuicChatClient("localhost", 4434, "chat").connect(username);
    }
}
