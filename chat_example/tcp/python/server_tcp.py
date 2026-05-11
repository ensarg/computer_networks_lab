"""
server_tcp.py — TCP Chat Server
Computer Networks Lab

Listens for TCP client connections on port 5555.
Every message received from one client is broadcast to all other clients.

Usage:
    python server_tcp.py
"""

import asyncio
from typing import Dict, Optional

# ── global registry: connection_id → writer ──────────────────────────────────
_writers: Dict[int, asyncio.StreamWriter] = {}
_usernames: Dict[int, str] = {}
_id_counter = 0

def _alloc_id() -> int:
    global _id_counter
    _id_counter += 1
    return _id_counter


# ── broadcast helper ──────────────────────────────────────────────────────────

def _broadcast(message: str, exclude: int):
    """Send *message* to every connected client except the one with id *exclude*."""
    data = (message + "\n").encode()
    for cid, writer in list(_writers.items()):
        if cid == exclude:
            continue
        try:
            writer.write(data)
        except Exception as e:
            print(f"[server] could not reach #{cid}: {e}")


# ── per-connection handler ────────────────────────────────────────────────────

async def handle_client(reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
    """
    Called by asyncio for every new TCP connection.

    asyncio.start_server() calls this coroutine with a StreamReader/StreamWriter
    pair.  We loop reading lines until the client disconnects.
    """
    conn_id = _alloc_id()
    addr = writer.get_extra_info("peername")
    _writers[conn_id] = writer
    username: Optional[str] = None

    print(f"[server] client #{conn_id} connected from {addr}  "
          f"(active={len(_writers)})")

    try:
        while True:
            # Read one line at a time (messages end with '\n')
            data = await reader.readline()
            if not data:
                break                       # client closed the connection

            text = data.decode(errors="replace").strip()
            if not text:
                continue

            # First message from each client is their display name
            if username is None:
                username = text
                _usernames[conn_id] = username
                print(f"[server] client #{conn_id} → name '{username}'")
                _broadcast(f"*** {username} joined the chat ***", exclude=conn_id)
                continue

            line = f"{username}: {text}"
            print(f"[server] {line}")
            _broadcast(line, exclude=conn_id)

    except (asyncio.IncompleteReadError, ConnectionResetError):
        pass    # client disconnected abruptly

    finally:
        _writers.pop(conn_id, None)
        _usernames.pop(conn_id, None)
        name = username or f"#{conn_id}"
        print(f"[server] {name} disconnected  (active={len(_writers)})")
        _broadcast(f"*** {name} left the chat ***", exclude=conn_id)
        try:
            writer.close()
            await writer.wait_closed()
        except Exception:
            pass


# ── entry point ───────────────────────────────────────────────────────────────

async def main():
    HOST = "localhost"
    PORT = 5555

    # asyncio.start_server() opens a TCP socket and calls handle_client()
    # for every new incoming connection.
    server = await asyncio.start_server(handle_client, HOST, PORT)

    print(f"[server] listening on {HOST}:{PORT}  (TCP)")
    print("[server] waiting for clients …\n")

    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[server] stopped.")
