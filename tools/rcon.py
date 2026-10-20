"""RCON minimo pro servidor de teste (run-server/). Le a senha do server.properties.

Uso: python tools/rcon.py "comando" ["comando2" ...]
"""
import os
import socket
import struct
import sys

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def senha():
    with open(os.path.join(RAIZ, "run-server", "server.properties"), encoding="utf-8") as fp:
        for linha in fp:
            if linha.startswith("rcon.password="):
                return linha.split("=", 1)[1].strip()
    raise SystemExit("rcon.password nao encontrado em run-server/server.properties")


def pacote(s, rid, tipo, corpo):
    dados = struct.pack("<ii", rid, tipo) + corpo.encode("utf-8") + b"\x00\x00"
    s.sendall(struct.pack("<i", len(dados)) + dados)


def ler(s):
    tam = struct.unpack("<i", s.recv(4))[0]
    dados = b""
    while len(dados) < tam:
        dados += s.recv(tam - len(dados))
    rid, _ = struct.unpack("<ii", dados[:8])
    return rid, dados[8:-2].decode("utf-8", "replace")


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    with socket.create_connection(("127.0.0.1", 25575), timeout=10) as s:
        pacote(s, 1, 3, senha())
        if ler(s)[0] == -1:
            raise SystemExit("senha RCON recusada")
        for i, cmd in enumerate(sys.argv[1:], start=2):
            pacote(s, i, 2, cmd)
            print("> " + cmd + "\n" + ler(s)[1])


if __name__ == "__main__":
    main()
