# QUIC Chat — Java Implementation
# Computer Networks Lab

A simple client-server **broadcast chat** application built on the **QUIC**
transport protocol using the **kwik** library — a pure-Java implementation
of QUIC (RFC 9000).

This mirrors the [Python QUIC Chat](README.md) so you can compare how the same
QUIC concepts map to Java's threading and object model.

---

## What You Need

- Java 11 or later
- Maven (`mvn`)

No other installation — kwik and its dependencies are downloaded automatically
by Maven.

---

## Files

```
computer_networks_lab/
├── README_QUIC_Java.md              ← this file
└── quic_java/
    ├── pom.xml                      ← Maven build file (declares kwik dependency)
    └── src/main/java/
        ├── ServerQUIC.java          ← QUIC broadcast server
        └── ClientQUIC.java          ← interactive QUIC chat client
```

---

## Build & Run

### 1. Build (download dependencies + compile)

```bash
cd quic_java
mvn package -q
```

This produces `target/quic-chat-1.0-jar-with-dependencies.jar` — a single JAR
with kwik bundled inside.

### 2. Start the server

```bash
java -cp target/quic-chat-1.0-jar-with-dependencies.jar ServerQUIC
```

- Listens on **UDP port 4434** (QUIC runs over UDP)
- On first run, generates a temporary TLS certificate (`chat-server.p12`) using
  `keytool` (bundled with every JDK) — you don't need to do anything manually.

### 3. Connect clients

Open **two or more** terminals:

```bash
java -cp target/quic-chat-1.0-jar-with-dependencies.jar ClientQUIC
```

Enter a name when prompted. Type messages and press Enter. Type `quit` to leave.

---

## How It Works

```
Client A ──(QUIC bidirectional stream)──┐
Client B ──(QUIC bidirectional stream)──┼── ServerQUIC ── broadcasts to all others
Client C ──(QUIC bidirectional stream)──┘
```

### ServerQUIC.java — key concepts

| Step | Code | What happens |
|---|---|---|
| Register ALPN protocol | `server.registerApplicationProtocol("chat", factory)` | QUIC uses ALPN (TLS extension) to agree on the application protocol during handshake |
| One object per connection | `factory.createConnection(...)` returns `ChatSession` | kwik calls this once per incoming QUIC connection |
| Receive client message | `acceptPeerInitiatedStream(QuicStream stream)` | kwik calls this when the client opens a new stream toward the server |
| Read a line | `reader.readLine()` | Reads bytes from the stream's `InputStream` until `\n` |
| Broadcast to one client | `connection.createStream(false)` + `out.write(...)` | Opens a **server-initiated unidirectional stream** and writes the message |
| Detect disconnect | `connection.setConnectionListener(...)` → `disconnected()` | kwik calls this when the QUIC connection closes |

### ClientQUIC.java — key concepts

| Step | Code | What happens |
|---|---|---|
| Build connection | `QuicClientConnection.newBuilder()...build()` | Configures but does not connect yet |
| Skip cert check | `.noServerCertificateCheck()` | Disables TLS certificate validation (OK for classroom demo) |
| ALPN | `.applicationProtocol("chat")` | Tells the server which protocol we speak during the TLS handshake |
| Allow server streams | `.maxOpenPeerInitiatedUnidirectionalStreams(100)` | Grants the server permission to open up to 100 push streams |
| Register push handler | `connection.setPeerInitiatedStreamCallback(stream -> ...)` | Called whenever the server opens a new stream to us |
| Connect (handshake) | `connection.connect()` | Performs QUIC + TLS 1.3 handshake (faster than TCP + TLS) |
| Send messages | `connection.createStream(true)` → `writer.println(...)` | Opens a **bidirectional stream** and writes chat messages |

### QUIC stream directions in this program

| Stream | Direction | Who opens it | Purpose |
|---|---|---|---|
| Bidirectional | Client → Server | Client | Sends chat messages to server |
| Unidirectional | Server → Client | Server | Pushes broadcast messages to client |

---

## Java (kwik) vs Python (aioquic) — QUIC Side-by-Side

| Aspect | Java `ServerQUIC.java` | Python `server.py` |
|---|---|---|
| Library | `kwik` 0.10.9 | `aioquic` |
| Concurrency | One thread per stream (explicit `new Thread(...)`) | Coroutines (`async def`, `await`) |
| Connection callback | `ApplicationProtocolConnectionFactory.createConnection()` | `QuicConnectionProtocol` subclass instantiated per connection |
| Receive stream data | `acceptPeerInitiatedStream(QuicStream)` override | `quic_event_received(StreamDataReceived)` override |
| Send to a client | `connection.createStream(false)` + `OutputStream` | `_quic.send_stream_data(sid, data)` + `transmit()` |
| Certificate | `KeyStore` via `keytool` | auto-generated with `cryptography` library |
| ALPN | `.applicationProtocol("chat")` | `QuicConfiguration(alpn_protocols=["chat"])` |
| TLS cert skip | `.noServerCertificateCheck()` | `config.verify_mode = ssl.CERT_NONE` |

---

## Java QUIC vs Java TCP — Comparison

| Aspect | `ServerQUIC.java` | `ServerTCP.java` |
|---|---|---|
| Transport socket | UDP (`DatagramSocket`) — managed by kwik | TCP (`ServerSocket`) |
| Protocol | QUIC (RFC 9000) over UDP | TCP (RFC 793/9293) |
| TLS | Built-in (TLS 1.3 mandatory) | None (plain text) |
| Connection setup | 1 RTT (QUIC + TLS combined) | 1 RTT (TCP 3-way handshake) + optional TLS |
| Streams per connection | Many independent QUIC streams | One ordered byte stream |
| Head-of-line blocking | No | Yes |
| Concurrency model | Thread per stream | Thread per connection |
| Server API | `ServerConnector` + `ApplicationProtocolConnectionFactory` | `ServerSocket.accept()` loop |

---

## Experiment Ideas

- Open 3 terminals with clients and observe the broadcast behaviour.
- Run `sudo tcpdump -i lo0 -n udp port 4434` (macOS) or `-i lo` (Linux) to see
  UDP packets — QUIC runs on top of UDP.
- Observe the ALPN in action: change `ALPN = "chat"` in the client to a
  different string and watch the connection be rejected.
- Add a timestamp to each broadcast (hint: `java.time.LocalTime.now()`).
- Try connecting the **Java client** to the **Python QUIC server** (port 4433) —
  both speak QUIC with ALPN "chat" so it should work.

---

## About kwik

[kwik](https://github.com/ptrd/kwik) is a pure-Java implementation of the QUIC
protocol (RFC 9000) by Peter Doornbosch. It requires no native libraries, making
it easy to use on any platform where Java runs.

Maven dependency:
```xml
<dependency>
    <groupId>tech.kwik</groupId>
    <artifactId>kwik</artifactId>
    <version>0.10.9</version>
</dependency>
```
