#!/usr/bin/env python3
"""desktop_login.py <devtools port> [keep <server container>] <user> <password> | <devtools port> [keep <server container>] token | <devtools port> logout: logs a desktop
client in, by its login form or (token) by the token login its Jagex session variables start (prints LOGGED_IN or
FAILED: <reason>; LOGGED_IN only once the inventory widget shows container 93), or out through the logout tab's button (prints LOGIN_SCREEN) through its devtools endpoint; keep: logs it in again after each logout, until the desktop is gone. Keys only:
the login screen's place on the canvas changes with the window, so no point on it is clicked.
"""
import contextlib, fcntl, json, subprocess, sys, tempfile, time, urllib.error, urllib.request

LOGOUT_BUTTON = 11927560  # component.logout:logout
INVENTORY_ITEMS = 9764864  # component.inventory:items, one dynamic child per slot of container 93
FRAMES = 10  # state reads after Enter: the client takes a key on its next frame, the rest a margin on a loaded host


class Refused(Exception):
    """The endpoint answered with an error (an unknown key; get_client_state 'Cancelled' while the client starts)."""


def call(port, name, **args):
    req = urllib.request.Request(f"http://127.0.0.1:{port}/mcp", json.dumps({"jsonrpc": "2.0", "id": 1, "method": "tools/call",
                                 "params": {"name": name, "arguments": args}}).encode(), {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"})
    while True:
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                body = r.read().decode()
            break
        except urllib.error.URLError as e:  # urlopen wraps a failure to connect or send: the request never reached the client
            if name == "get_client_state" or not isinstance(e.reason, TimeoutError):
                raise
            wait(port, (), leave=True)  # the client is busy starting: resend once it answers a state read again
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


def attempt(port, again=True):
    """Presses Enter and returns the attempt's end state: LOGGED_IN or LOGIN_SCREEN, or None when the key started no attempt.
    The client takes a key on its next frame and a submit leaves LOGIN_SCREEN on that frame, and each state read runs on a
    frame, so FRAMES reads still on LOGIN_SCREEN are a page Enter does not submit (a refusal's response page): Escape
    leaves it, once (again), and Enter is pressed on the page under it."""
    call(port, "press_key", key="ENTER")
    for _ in range(FRAMES):
        if state(port).get("gameState") != "LOGIN_SCREEN":
            return wait(port, ("LOGGED_IN", "LOGIN_SCREEN"))["gameState"]
    if not again:
        return None
    call(port, "press_key", key="ESCAPE")
    return attempt(port, again=False)


def login(port, user, password):
    s = wait(port, ("LOGIN_SCREEN", "LOGGED_IN"))
    if s["gameState"] == "LOGGED_IN":
        return inventory(port)
    call(port, "press_key", key="ESCAPE")  # any login screen page back to the welcome page
    state(port), state(port)  # each read runs on a client frame: the page has changed before the next key (one frame took both)
    call(port, "press_key", key="ENTER")   # Existing User: the form, its login field focused
    clear(port)
    call(port, "type_chat", text=user, send=False)
    call(port, "press_key", key="TAB")
    clear(port)
    call(port, "type_chat", text=password, send=False)
    return result(port, attempt(port, again=False), "the server refused the login")


def token(port):
    s = wait(port, ("LOGIN_SCREEN", "LOGGED_IN"))
    if s["gameState"] == "LOGGED_IN":
        return inventory(port)
    return result(port, attempt(port), "the server refused the token")   # the session's login page: Enter plays; the client fetches its token from param 11


def result(port, g, refused):
    if g is None:
        return "FAILED: Enter started no login: the login screen is on a page Enter does not submit and Escape does not leave (the disconnected dialog, a refusal's page)"
    return inventory(port) if g == "LOGGED_IN" else f"FAILED: back at the login screen: {refused}"


def inventory(port):
    """LOGGED_IN when the inventory widget's slots show container 93's items, else FAILED naming both (an empty widget over
    a full container leaves every item_action unfound); waits until the logged-in client has built the widget's slots."""
    while True:
        try:
            slots = json.loads(call(port, "get_widget", componentId=INVENTORY_ITEMS)).get("dynamicChildren") or []
        except Refused:  # not built yet: the interface opens after LOGGED_IN
            slots = []
        if slots:
            break
        time.sleep(1)
    shown = {c["childIndex"]: (c["itemId"], c["itemQuantity"]) for c in slots if not c.get("selfHidden") and c.get("itemId", -1) > 0}
    try:
        held = {i["slot"]: (i["itemId"], i["quantity"]) for i in json.loads(call(port, "get_inventory", inventoryId=93))["items"]}
    except Refused:  # no container 93: the server sent no inventory, an empty one
        held = {}
    return "LOGGED_IN" if shown == held else f"FAILED: the inventory widget shows {shown} but container 93 holds {held}"


def logout(port):
    call(port, "invoke_menu_action", param0=-1, param1=LOGOUT_BUTTON, type="CC_OP", identifier=1, option="Logout")
    return wait(port, ("LOGIN_SCREEN",))["gameState"]


def keep(port, act, ready):
    """A run's desktop that is logged out (a disconnect, a server restart, any logout) is logged in again by act: it waits
    for LOGGED_IN, then for LOGIN_SCREEN, then logs in. A failed login is tried again once the server is ready after it
    (ready(since): a server that was down or restarting), and never while the same server refuses it; a login made by
    another waits for the next logout. Ends when the endpoint is gone (the desktop stopped)."""
    while True:
        wait(port, ("LOGGED_IN",)); wait(port, ("LOGIN_SCREEN",))
        while True:
            since = time.time()
            with locked(port):
                r = act()
            print(r, flush=True)
            while r != "LOGGED_IN" and not ready(since) and state(port).get("gameState") != "LOGGED_IN":
                time.sleep(2)
            if r == "LOGGED_IN" or not ready(since):
                break


def server_ready(container):  # ready(since): the container logged "Server ready in" at or after since
    def ready(since):
        t = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(since))
        out = subprocess.run(["docker", "logs", "--since", t, container], capture_output=True, text=True)
        return "Server ready in" in out.stdout + out.stderr
    return ready


@contextlib.contextmanager
def locked(port):  # one action at a time on a desktop: up, restart and keep each log it in
    with open(f"{tempfile.gettempdir()}/desktop-login-{port}.lock", "w") as f:
        fcntl.flock(f, fcntl.LOCK_EX); yield


if __name__ == "__main__":
    port, a = sys.argv[1], sys.argv[2:]
    k = a[0] == "keep"; c, a = (a[1], a[2:]) if k else (None, a)
    act = (lambda: logout(port)) if a[0] == "logout" else (lambda: token(port)) if a[0] == "token" else (lambda: login(port, a[0], a[1]))
    if k:
        try:
            keep(port, act, server_ready(c))
        except (OSError, Refused) as e:
            print(f"keep: devtools endpoint on {port} gone: {e}")
        sys.exit(0)
    try:
        with locked(port):
            r = act()
    except (OSError, Refused) as e:
        r = f"FAILED: devtools endpoint on {port}: {e}"
    print(r)
    sys.exit(0 if r in ("LOGGED_IN", "LOGIN_SCREEN") else 1)
