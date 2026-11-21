/**
 * A single item in the auction: its name, the current highest bid and the
 * IP address of the client that placed it.
 *
 * BidItem itself is not thread-safe. It is only changed through Auction,
 * which synchronizes every access.
 */
public class BidItem {
    private final String name; // item name
    private double bid;        // current highest bid
    private String bidder;     // IP address of the highest bidder, or null if nobody has bid

    public BidItem(String name) {
        this.name = name;
        this.bid = 0.0;
        this.bidder = null;
    }

    /** Records a new highest bid and the IP address of the bidder. */
    public void setBidder(double bid, String bidder) {
        this.bid = bid;
        this.bidder = bidder;
    }

    public String getItemName() {
        return name;
    }

    public double getBid() {
        return bid;
    }

    public String getBidder() {
        return bidder;
    }

    /** Formats the item as "name : bid : bidder", or "name : bid : <no bids>". */
    @Override
    public String toString() {
        String who = (bidder != null) ? bidder : "<no bids>";
        return name + " : " + bid + " : " + who;
    }
}
