# Java TCP Auction

A multithreaded client–server auction written in Java. The server keeps a list of items. Clients connect over TCP to list the items, add new ones, and place bids. Every request is recorded in a log file.

## Requirements

- Java 11 or later (tested with OpenJDK 17)
- Bash, to run the test script (on Windows, Git Bash works)

## Quick start

Compile and start the server:

```bash
cd auction/server
javac *.java
java Server            # listens on port 6001
```

In a second terminal, compile the client and send requests:

```bash
cd auction/client
javac Client.java
java Client item laptop       # Success.
java Client bid laptop 50     # Accepted.
java Client bid laptop 20     # Rejected.
java Client show              # laptop : 50.0 : 127.0.0.1
```

Stop the server with `Ctrl+C`. Requests already in progress are allowed to finish.

## Commands

| Command | Response |
|---|---|
| `show` | One line per item, as `name : highest bid : bidder IP` (`<no bids>` if nobody has bid). If the auction is empty: `There are currently no items in this auction.` |
| `item <name>` | `Success.` if the item was added, `Failure.` if an item with that name already exists |
| `bid <name> <amount>` | `Accepted.` if the bid is higher than the current highest bid, `Rejected.` if it is not, `Failure.` if the item does not exist or the amount is not a positive number |

Items are listed in the order they were added. New items start at a bid of `0.0`. A bid must be strictly higher than the current one, so an equal bid is rejected.

Item names can contain spaces, with or without quotes:

```bash
java Client item red bike
java Client bid "red bike" 30
```

The client checks its arguments before connecting. It prints usage help for unknown commands, missing arguments, or a non-numeric bid amount, then exits with status 1.

## Configuration

| Setting | Default | How to change it |
|---|---|---|
| Server port | `6001` | `java Server 7000` |
| Client host | `localhost` | `java -Dhost=192.168.1.10 Client show` |
| Client port | `6001` | `java -Dport=7000 Client show` |

## Logging

The server writes `log.txt` to the directory it was started from. It adds one line per request:

```
2026-11-21|14:03:27.512|127.0.0.1|bid laptop 50
```

The fields are date, time, client IP address and the request exactly as received.

## Project structure

```
auction/
├── server/
│   ├── Server.java         accepts connections and hands each one to a 30-thread pool
│   ├── ClientHandler.java  reads one request, checks it, replies, closes the connection
│   ├── Auction.java        shared auction state; every operation is synchronized
│   ├── BidItem.java        one item: name, highest bid, highest bidder
│   └── RequestLog.java     thread-safe writer for log.txt
└── client/
    ├── Client.java         command-line client
    └── testscript.sh       integration tests
```

### How a request is handled

1. `Server` accepts a TCP connection and submits a `ClientHandler` to a fixed pool of 30 threads.
2. `ClientHandler` reads one line. A client that sends nothing is dropped after 10 seconds, so it can't hold a pool thread forever.
3. The request is logged, parsed and passed to `Auction`.
4. The response is written back and the connection is closed. Each connection carries exactly one request.

### Thread safety

All handler threads share a single `Auction`. Its methods are `synchronized`, so "check whether the item exists, then add it" and "check whether the bid is higher, then record it" each happen as one atomic step. Without this, two clients adding the same item at the same moment could both get `Success.`, and two bids at the same moment could leave the lower one as the winner. `RequestLog` is synchronized for the same reason, so log lines from different threads never interleave.

### Input checking

The server does not rely on the client to check requests. Malformed requests get `Failure.` or `Invalid request.` and never crash a handler thread. Examples include a missing item name, a missing or non-numeric bid amount, `NaN`/`Infinity` bids, empty requests and unknown commands.

## Running the tests

```bash
cd auction/client
./testscript.sh
```

The script:

- Compiles both programs.
- Starts a fresh server on port `6099` (change it with `TEST_PORT=7000 ./testscript.sh`). The server runs in a temporary directory, so your real `log.txt` is not touched.
- Runs 34 tests, then stops the server.

The tests cover:

- Every command and response.
- Item names containing spaces.
- The client's argument checking.
- Malformed requests sent straight to the server over a raw socket.
- Concurrency: 20 clients adding the same item at once (exactly one should succeed), and 20 bids at once (the highest should win).
- The log file format.

The script exits with status 0 only if every test passes.

## Limitations and ideas for future work

- **State is in memory only.** Restarting the server clears the auction. Saving items to a file or database would fix this.
- **Bidders are identified by IP address.** Clients behind the same network address can't be told apart. Usernames or authentication would solve this.
- **Auctions never close.** Possible additions: an end time per item, a `close <item>` command, and a minimum bid increment.
- **One global lock.** Fine at this scale. Locking each item separately would let bids on different items run in parallel.
