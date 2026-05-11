"""
client.py — QUIC Chat Client
Computer Networks Lab

Connects to the chat server over QUIC (UDP port 4433).
Sends messages typed by the user and prints messages from other clients.

Usage:
    python client.py
"""

import asyncio
import sys

from aioquic.asyncio import QuicConnectionProtocol, connect
from aioquic.quic.configuration import QuicConfiguration
from aioquic.quic.events import QuicEvent, StreamDataReceived


# ── client-side protocol handler ─────────────────────────────────────────────

class ChatClientProtocol(QuicConnectionProtocol):
    """
    Handles incoming QUIC events on the client side.

    The only event we care about is StreamDataReceived — that is when
    the server pushes a broadcast message to us.
    """

    def quic_event_received(self, event: QuicEvent):
        if isinstance(event, StreamDataReceived):
            # We handle incoming data here ourselves — don't call super() for this
            # event because the base class would create a StreamWriter for the
            # server-initiated stream, which can't be written to and causes errors.
            text = event.data.decode(errors="replace").strip()
            if text:
                # Print the incoming message, then re-draw the prompt
                print(f"\r{text}\n> ", end="", flush=True)
        else:
            super().quic_event_received(event)


# ── entry point ───────────────────────────────────────────────────────────────

async def main():
    HOST = "localhost"
    PORT = 4433

    username = input("Enter your name: ").strip() or "anonymous"

    # ssl.CERT_NONE: skip certificate validation (OK for a local classroom demo)
    import ssl
    config = QuicConfiguration(is_client=True)
    config.verify_mode = ssl.CERT_NONE

    print(f"[client] connecting to {HOST}:{PORT} …")

    async with connect(
        HOST, PORT,
        configuration=config,
        create_protocol=ChatClientProtocol,
    ) as conn:

        # Open a bidirectional stream — we write messages on this stream;
        # the server receives them and broadcasts to others.
        reader, writer = await conn.create_stream()

        print(f"[client] connected!  You are '{username}'")
        print("[client] type a message and press Enter.  'quit' to exit.\n")

        # First message to the server sets our display name
        writer.write((username + "\n").encode())

        # Read lines from the terminal and send them
        loop = asyncio.get_event_loop()

        while True:
            print("> ", end="", flush=True)
            try:
                line = await loop.run_in_executor(None, sys.stdin.readline)
            except (EOFError, KeyboardInterrupt):
                break

            line = line.rstrip("\n").strip()
            if not line:
                continue
            if line.lower() in ("quit", "exit", "q"):
                break

            writer.write((line + "\n").encode())

        writer.write_eof()

    print("\n[client] disconnected.")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[client] disconnected.")
