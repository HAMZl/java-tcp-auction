import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;

/**
 * Command-line client for the auction server.
 *
 * Usage:
 *   java Client show
 *   java Client item <name>
 *   java Client bid <name> <amount>
 *
 * Item names may contain spaces, for example: java Client bid red bike 25
 * The server address can be changed with -Dhost=... and -Dport=...
 */
public class Client {
    private static final String HOST = System.getProperty("host", "localhost");
    private static final int PORT = Integer.getInteger("port", 6001);

    /** Sends one request to the server and prints every line of the response. */
    public void connect(String request) {
        try (Socket s = new Socket(HOST, PORT);
             PrintWriter writer = new PrintWriter(s.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()))) {

            writer.println(request);
            String line;
            // the server closes the connection when it has finished replying
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (ConnectException ex) {
            System.err.println("Could not connect to the server at " + HOST + ":" + PORT + ". Is it running?");
            System.exit(1);
        } catch (IOException ex) {
            System.err.println("Communication error: " + ex.getMessage());
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.err.println("Usage: java Client show");
        System.err.println("       java Client item <name>");
        System.err.println("       java Client bid <name> <amount>");
    }

    /** Checks the arguments. Returns an error message, or null if they are valid. */
    private static String validate(String[] args) {
        switch (args[0]) {
            case "show":
                return (args.length == 1) ? null : "'show' takes no arguments.";
            case "item":
                return (args.length >= 2) ? null : "'item' needs an item name.";
            case "bid":
                if (args.length < 3) {
                    return "'bid' needs an item name and an amount.";
                }
                // only check that the amount is a number. Deciding whether the bid
                // is valid (positive, high enough) is the server's job
                String amount = args[args.length - 1];
                try {
                    Double.parseDouble(amount);
                } catch (NumberFormatException ex) {
                    return "Bid amount is not a number: " + amount;
                }
                return null;
            default:
                return "Unknown command: " + args[0];
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }
        String error = validate(args);
        if (error != null) {
            System.err.println(error);
            printUsage();
            System.exit(1);
        }
        // join the arguments with spaces to form a single request line
        new Client().connect(String.join(" ", args));
    }
}
