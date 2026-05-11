# TCP Chat — Computer Networks Lab

A simple client-server **broadcast chat** application built on plain **TCP**
sockets using Python's `asyncio` library.

This implementation mirrors the [QUIC Chat](README.md) program so you can
compare the two protocols side-by-side.

---

## What is TCP?

TCP (Transmission Control Protocol) is the foundational reliable transport
protocol of the internet, defined in **RFC 793** (1981) and updated by
**RFC 9293** (2022).

| Property | Value |
|---|---|
| Layer | Transport (Layer 4) |
| Reliability | Yes — retransmission, ordering, duplicate detection |
| Connection setup | 3-way handshake (SYN → SYN-ACK → ACK) |
| Flow control | Yes (sliding window) |
| Congestion control | Yes (slow start, AIMD, …) |
| Encryption | Optional (add TLS on top) |
| Multiplexing | No — one ordered byte stream per connection |

---

## Project Structure

```
quic_ex/
├── README_TCP.md      ← this file
├── server_tcp.py      ← TCP chat server  (broadcast hub)
└── client_tcp.py      ← TCP chat client  (interactive terminal)
```

No extra dependencies — uses only Python's standard library.

---

## Setup & Usage

### 1. Start the server

```bash
python server_tcp.py
```

Listens on `localhost:5555` (TCP).

### 2. Connect clients

Open **two or more** terminals:

```bash
python client_tcp.py
```

Enter a name, then type messages. Type `quit` or press Ctrl-C to disconnect.

---

## How it Works

```
Client A ──(TCP connection)──┐
Client B ──(TCP connection)──┼── server_tcp.py ── broadcasts to all others
Client C ──(TCP connection)──┘
```

### server_tcp.py

1. `asyncio.start_server()` binds a TCP socket and registers `handle_client()`
   as the callback for every new incoming connection.
2. Each connection runs `handle_client()` as an **independent coroutine**,
   so many clients can be handled concurrently without threads.
3. The coroutine calls `reader.readline()` in a loop — this suspends (yields
   control) until the client sends a `\n`-terminated line.
4. The first line sets the client's display name; subsequent lines are
   **broadcast** to all other connected clients via their `StreamWriter`.

### client_tcp.py

1. `asyncio.open_connection()` performs the **TCP three-way handshake** and
   returns a `(StreamReader, StreamWriter)` pair.
2. Two concurrent async tasks run:
   - **`receive_loop`** — calls `reader.readline()` waiting for server pushes.
   - **`send_loop`** — reads stdin (in a thread executor) and writes lines.
3. `asyncio.wait(..., FIRST_COMPLETED)` stops both tasks when either exits.

### Key asyncio primitives used

| Primitive | Purpose |
|---|---|
| `asyncio.start_server()` | create listening TCP server socket |
| `asyncio.open_connection()` | connect to server (TCP handshake) |
| `StreamReader.readline()` | read one `\n`-terminated line, suspend until ready |
| `StreamWriter.write()` | buffer bytes for sending |
| `asyncio.create_task()` | run coroutine concurrently |
| `loop.run_in_executor()` | run blocking stdin read in a thread |

---

## TCP vs QUIC — Side-by-Side Comparison

| Aspect | TCP (`server_tcp.py`) | QUIC (`server.py`) |
|---|---|---|
| Socket type | TCP (`SOCK_STREAM`) | UDP (`SOCK_DGRAM`) |
| Python API | `asyncio.start_server` | `aioquic.asyncio.serve` |
| Connection setup | 3-way handshake | 1-RTT (TLS 1.3 combined) |
| Per-connection object | `StreamReader`/`StreamWriter` pair | `QuicConnectionProtocol` subclass |
| Receiving data | `reader.readline()` | `quic_event_received(StreamDataReceived)` |
| Sending to another client | `writer.write(data)` | `_quic.send_stream_data(sid, data)` + `transmit()` |
| TLS | Not used | Built-in, mandatory |
| Extra dependencies | None (stdlib only) | `aioquic`, `cryptography` |
| Stream multiplexing | One stream per connection | Multiple independent streams per connection |
| Head-of-line blocking | Yes | No |

> **Observation:** the application logic (buffering lines, broadcasting,
> tracking connected clients) is identical in both versions.  The only
> differences are in the transport API.

---

## Experiment Ideas

- Open 3 terminals with clients and observe the broadcast behaviour.
- Run `sudo tcpdump -i lo0 -n tcp port 5555` (macOS) or `-i lo` (Linux) to
  see the TCP packets — notice the SYN/SYN-ACK/ACK handshake on connect and
  FIN/ACK on disconnect.
- Kill a client with Ctrl-C and watch the server detect the disconnection.
- Add a per-client message counter and print it when the client leaves.
- Modify the server to keep the last 10 messages and send them to newly
  joined clients as a "history".
- Compare the connection setup packet count with the QUIC version using
  `tcpdump` on both ports simultaneously.
