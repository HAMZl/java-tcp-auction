import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Auction server. Accepts TCP connections and hands each one to a
 * ClientHandler running on a fixed-size thread pool.
 *
 * Usage: java Server [port]   (the default port is 6001)
 */
public class Server {
    private static final int DEFAULT_PORT = 6001;
    private static final int POOL_SIZE = 30;
    private static final String LOG_FILE = "log.txt";

    private final int port;
    private final Auction auction = new Auction(); // shared by every handler
    private ServerSocket serverSock;
    private ExecutorService executor;

    public Server(int port) {
        this.port = port;
    }

    public void runServer() {
        try {
            RequestLog log = new RequestLog(LOG_FILE);
            serverSock = new ServerSocket(port);
            executor = Executors.newFixedThreadPool(POOL_SIZE);

            // on Ctrl+C, stop accepting connections and let running requests finish
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

            System.out.println("Auction server listening on port " + port);
            while (!serverSock.isClosed()) {
                Socket sock = serverSock.accept();
                executor.execute(new ClientHandler(sock, auction, log));
            }
        } catch (IOException ex) {
            // accept() throws when shutdown() closes the socket. That is expected, not an error.
            if (serverSock == null || !serverSock.isClosed()) {
                System.err.println("Server error: " + ex.getMessage());
            }
        }
    }

    private void shutdown() {
        try {
            if (serverSock != null) {
                serverSock.close();
            }
        } catch (IOException ignored) {
            // nothing useful to do while shutting down
        }
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                System.err.println("Invalid port: " + args[0]);
                System.exit(1);
            }
        }
        new Server(port).runServer();
    }
}
