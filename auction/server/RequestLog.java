import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Appends one line per client request to the log file, in the format
 *
 *     date|time|client IP|request
 *
 * log() is synchronized so lines written by different threads never interleave.
 */
public class RequestLog {
    private final File file;

    public RequestLog(String path) throws IOException {
        this.file = new File(path);
        file.createNewFile(); // create the log file if it does not exist yet
    }

    public synchronized void log(String clientIp, String request) {
        String line = LocalDate.now() + "|" + LocalTime.now() + "|" + clientIp + "|" + request
                + System.lineSeparator();
        try (FileWriter writer = new FileWriter(file, true)) { // true = append
            writer.write(line);
        } catch (IOException ex) {
            System.err.println("Could not write to log file: " + ex.getMessage());
        }
    }
}
