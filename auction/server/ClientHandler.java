import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;

/**
 * Handles one client connection: reads a single request line, logs it,
 * sends back a response and closes the connection.
 *
 * Supported requests:
 *   show                 list every item
 *   item <name>          add a new item (the name may contain spaces)
 *   bid <name> <amount>  bid on an item (the last word is the amount)
 */
public class ClientHandler implements Runnable {
    // a client that connects but sends nothing is dropped after this many milliseconds,
    // so it cannot hold on to one of the pool's threads forever
    private static final int READ_TIMEOUT_MS = 10_000;

    private final Socket sock;
    private final Auction auction;
    private final RequestLog log;

    public ClientHandler(Socket sock, Auction auction, RequestLog log) {
        this.sock = sock;
        this.auction = auction;
        this.log = log;
    }

    @Override
    public void run() {
        String clientIp = sock.getInetAddress().getHostAddress();
        // try-with-resources closes the reader, writer and socket even if an exception is thrown
        try (Socket s = sock;
             BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter writer = new PrintWriter(s.getOutputStream(), true)) {

            s.setSoTimeout(READ_TIMEOUT_MS);
            String request = reader.readLine();
            if (request == null) {
                return; // the client closed the connection without sending anything
            }
            request = request.trim();
            log.log(clientIp, request);

            for (String line : handle(request, clientIp)) {
                writer.println(line);
            }
        } catch (SocketTimeoutException ex) {
            System.err.println("Client " + clientIp + " timed out without sending a request.");
        } catch (IOException ex) {
            System.err.println("Error handling client " + clientIp + ": " + ex.getMessage());
        }
    }

    /** Parses a request and returns the response lines to send back. */
    private List<String> handle(String request, String clientIp) {
        // split off the command; everything after it is the arguments
        String[] parts = request.split("\\s+", 2);
        String command = parts[0];
        String args = (parts.length > 1) ? parts[1].trim() : "";

        switch (command) {
            case "show":
                return args.isEmpty() ? show() : List.of("Invalid request.");
            case "item":
                return List.of(addItem(args));
            case "bid":
                return List.of(bid(args, clientIp));
            default:
                return List.of("Invalid request.");
        }
    }

    // request 'show'
    private List<String> show() {
        List<String> lines = auction.describeItems();
        if (lines.isEmpty()) {
            return List.of("There are currently no items in this auction.");
        }
        return lines;
    }

    // request 'item <name>'
    private String addItem(String name) {
        return auction.addItem(normaliseName(name)) ? "Success." : "Failure.";
    }

    // request 'bid <name> <amount>'
    private String bid(String args, String clientIp) {
        // the amount is the last word, so the item name may contain spaces
        int split = args.lastIndexOf(' ');
        if (split < 0) {
            return "Failure."; // missing either the item name or the amount
        }
        String name = normaliseName(args.substring(0, split));
        double amount;
        try {
            amount = Double.parseDouble(args.substring(split + 1));
        } catch (NumberFormatException ex) {
            return "Failure.";
        }

        switch (auction.placeBid(name, amount, clientIp)) {
            case ACCEPTED: return "Accepted.";
            case REJECTED: return "Rejected.";
            default:       return "Failure.";
        }
    }

    // collapse runs of whitespace, so "red   bike" and "red bike" are the same item
    private static String normaliseName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }
}
