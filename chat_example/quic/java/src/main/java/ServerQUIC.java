/**
 * ServerQUIC.java — Java QUIC Chat Server (entry point)
 * Computer Networks Lab
 *
 * Build:  mvn package -q
 * Run:    java -cp target/quic-chat-1.0-jar-with-dependencies.jar ServerQUIC
 */

public class ServerQUIC {
    public static void main(String[] args) throws Exception {
        new QuicChatServer(4434).start();
    }
}
