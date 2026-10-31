#!/usr/bin/env python3
"""desktop_login.py <devtools port> <user> <password> | <devtools port> relog: logs a desktop client in through its
devtools endpoint and prints LOGGED_IN or FAILED: <reason>. The login screen is drawn at fixed size in the middle of
the canvas, so every click is offset by the canvas size read from the client, never a fixed-mode coordinate.
"""
import json, sys, time, urllib.request

FIXED = (765, 503)
EXISTING_USER, LOGIN_FIELD = (460, 290), (360, 248)


def call(port, name, **args):
    req = urllib.request.Request(f"http://127.0.0.1:{port}/mcp", json.dumps({"jsonrpc": "2.0", "id": 1, "method": "tools/call",
                                 "params": {"name": name, "arguments": args}}).encode(), {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"})
    with urllib.request.urlopen(req, timeout=30) as r:
        body = r.read().decode()
    line = next((l[5:] for l in body.splitlines() if l.startswith("data:")), body)
    out = json.loads(line).get("result", {})
    return "".join(c.get("text", "") for c in out.get("content", []) if c.get("type") == "text") or json.dumps(out)


def state(port):
    s = call(port, "get_client_state")
    try:
        s = json.loads(s)
    except ValueError:
        return {"gameState": s}
    return s


def wait(port, states):
    """Blocks until the game state is one of states; the client reports a state on every read, so nothing is timed."""
    while (s := state(port)).get("gameState") not in states:
        time.sleep(2)
    return s


def click(port, s, xy):
    w, h = s.get("canvasWidth") or FIXED[0], s.get("canvasHeight") or FIXED[1]
    call(port, "click", x=xy[0] + (w - FIXED[0]) // 2, y=xy[1] + (h - FIXED[1]) // 2)


def login(port, user, password):
    s = wait(port, ("LOGIN_SCREEN", "LOGGED_IN"))
    if s["gameState"] == "LOGGED_IN":
        return "LOGGED_IN"
    click(port, s, EXISTING_USER)
    time.sleep(1)
    click(port, s, LOGIN_FIELD)
    call(port, "press_key", key="BACK_SPACE", times=24)
    call(port, "type_chat", text=user, send=False)
    call(port, "press_key", key="TAB")
    call(port, "press_key", key="BACK_SPACE", times=24)
    call(port, "type_chat", text=password, send=True)
    s = wait(port, ("LOGGED_IN", "LOGIN_SCREEN"))
    return "LOGGED_IN" if s["gameState"] == "LOGGED_IN" else "FAILED: " + (s.get("loginMessage") or "back at the login screen")


def relog(port):
    call(port, "type_chat", text="::drop", send=True)
    wait(port, ("LOGIN_SCREEN", "CONNECTION_LOST"))
    return login(port, "", "")  # the client keeps the last credentials: Existing User, then Enter


if __name__ == "__main__":
    port = sys.argv[1]
    try:
        r = relog(port) if sys.argv[2] == "relog" else login(port, sys.argv[2], sys.argv[3])
    except OSError as e:
        r = f"FAILED: devtools endpoint on {port} does not answer ({e})"
    print(r)
    sys.exit(0 if r == "LOGGED_IN" else 1)
