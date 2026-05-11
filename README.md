# Computer Networks Lab

Hands-on programming examples for an undergraduate computer networks course.

---

## Repository Structure

```
computer_networks_lab/
└── chat_example/
    ├── quic/
    │   ├── python/        QUIC chat server & client (Python / aioquic)
    │   └── java/          QUIC chat server & client (Java / kwik)
    ├── tcp/
    │   ├── python/        TCP chat server & client (Python / asyncio)
    │   └── java/          TCP chat server & client (Java / java.net)
    └── slides/            Beamer presentation — TCP vs QUIC comparison
```

---

## Chat Application

A broadcast chat program implemented four ways — same application logic, different transport protocol and language.
Clients connect to a server, send messages, and the server broadcasts each message to all other connected clients.

| Protocol | Language | Directory |
|---|---|---|
| QUIC (RFC 9000) | Python | `chat_example/quic/python/` |
| QUIC (RFC 9000) | Java   | `chat_example/quic/java/` |
| TCP  (RFC 9293) | Python | `chat_example/tcp/python/` |
| TCP  (RFC 9293) | Java   | `chat_example/tcp/java/` |

See the `README.md` inside each directory for setup and run instructions.

---

## Slides

`chat_example/slides/slides.pdf` — Beamer presentation covering:
- TCP and QUIC protocol summaries
- Connection setup and stream model diagrams
- Side-by-side code comparisons across all four implementations

Compile from source: `pdflatex slides.tex` (run twice for TOC).

---

## Quick Start

### TCP — Python
```bash
cd chat_example/tcp/python
python server_tcp.py          # terminal 1
python client_tcp.py          # terminal 2+
```

### TCP — Java
```bash
cd chat_example/tcp/java
javac ServerTCP.java ClientTCP.java
java ServerTCP                # terminal 1
java ClientTCP                # terminal 2+
```

### QUIC — Python
```bash
cd chat_example/quic/python
pip install -r requirements.txt
python server.py              # terminal 1
python client.py              # terminal 2+
```

### QUIC — Java
```bash
cd chat_example/quic/java
mvn package -q
java -cp target/quic-chat-1.0-jar-with-dependencies.jar ServerQUIC   # terminal 1
java -cp target/quic-chat-1.0-jar-with-dependencies.jar ClientQUIC   # terminal 2+
```

---

## Requirements

| | Minimum version |
|---|---|
| Python | 3.8 |
| Java | 11 |
| Maven | 3.6 |
