#!/bin/bash
#
# Integration tests for the auction system.
#
# Compiles the server and client, starts a fresh server on a test port in a
# temporary directory (so the real log.txt is not touched), runs the tests
# and stops the server again.
#
# Usage: ./testscript.sh            (run from any directory)
#        TEST_PORT=7000 ./testscript.sh

set -u

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
SERVER_DIR="$SCRIPT_DIR/../server"
CLIENT_DIR="$SCRIPT_DIR"
TEST_PORT=${TEST_PORT:-6099}

PASSED=0
FAILED=0

# ---------- setup ----------

echo "Compiling..."
javac "$SERVER_DIR"/*.java || { echo "Server failed to compile"; exit 1; }
javac "$CLIENT_DIR"/Client.java || { echo "Client failed to compile"; exit 1; }

WORK_DIR=$(mktemp -d)
(cd "$WORK_DIR" && exec java -cp "$SERVER_DIR" Server "$TEST_PORT") > "$WORK_DIR/server.out" 2>&1 &
SERVER_PID=$!

cleanup() {
    kill "$SERVER_PID" 2>/dev/null
    wait "$SERVER_PID" 2>/dev/null
    rm -rf "$WORK_DIR"
}
trap cleanup EXIT

# run the client against the test server; stdout and stderr are combined,
# and \r is removed so results compare correctly on Windows
client() {
    java -Dport="$TEST_PORT" -cp "$CLIENT_DIR" Client "$@" 2>&1 | tr -d '\r'
}

# wait up to about 10 seconds for the server to start accepting connections
for _ in $(seq 1 50); do
    if ! client show | grep -q "Could not connect"; then
        break
    fi
    sleep 0.2
done

# ---------- helpers ----------

# expect <test name> <expected output> <client args...>
expect() {
    local name=$1 expected=$2
    shift 2
    local actual
    actual=$(client "$@")
    if [ "$actual" == "$expected" ]; then
        echo "PASSED: $name"
        PASSED=$((PASSED + 1))
    else
        echo "FAILED: $name"
        echo "    EXPECTED: $expected"
        echo "    GOT:      $actual"
        FAILED=$((FAILED + 1))
    fi
}

# expect_match <test name> <regex> <client args...>
expect_match() {
    local name=$1 pattern=$2
    shift 2
    local actual
    actual=$(client "$@")
    if [[ "$actual" =~ $pattern ]]; then
        echo "PASSED: $name"
        PASSED=$((PASSED + 1))
    else
        echo "FAILED: $name"
        echo "    EXPECTED to match: $pattern"
        echo "    GOT:               $actual"
        FAILED=$((FAILED + 1))
    fi
}

# check <test name> <condition result: 0 = pass> <details>
check() {
    if [ "$2" -eq 0 ]; then
        echo "PASSED: $1"
        PASSED=$((PASSED + 1))
    else
        echo "FAILED: $1"
        echo "    $3"
        FAILED=$((FAILED + 1))
    fi
}

# ---------- basic behaviour ----------

expect "show with no items" "There are currently no items in this auction." show
expect "add item" "Success." item laptop
expect "add duplicate item" "Failure." item laptop
expect_match "new item has no bids" "^laptop : 0\.0 : <no bids>$" show
expect "first bid accepted" "Accepted." bid laptop 50
expect "lower bid rejected" "Rejected." bid laptop 20
expect "equal bid rejected" "Rejected." bid laptop 50
expect "higher bid accepted" "Accepted." bid laptop 75.5
expect "bid on missing item" "Failure." bid phone 10
expect "zero bid" "Failure." bid laptop 0
expect "negative bid" "Failure." bid laptop -5
expect_match "show lists highest bid and bidder" "^laptop : 75\.5 : [0-9a-f.:]+$" show

# ---------- item names containing spaces ----------

expect "add multi-word item" "Success." item red bike
expect "bid on multi-word item" "Accepted." bid red bike 30
expect "quoted name is the same item" "Failure." item "red bike"
expect_match "show lists items in order added" $'^laptop : .*\nred bike : 30\.0 : .*$' show

# ---------- client-side argument checking ----------

expect_match "no arguments prints usage" "Usage:"
expect_match "unknown command" "Unknown command: sell" sell laptop
expect_match "show with extra argument" "'show' takes no arguments" show laptop
expect_match "item without name" "'item' needs an item name" item
expect_match "bid without amount" "'bid' needs an item name and an amount" bid laptop
expect_match "bid with non-numeric amount" "Bid amount is not a number: abc" bid laptop abc
expect "bid with infinite amount" "Failure." bid laptop Infinity
expect "bid with NaN amount" "Failure." bid laptop NaN

# ---------- server robustness ----------
# These requests skip the Java client and go straight to the server over a
# raw socket, the way a buggy or malicious client would. The old server
# crashed its handler thread on each of them.

# raw <request line>: send a raw request and print the response
raw() {
    exec 3<>"/dev/tcp/127.0.0.1/$TEST_PORT"
    printf '%s\n' "$1" >&3
    tr -d '\r' <&3
    exec 3<&-
}

# raw_expect <test name> <expected output> <request line>
raw_expect() {
    local actual
    actual=$(raw "$3")
    check "$1" $([ "$actual" == "$2" ]; echo $?) "expected '$2', got '$actual'"
}

raw_expect "raw: item with no name" "Failure." "item"
raw_expect "raw: bid with no amount" "Failure." "bid laptop"
raw_expect "raw: bid with non-numeric amount" "Failure." "bid laptop abc"
raw_expect "raw: unknown command" "Invalid request." "sell laptop"
raw_expect "raw: empty request" "Invalid request." ""
# open a connection and close it without sending anything
exec 3<>"/dev/tcp/127.0.0.1/$TEST_PORT"; exec 3<&-
expect_match "server still works after bad requests" "^laptop : 75\.5 : " show

# ---------- concurrency ----------
# Before Auction was synchronized, these could produce duplicate items, or
# leave a lower bid as the winner.

N=20
for i in $(seq 1 $N); do
    client item gadget > "$WORK_DIR/item_$i.out" &
done
wait_for_clients() { for job in $(jobs -p); do [ "$job" != "$SERVER_PID" ] && wait "$job"; done; }
wait_for_clients
successes=$(cat "$WORK_DIR"/item_*.out | grep -c "^Success\.$")
check "concurrent adds of the same item: exactly one succeeds" $([ "$successes" -eq 1 ]; echo $?) \
    "expected 1 'Success.', got $successes"

for i in $(seq 1 $N); do
    client bid gadget "$i" > /dev/null &
done
wait_for_clients
expect_match "concurrent bids: the highest bid wins" "gadget : $N\.0 : " show

# ---------- logging ----------

log_lines=$(wc -l < "$WORK_DIR/log.txt")
check "every request is logged" $([ "$log_lines" -gt 0 ]; echo $?) "log.txt is empty"
first_line=$(head -n 1 "$WORK_DIR/log.txt" | tr -d '\r')
[[ "$first_line" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}\|[0-9:.]+\|[0-9a-f.:]+\|show$ ]]
check "log line format is date|time|ip|request" $? "got: $first_line"

# ---------- summary ----------

echo
echo "$PASSED passed, $FAILED failed"
[ "$FAILED" -eq 0 ]
