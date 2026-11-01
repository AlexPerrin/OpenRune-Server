#!/usr/bin/env python3
"""issuer.py: the test desktop's token issuer. A token is <account>.<expiry, unix seconds>.<HMAC-SHA256 of the two by the
issuer's secret, base64url>: at most 67 bytes, as the client carries it in its RSA login block (about 90 bytes; an RS256 JWT
does not fit, nor an Ed25519 signature with its account). The server's game.yml token-login block holds the secret.
  issuer.py key DIR                makes DIR/secret and DIR/tls.pem (with tls.key) once; prints the game.yml token-login block
  issuer.py mint DIR ACCOUNT [--exp S]   prints a token for ACCOUNT, expiring S seconds from now (negative: already
                                   expired); another DIR's token is a forged one
  issuer.py serve DIR              the game-session API on https :8080 (jav_config param 11; the client takes https only,
                                   and trusts tls.pem by its truststore): tokens answers DIR/token, read at each request,
                                   so a token written there is the one the desktop's next login sends
"""
import base64, hashlib, hmac, http.server, json, os, ssl, subprocess, sys, time
from pathlib import Path


def b64(b):
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def key(d):
    d = Path(d); d.mkdir(parents=True, exist_ok=True); sec = d / "secret"
    if not sec.exists():
        sec.write_text(base64.b64encode(os.urandom(32)).decode())
    if not (d / "tls.pem").exists():
        subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "3650", "-subj", "/CN=127.0.0.1", "-addext",
                        "subjectAltName=IP:127.0.0.1", "-keyout", str(d / "tls.key"), "-out", str(d / "tls.pem")], check=True, capture_output=True)
    return f'token-login:\n  secret: "{sec.read_text()}"'


def mint(d, account, exp=3600):
    body = f"{account}.{int(time.time()) + int(exp)}"
    return body + "." + b64(hmac.new(base64.b64decode((Path(d) / "secret").read_text()), body.encode(), hashlib.sha256).digest())


class GameSession(http.server.BaseHTTPRequestHandler):
    dir = Path(".")  # serve's DIR

    def answer(self):
        p = self.path.split("?")[0]
        if p.endswith("game-session/v1/tokens"):
            body = {"token": (self.dir / "token").read_text().strip()}
        elif p.endswith("game-session/v1/sessions"):
            body = {"sessionId": "harness"}
        elif p.endswith("game-session/v1/accounts"):
            body = [{"accountId": "1", "displayName": "harness", "userHash": "1"}]
        else:
            return self.send_error(404)
        b = json.dumps(body).encode()
        self.send_response(200); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(b))); self.end_headers()
        self.wfile.write(b)

    do_GET = do_POST = answer


if __name__ == "__main__":
    a = sys.argv[1:]
    if a[0] == "key":
        print(key(a[1]))
    elif a[0] == "mint":
        print(mint(a[1], a[2], a[4] if a[3:4] == ["--exp"] else 3600))
    elif a[0] == "serve":
        GameSession.dir = Path(a[1])
        srv = http.server.ThreadingHTTPServer(("", 8080), GameSession)
        tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER); tls.load_cert_chain(f"{a[1]}/tls.pem", f"{a[1]}/tls.key")
        srv.socket = tls.wrap_socket(srv.socket, server_side=True)
        srv.serve_forever()
