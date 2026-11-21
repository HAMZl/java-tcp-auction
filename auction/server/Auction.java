import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the shared auction state.
 *
 * Every public method is synchronized, so each "check then modify" step
 * (for example "does this item exist? then add it" or "is this bid higher?
 * then record it") happens atomically, even when many ClientHandler threads
 * call in at the same time.
 */
public class Auction {

    /** Possible outcomes of a bid. */
    public enum BidResult { ACCEPTED, REJECTED, FAILURE }

    // LinkedHashMap gives O(1) lookups by name and keeps items in the order they were added
    private final Map<String, BidItem> items = new LinkedHashMap<>();

    /**
     * Adds a new item with a starting bid of 0.0.
     *
     * @return true if the item was added, false if the name is blank or already in use
     */
    public synchronized boolean addItem(String name) {
        if (name == null || name.isBlank() || items.containsKey(name)) {
            return false;
        }
        items.put(name, new BidItem(name));
        return true;
    }

    /**
     * Places a bid on an item.
     *
     * @return FAILURE if the item does not exist or the amount is not a positive finite number,
     *         ACCEPTED if the bid is higher than the current highest bid, REJECTED otherwise
     */
    public synchronized BidResult placeBid(String name, double amount, String bidder) {
        BidItem item = items.get(name);
        if (item == null || !Double.isFinite(amount) || amount <= 0.0) {
            return BidResult.FAILURE;
        }
        if (amount > item.getBid()) {
            item.setBidder(amount, bidder);
            return BidResult.ACCEPTED;
        }
        return BidResult.REJECTED;
    }

    /** Returns one line per item, in the order the items were added. */
    public synchronized List<String> describeItems() {
        List<String> lines = new ArrayList<>();
        for (BidItem item : items.values()) {
            lines.add(item.toString());
        }
        return lines;
    }
}
