#!/usr/bin/env python3
"""desktop_login.py <devtools port> <user> <password> | <devtools port> logout: logs a desktop client in (prints LOGGED_IN
or FAILED: <reason>) or out through the logout tab's button (prints LOGIN_SCREEN) through its devtools endpoint. Keys only:
the login screen's place on the canvas changes with the window, so no point on it is clicked.
"""
import json, sys, time, urllib.error, urllib.request

LOGOUT_BUTTON = 11927560  # component.logout:logout


class Refused(Exception):
    """The endpoint answered with an error (an unknown key; get_client_state 'Cancelled' while the client starts)."""


def call(port, name, **args):
    req = urllib.request.Request(f"http://127.0.0.1:{port}/mcp", json.dumps({"jsonrpc": "2.0", "id": 1, "method": "tools/call",
                                 "params": {"name": name, "arguments": args}}).encode(), {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"})
    with urllib.request.urlopen(req, timeout=30) as r:
        body = r.read().decode()
    line = next((l[5:] for l in body.splitlines() if l.startswith("data:")), body)
    msg = json.loads(line); out = msg.get("result", {})
    text = "".join(c.get("text", "") for c in out.get("content", []) if c.get("type") == "text") or json.dumps(out)
    if "error" in msg or out.get("isError"):  # a refused call (an unknown key) must not pass as done
        raise Refused(f"{name}: {msg.get('error') or text}")
    return text


def state(port):
    try:
        s = call(port, "get_client_state")
    except (Refused, TimeoutError):
        return {}  # no state yet (the client is starting, its thread busy): the next read has one
    except urllib.error.URLError as e:
        if isinstance(e.reason, TimeoutError):
            return {}
        raise
    try:
        s = json.loads(s)
    except ValueError:
        return {"gameState": s}
    return s


def wait(port, states, poll=2, leave=False):
    """Blocks until the game state is one of states (leave: is none of them); the client reports a state on every read,
    so nothing is timed."""
    while (g := (s := state(port)).get("gameState")) is None or (g in states) == leave:
        time.sleep(poll)
    return s


def clear(port):  # press_key takes one key per call
    for _ in range(24):
        call(port, "press_key", key="BACKSPACE")


def login(port, user, password):
    s = wait(port, ("LOGIN_SCREEN", "LOGGED_IN"))
    if s["gameState"] == "LOGGED_IN":
        return "LOGGED_IN"
    call(port, "press_key", key="ESCAPE")  # any login screen page back to the welcome page
    state(port), state(port)  # each read runs on a client frame: the page has changed before the next key (one frame took both)
    call(port, "press_key", key="ENTER")   # Existing User: the form, its login field focused
    clear(port)
    call(port, "type_chat", text=user, send=False)
    call(port, "press_key", key="TAB")
    clear(port)
    call(port, "type_chat", text=password, send=True)
    # the attempt starts at LOGIN_SCREEN and runs as LOGGING_IN: LOGIN_SCREEN right after Enter is not yet a result
    # shortcut: a refusal faster than the 0.1 s poll is never seen and the wait blocks; a devtools login tool removes it (harness-desktop-login-devtools)
    wait(port, ("LOGIN_SCREEN",), poll=0.1, leave=True)
    s = wait(port, ("LOGGED_IN", "LOGIN_SCREEN"))
    return "LOGGED_IN" if s["gameState"] == "LOGGED_IN" else "FAILED: back at the login screen: the server refused the login"


def logout(port):
    call(port, "invoke_menu_action", param0=-1, param1=LOGOUT_BUTTON, type="CC_OP", identifier=1, option="Logout")
    return wait(port, ("LOGIN_SCREEN",))["gameState"]


if __name__ == "__main__":
    port = sys.argv[1]
    try:
        r = logout(port) if sys.argv[2] == "logout" else login(port, sys.argv[2], sys.argv[3])
    except (OSError, Refused) as e:
        r = f"FAILED: devtools endpoint on {port}: {e}"
    print(r)
    sys.exit(0 if r in ("LOGGED_IN", "LOGIN_SCREEN") else 1)
