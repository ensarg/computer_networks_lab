/**
 * ChatServer.java
 * Opens a TCP server socket and accepts incoming client connections.
 * Each client is handed off to a ClientHandler running on its own thread.
 */

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

class ChatServer {

    private final int port;
    private final ClientRegistry registry = new ClientRegistry();

    ChatServer(int port) {
        this.port = port;
    }

    void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("[server] listening on port " + port + "  (TCP)");
        System.out.println("[server] waiting for clients ...\n");

        while (true) {
            Socket socket = serverSocket.accept();   // blocks until a client connects
            int id = registry.register(socket);
            System.out.println("[server] client #" + id + " connected from "
                    + socket.getRemoteSocketAddress()
                    + "  (active=" + registry.size() + ")");
            new Thread(new ClientHandler(socket, id, registry)).start();
        }
    }
}
