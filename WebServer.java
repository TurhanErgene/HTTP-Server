
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebServer {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java WebServer <port> <public_path>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String publicPath = args[1];

        // Thread Pool // https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/package-summary.html
        ExecutorService threadPool = Executors.newFixedThreadPool(10); // async handling up to 10 clients

        // https://docs.oracle.com/javase/tutorial/networking/sockets/clientServer.html
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server runs on port: " + port);
            System.out.println("Directory: " + publicPath);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from: " + clientSocket.getRemoteSocketAddress());

                // Direct request to a new thread
                threadPool.execute(new ClientHandler(clientSocket, publicPath));

            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}
