"""
server.py — QUIC Chat Server
Computer Networks Lab

Listens for QUIC client connections on UDP port 4433.
Every message received from one client is broadcast to all other clients.

Usage:
    python server.py
"""

import asyncio
import datetime
from typing import Dict, Optional

from aioquic.asyncio import QuicConnectionProtocol, serve
from aioquic.quic.configuration import QuicConfiguration
from aioquic.quic.events import QuicEvent, StreamDataReceived, ConnectionTerminated

# ── TLS certificate (required internally by QUIC — students can ignore this) ─
from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa

def _make_cert():
    """Generate an in-memory self-signed certificate so QUIC can do its TLS handshake."""
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "localhost")])
    now = datetime.datetime.now(datetime.timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(name).issuer_name(name)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now)
        .not_valid_after(now + datetime.timedelta(days=1))
        .add_extension(
            x509.SubjectAlternativeName([x509.DNSName("localhost")]),
            critical=False,
        )
        .sign(key, hashes.SHA256())
    )
    return cert, key


# ── global registry: connection_id → protocol instance ───────────────────────
_connections: Dict[int, "ChatServerProtocol"] = {}
_id_counter = 0

def _alloc_id() -> int:
    global _id_counter
    _id_counter += 1
    return _id_counter


# ── per-connection protocol handler ──────────────────────────────────────────

class ChatServerProtocol(QuicConnectionProtocol):
    """
    One instance is created for every QUIC client connection.

    Subclasses QuicConnectionProtocol and overrides quic_event_received()
    to act on incoming stream data.
    """

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.conn_id: int = _alloc_id()
        self.username: Optional[str] = None
        self._buf: bytes = b""          # incomplete-line buffer

    # ── lifecycle ─────────────────────────────────────────────────────────────

    def connection_made(self, transport):
        super().connection_made(transport)
        _connections[self.conn_id] = self
        print(f"[server] client #{self.conn_id} connected  "
              f"(active={len(_connections)})")

    def connection_lost(self, exc):
        _connections.pop(self.conn_id, None)
        name = self.username or f"#{self.conn_id}"
        print(f"[server] {name} disconnected  (active={len(_connections)})")
        _broadcast(f"*** {name} left the chat ***", exclude=self.conn_id)
        super().connection_lost(exc)

    # ── QUIC event handler ────────────────────────────────────────────────────

    def quic_event_received(self, event: QuicEvent):
        """
        Called by aioquic whenever something happens on this connection.

        We only care about StreamDataReceived — which carries the actual
        chat messages sent by this client.
        """
        if isinstance(event, StreamDataReceived):
            self._buf += event.data
            # Process every complete line (messages end with '\n')
            while b"\n" in self._buf:
                line, self._buf = self._buf.split(b"\n", 1)
                self._on_line(line.decode(errors="replace").strip())

        # Always call the base implementation so internal bookkeeping works
        super().quic_event_received(event)

    # ── message handling ──────────────────────────────────────────────────────

    def _on_line(self, text: str):
        if not text:
            return

        # The first message from each client is treated as their name
        if self.username is None:
            self.username = text
            print(f"[server] client #{self.conn_id} → name '{self.username}'")
            _broadcast(f"*** {self.username} joined the chat ***",
                       exclude=self.conn_id)
            return

        line = f"{self.username}: {text}"
        print(f"[server] {line}")
        _broadcast(line, exclude=self.conn_id)


# ── broadcast helper (module-level, not a method) ─────────────────────────────

def _broadcast(message: str, exclude: int):
    """
    Send *message* to every connected client except the one with id *exclude*.

    Each broadcast opens a new server-initiated unidirectional stream so the
    client can read it as a fresh chunk of data.
    """
    data = (message + "\n").encode()
    for cid, proto in list(_connections.items()):
        if cid == exclude:
            continue
        try:
            # Server-initiated unidirectional stream (odd stream IDs, server side)
            sid = proto._quic.get_next_available_stream_id(is_unidirectional=True)
            proto._quic.send_stream_data(sid, data, end_stream=True)
            proto.transmit()   # flush the QUIC send buffer to the UDP socket
        except Exception as e:
            print(f"[server] could not reach #{cid}: {e}")


# ── entry point ───────────────────────────────────────────────────────────────

async def main():
    HOST = "localhost"
    PORT = 4433

    cert, key = _make_cert()
    config = QuicConfiguration(is_client=False)
    config.certificate = cert
    config.private_key = key

    print(f"[server] listening on {HOST}:{PORT}  (QUIC / UDP)")
    print("[server] waiting for clients …\n")

    # serve() starts a UDP socket and returns; connections come in asynchronously
    await serve(HOST, PORT, configuration=config, create_protocol=ChatServerProtocol)

    # Keep the event loop alive forever
    await asyncio.Future()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[server] stopped.")
