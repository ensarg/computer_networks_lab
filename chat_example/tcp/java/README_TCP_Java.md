# TCP Chat — Java Implementation
# Computer Networks Lab

A simple client-server **broadcast chat** application built on plain **TCP**
sockets using Java's standard `java.net` library.

This mirrors the [Python TCP Chat](README_TCP.md) so you can compare how the
same networking concepts map to a different language and threading model.

---

## What You Need

- Java 8 or later (`javac`, `java`)
- No external libraries — uses only the Java standard library

---

## Files

```
computer_networks_lab/
├── README_TCP_Java.md   ← this file
├── ServerTCP.java       ← TCP broadcast server
└── ClientTCP.java       ← interactive TCP chat client
```

---

## Compile & Run

### 1. Compile both files

```bash
javac ServerTCP.java ClientTCP.java
```

This produces `ServerTCP.class` and `ClientTCP.class`.

### 2. Start the server

```bash
java ServerTCP
```

Listens on **port 5556** (TCP).

### 3. Connect clients

Open **two or more** terminals:

```bash
java ClientTCP
```

Enter a name when prompted, then type messages. Type `quit` or press Ctrl-C
to disconnect.

---

## How It Works

```
Client A ──(TCP connection)──┐
Client B ──(TCP connection)──┼── ServerTCP ── broadcasts to all others
Client C ──(TCP connection)──┘
```

### ServerTCP.java

| Step | Code | What happens |
|---|---|---|
| Open listening socket | `new ServerSocket(PORT)` | OS binds a TCP port and allocates a backlog queue |
| Wait for connection | `serverSocket.accept()` | Blocks until a client completes the 3-way handshake; returns a connected `Socket` |
| Spawn a thread | `new Thread(handler).start()` | Each client gets its own thread so they are handled concurrently |
| Read a line | `reader.readLine()` | Blocks until `\n` arrives; returns `null` when the client closes the connection |
| Broadcast | `out.write(data); out.flush()` | Writes bytes to every other client's output stream |
| Thread-safe map | `ConcurrentHashMap` | Allows multiple threads to add/remove clients safely |

### ClientTCP.java

| Step | Code | What happens |
|---|---|---|
| Connect | `new Socket(HOST, PORT)` | Performs the TCP **3-way handshake** (SYN → SYN-ACK → ACK) |
| Send a line | `writer.println(line)` | Writes the message + `\n` to the server |
| Receive thread | `new Thread(receiverThread)` | Runs concurrently; calls `readLine()` waiting for server broadcasts |
| Daemon thread | `setDaemon(true)` | Thread is killed automatically when the main thread exits |

### Java networking classes used

| Class | Purpose |
|---|---|
| `ServerSocket` | Listens for incoming TCP connections |
| `Socket` | Represents one end of a TCP connection |
| `BufferedReader` / `InputStreamReader` | Read text lines from a socket's input stream |
| `PrintWriter` / `OutputStreamWriter` | Write text lines to a socket's output stream |
| `ConcurrentHashMap` | Thread-safe registry of all connected clients |
| `Thread` | Runs each client handler concurrently |

---

## Java vs Python — TCP Side-by-Side

| Aspect | Java (`ServerTCP.java`) | Python (`server_tcp.py`) |
|---|---|---|
| Concurrency model | One **thread** per client | One **coroutine** per client (`async def`) |
| Accept loop | `serverSocket.accept()` blocks; thread spawned manually | `asyncio.start_server()` registers callback |
| Read a line | `BufferedReader.readLine()` | `await reader.readline()` |
| Write a line | `PrintWriter.println()` | `writer.write(data)` |
| Shared client list | `ConcurrentHashMap` (thread-safe) | Plain `dict` (safe — single async thread) |
| Error on disconnect | `readLine()` returns `null` or throws `IOException` | `readline()` returns `b""` |
| Extra dependencies | None (stdlib) | None (stdlib) |

> **Key difference:** Python uses *cooperative multitasking* (one thread, tasks
> yield voluntarily with `await`). Java uses *preemptive multitasking* (OS
> scheduler switches between threads). Both approaches handle many clients
> concurrently — they just do it differently.

---

## Experiment Ideas

- Open 3 terminals with clients and observe the broadcast behaviour.
- Run `sudo tcpdump -i lo0 -n tcp port 5556` (macOS) or `-i lo` (Linux) to
  see the TCP handshake packets (SYN, SYN-ACK, ACK) on every connection.
- Kill a client with Ctrl-C and watch the server detect the disconnect.
- Add a timestamp to each broadcast message (hint: `java.time.LocalTime.now()`).
- Change the server to keep a list of the last 5 messages and send them to
  newly joined clients as a chat history.
- Try connecting the **Java client** to the **Python server** and vice versa —
  they speak the same protocol (plain TCP + newline-delimited text) so it works.
