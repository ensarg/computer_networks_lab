"""
client_tcp.py — TCP Chat Client
Computer Networks Lab

Connects to the chat server over TCP (port 5555).
Sends messages typed by the user and prints messages from other clients.

Usage:
    python client_tcp.py
"""

import asyncio
import sys


# ── entry point ───────────────────────────────────────────────────────────────

async def main():
    HOST = "localhost"
    PORT = 5555

    username = input("Enter your name: ").strip() or "anonymous"

    print(f"[client] connecting to {HOST}:{PORT} …")

    # asyncio.open_connection() performs the TCP three-way handshake and
    # returns a (StreamReader, StreamWriter) pair.
    reader, writer = await asyncio.open_connection(HOST, PORT)

    print(f"[client] connected!  You are '{username}'")
    print("[client] type a message and press Enter.  'quit' to exit.\n")

    # First message to the server sets our display name
    writer.write((username + "\n").encode())

    # ── receiver task: print messages pushed by the server ────────────────────
    async def receive_loop():
        while True:
            try:
                data = await reader.readline()
            except (asyncio.IncompleteReadError, ConnectionResetError):
                break
            if not data:
                break
            text = data.decode(errors="replace").strip()
            if text:
                print(f"\r{text}\n> ", end="", flush=True)
        print("\n[client] server closed the connection.")

    # ── sender task: read stdin and send lines to the server ──────────────────
    async def send_loop():
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

    # Run both tasks concurrently; stop when either finishes
    done, pending = await asyncio.wait(
        [
            asyncio.create_task(receive_loop()),
            asyncio.create_task(send_loop()),
        ],
        return_when=asyncio.FIRST_COMPLETED,
    )
    for task in pending:
        task.cancel()

    try:
        writer.close()
        await writer.wait_closed()
    except Exception:
        pass

    print("\n[client] disconnected.")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[client] disconnected.")
