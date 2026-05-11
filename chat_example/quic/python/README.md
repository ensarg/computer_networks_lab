# QUIC Chat — Computer Networks Lab

A simple client-server **broadcast chat** application built on the **QUIC**
transport protocol. Intended for undergraduate computer networks coursework.

---

## What is QUIC?

QUIC (Quick UDP Internet Connections) is a modern transport protocol developed
by Google and standardised as **RFC 9000** (2021). Key properties:

| Property | TCP | QUIC |
|---|---|---|
| Runs over | IP | UDP |
| Reliability | Yes (stream) | Yes (multiple independent streams) |
| Connection setup | 1-3 RTT | 0-1 RTT |
| Head-of-line blocking | Yes | No |
| TLS | Optional | Built-in (TLS 1.3, mandatory) |
| Connection migration | No | Yes (connection ID survives IP change) |

QUIC is the transport used by **HTTP/3** and is increasingly common in
production systems (YouTube, Google Search, Cloudflare, etc.).

---

## Project Structure

```
quic_ex/
├── README.md          ← this file
├── requirements.txt   ← Python dependencies
├── server.py          ← QUIC chat server  (broadcast hub)
└── client.py          ← QUIC chat client  (interactive terminal)
```

---

## Setup

### 1. Install dependencies

```bash
pip install -r requirements.txt
```

Requires Python 3.8+.

### 2. Start the server

```bash
python server.py
```

The server listens on `localhost:4433` (UDP).  
It generates a temporary TLS certificate automatically — you do not need to
create one manually.

### 3. Connect clients

Open **two or more** terminals and run in each:

```bash
python client.py
```

Enter a name when prompted. Then type messages and press Enter to chat.
Type `quit` (or Ctrl-C) to disconnect.

---

## How it Works

```
Client A ──(QUIC stream)──┐
Client B ──(QUIC stream)──┼── server.py ── broadcasts to all other clients
Client C ──(QUIC stream)──┘
```

### server.py

1. Calls `serve()` which binds a **UDP socket** on port 4433.
2. For every incoming QUIC connection, aioquic creates a new
   `ChatServerProtocol` instance.
3. `quic_event_received()` is called whenever data arrives on a stream.
4. The server buffers bytes until it has a complete line (`\n`), then
   **broadcasts** the line to every other connected client by opening a new
   unidirectional stream on each destination connection.

### client.py

1. Opens a QUIC connection to the server.
2. Creates one **bidirectional stream** and sends all typed messages on it.
3. `quic_event_received()` prints any `StreamDataReceived` event (those are
   the broadcast messages pushed by the server).

### QUIC concepts visible in the code

| Concept | Where to look |
|---|---|
| UDP socket underneath QUIC | `serve()` call in `server.py` |
| One protocol instance per connection | `create_protocol=ChatServerProtocol` |
| Bidirectional stream (client→server) | `conn.create_stream()` in `client.py` |
| Unidirectional stream (server→client) | `get_next_available_stream_id(is_unidirectional=True)` in `server.py` |
| QUIC event loop | `quic_event_received()` in both files |
| Built-in TLS (required by protocol) | auto-generated cert inside `server.py` |

---

## Experiment Ideas

- Open 3 terminals with clients and observe the broadcast behaviour.
- Run `sudo tcpdump -i lo0 -n udp port 4433` to see the UDP packets QUIC
  runs on top of (macOS) or `-i lo` on Linux.
- Disconnect a client mid-conversation and watch the server handle it.
- Add a timestamp to broadcast messages (hint: `datetime.datetime.now()`).
- Modify the server to send the chat history to a newly joined client.
- Compare with a plain TCP socket chat — what changes in the code?

---

## Dependencies

| Package | Purpose |
|---|---|
| `aioquic` | Pure-Python QUIC / HTTP3 implementation |
| `cryptography` | Used internally by aioquic for TLS |
