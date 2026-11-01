"""The contract's script rows against a throwaway git repo: python3 -m unittest tools/server/test_tools.py"""
import json, os, shutil, subprocess, sys, tempfile, unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))


def git(repo, *a, env=None):
    return subprocess.run(["git", "-C", repo, *a], capture_output=True, text=True, env={**os.environ, **(env or {})}).stdout.strip()


def change(root, slug):
    p = Path(root) / "harness/changes" / f"{slug}.md"; p.parent.mkdir(parents=True, exist_ok=True); p.write_text("")


class Slug(unittest.TestCase):
    def test_a_pattern_in_the_slug_place_is_refused_before_any_state(s):
        root = Path(tempfile.mkdtemp())
        r = subprocess.run(["bash", str(HERE.parent / "track.sh"), "log", "Have a nice day"], capture_output=True, text=True, env={**os.environ, "HD_ROOT": str(root)})
        s.assertEqual(r.returncode, 2); s.assertIn('no change or goal "Have a nice day"', r.stderr)
        s.assertFalse((root / "state").exists())
        shutil.rmtree(root)


class NoPowerShell(unittest.TestCase):
    def test_windows_programs_run_from_bash_by_absolute_path(s):  # Windows refuses unsigned .ps1 from the UNC path; a lost PATH loses bare .exe names
        s.assertEqual(list(HERE.parent.rglob("*.ps1")), [])
        src = "\n".join(l for l in (HERE.parent / "track.sh").read_text().splitlines() if not l.lstrip().startswith("#"))
        s.assertNotIn("powershell", src.lower())
        import re
        s.assertEqual(re.findall(r'(?:^|[\s(|;&])[\w.-]+\.exe\b', src), [])


class CacheStore(unittest.TestCase):
    def test_a_store_hit_restores_every_output_of_the_cache_step(s):  # a hit without api/generated fails :api:music:compileKotlin
        line = next(l for l in (HERE.parent / "track.sh").read_text().splitlines() if "cp -a --parents" in l)
        for out in (".data/cache ", ".data/gamevals ", ".data/gamevals-binary ", "api/generated "):
            s.assertIn(out, line)


class Central(unittest.TestCase):
    def test_a_build_with_central_includes_that_central_at_its_commit(s):  # a server change needing a Central change built against the published dev.or2
        b = (HERE.parent / "track.sh").read_text().split("\nbuild() {")[1].split("\n}")[0]
        s.assertIn('read -r cpath ccommit <<< "$CENTRAL"', b)
        s.assertIn('worktree add -q --detach "$cdir" "$ccommit"', b)
        s.assertIn('-v "$cdir:$cdir"', b)
        s.assertIn("${CDIR:+ --init-script /central.init.gradle}", b)
        init = (HERE / "central.init.gradle").read_text()  # openrune-central is named unlike its project: substituted explicitly
        for m, p in (("openrune-central", "central-app"), ("central-common", "central-common"), ("central-worldlink", "central-worldlink")):
            s.assertIn(f"substitute module('dev.or2:{m}') using project(':{p}')", init)
        s.assertIn("CENTRAL=${CENTRAL:-$cen}", (HERE.parent / "track.sh").read_text())


class BuildLock(unittest.TestCase):
    def test_a_second_build_of_a_slug_waits_then_reuses_the_image(s):  # a dev's build was refused: the pass's build-<slug> container was running
        b = (HERE.parent / "track.sh").read_text().split("\nbuild() {")[1].split("\n}")[0]
        s.assertIn('exec 9> "$state/build.lock"; flock 9', b)
        s.assertLess(b.index("flock 9"), b.index('docker image inspect "or:$T"'))


class DesktopDevtools(unittest.TestCase):
    def test_each_desktop_loads_the_live_clients_devtools_jar(s):  # the client track's build, not a jar staged by hand
        t = (HERE.parent / "track.sh").read_text()
        f = t.split("\ndevtools_jar() {")[1].split("\n}")[0]
        s.assertIn('"$c" build live "$tc"', f); s.assertIn("-u T -u EVIDENCE", f)
        s.assertIn('jar=$root/evidence/live/$(git -C "$(dirname "$c")" rev-parse "$tc^{tree}").devtools-desktop.jar', f)
        s.assertIn("  devtools_jar", t.split("\nup() {")[1].split("\n}")[0])
        start = t.split("\ndesktop_start() {")[1].split("\n}")[0]
        s.assertLess(start.index('cp "$jar" "$d/home/.rlcustom/sideloaded-plugins/OpenRune-Developer-Tools.jar"'), start.index("exec setsid"))


class AccountName(unittest.TestCase):
    def test_each_login_name_fits_the_client_and_is_unique_per_slug_and_client(s):  # the client refuses a login name over 12 characters
        fn = next(l for l in (HERE.parent / "track.sh").read_text().splitlines() if l.startswith("account()"))
        name = lambda slug, i: subprocess.run(["bash", "-c", f'{fn}\nslug=$1; account $2', "-", slug, str(i)], capture_output=True, text=True).stdout.strip()
        names = {name(slug, i) for slug in ("harness-no-powershell", "harness-server-account-name-length") for i in (1, 5)}
        s.assertEqual(len(names), 4)
        for n in names: s.assertRegex(n, r"^[a-z][a-z0-9]{1,11}$")

    def test_without_an_index_it_is_the_prefix_of_every_name_under_set_u(s):  # up's members update calls account with no argument
        fn = next(l for l in (HERE.parent / "track.sh").read_text().splitlines() if l.startswith("account()"))
        r = subprocess.run(["bash", "-c", f'set -euo pipefail\n{fn}\nslug=s; echo "$(account) $(account 1) $(account 5)"'], capture_output=True, text=True)
        s.assertEqual(r.returncode, 0, r.stderr)
        prefix, *names = r.stdout.split()
        for n in names: s.assertEqual(n, prefix + n[len(prefix):]); s.assertEqual(len(n), len(prefix) + 1)


class QuestMode(unittest.TestCase):
    def test_up_runs_the_image_config_with_the_changes_quest_mode(s):  # upstream's assume-completed let the range cook before Cook's Assistant; a cannon needs it while main has no Dwarf Cannon quest
        src = (HERE.parent / "track.sh").read_text()
        fns = "\n".join(l for l in src.splitlines() if l.startswith(("quests()", "quest_mode()")) or l.startswith("  y=$(sed"))
        yml = 'game:\n  pvp:\n    mode: "off"\n  quest-requirements:\n    # Bypass quest gates by default.\n    mode: "assume-completed"\n    virtual-lines: []\n  other:\n    mode: "on"\n'   # upstream game.example.yml's shape
        root = Path(tempfile.mkdtemp()); s.addCleanup(shutil.rmtree, root); (root / "harness/changes").mkdir(parents=True)
        def run(text, front, env=""):
            (root / "harness/changes/x.md").write_text(f"---\nslug: x\n{front}---\nquest-mode: assume-completed in the body is no setting\n")
            return subprocess.run(["bash", "-c", f"set -euo pipefail\nroot={root}; slug=x\n{fns}\n{env} quests \"$({env} quest_mode)\""], input=text, capture_output=True, text=True,
                                  env={k: v for k, v in os.environ.items() if k != "QUEST_MODE"})
        for front, env, mode in [("", "", "respect-progress"), ("quest-mode: assume-completed\n", "", "assume-completed"), ("quest-mode: respect-progress\n", "", "respect-progress"),
                                 ("quest-mode: assume-completed\n", "QUEST_MODE=respect-progress", "respect-progress"), ("", "QUEST_MODE=assume-completed", "assume-completed")]:
            r = run(yml, front, env); s.assertEqual(r.returncode, 0, r.stderr)
            s.assertEqual([l for l in r.stdout.splitlines() if l.strip().startswith("mode:")], ['    mode: "off"', f'    mode: "{mode}"', '    mode: "on"'])
            s.assertEqual(len(r.stdout.splitlines()), len(yml.splitlines()))
        s.assertIn("respect-progress or assume-completed", run(yml, "quest-mode: off\n").stderr)
        s.assertNotEqual(run("game:\n  name: x\n", "").returncode, 0)
        up = src.split("\nup() {")[1].split("\n}")[0]
        s.assertIn('| quests "$(quest_mode)" > "$state/game.yml"', up); s.assertIn('-v "$state/game.yml:/app/game.yml:ro"', up)

    def test_restart_keeps_the_config_and_database_of_up(s):  # a hand-run container lost its game.yml mount at restart; a tmpfs database loses the accounts
        src = (HERE.parent / "track.sh").read_text()
        up = src.split("\nup() {")[1].split("\n}")[0]; restart = src.split("\nrestart() {")[1].split("\n}")[0]
        s.assertIn('-v "$state/postgres:/app/.data/postgres"', up); s.assertNotIn("--tmpfs", up)
        s.assertIn('docker restart "server-$slug"', restart); s.assertNotIn("docker run", restart); s.assertNotIn("> \"$state/game.yml\"", restart)


class Resume(unittest.TestCase):
    def test_down_saves_the_characters_and_resume_up_keeps_them(s):  # each up wiped the account: a long quest review replayed the quest every run
        src = (HERE.parent / "track.sh").read_text()
        fn = lambda name: f"{name}() {{" + src.split(f"\n{name}() {{")[1].split("\n}")[0] + "\n}"
        line = lambda pre: next(l for l in src.splitlines() if l.startswith(pre))
        root = Path(tempfile.mkdtemp()); s.addCleanup(shutil.rmtree, root); (root / "bin").mkdir(); (root / "st").mkdir()
        # a fake docker: the server is ready, its characters go offline one per count, and every call is logged
        (root / "bin/docker").write_text(f"""#!/bin/bash
echo "$*" >> {root}/calls
case $1 in inspect) echo true ;; logs) echo "Server ready in 1s" ;;
  run) n=$(cat {root}/online); echo $n; [ $n = 0 ] || echo $((n - 1)) > {root}/online ;; esac
""")
        (root / "bin/docker").chmod(0o755); (root / "online").write_text("2")
        script = "\n".join(["set -euo pipefail", f"slug=x; state={root}/st", line("account()"), line("mine="), fn("sql"), line("offline()"), fn("down"),
                            "desktop_stop() { :; }", "down"])
        r = subprocess.run(["bash", "-c", script], capture_output=True, text=True, env={**os.environ, "EVIDENCE": "", "PATH": f"{root}/bin:{os.environ['PATH']}"})
        s.assertEqual(r.returncode, 0, r.stderr)
        calls = (root / "calls").read_text().splitlines()
        s.assertEqual([c.split()[0] for c in calls if c.startswith(("run", "rm"))], ["run", "run", "run", "rm"])   # removed only once none is online
        wipe = line('  [ -n "${RESUME:-}" ] || rm -rf "$state/postgres"')
        for env, kept in (("", False), ("RESUME=1", True)):
            (root / "st/postgres").mkdir(exist_ok=True); (root / "st/postgres/PG_VERSION").write_text("14")
            subprocess.run(["bash", "-c", f"set -euo pipefail; state={root}/st; {env}\n{wipe}"], check=True)
            s.assertEqual((root / "st/postgres/PG_VERSION").exists(), kept)
        s.assertIn('-v "$state/postgres:/app/.data/postgres"', src.split("\nup() {")[1].split("\n}")[0])


class DesktopInstance(unittest.TestCase):
    """A fake Windows: each process is win/<pid>.jvm (its jps line) and win/<pid>.port (the port it listens on)."""
    def setUp(s):
        s.t = t = Path(tempfile.mkdtemp()); (t / "state").mkdir(); (t / "bin").mkdir(); (t / "sys32").mkdir(); (t / "win").mkdir()
        exe = lambda p, body: (p.write_text("#!/bin/bash\n" + body), p.chmod(0o755))
        exe(t / "sys32/netstat.exe", f'[ -e {t}/interop-fails ] && {{ rm {t}/interop-fails; echo "<3>WSL ERROR: UtilAcceptVsock:273: accept4 failed 110" >&2; exit 1; }}; for f in {t}/win/*.port; do [ -e "$f" ] && echo "  TCP 127.0.0.1:$(cat $f) 0.0.0.0:0 LISTENING $(basename $f .port)"; done; true\n')
        exe(t / "sys32/taskkill.exe", f'echo "$4" >> {t}/killed; [ -e {t}/win/$4.dir ] && chmod u+w "$(cat {t}/win/$4.dir)/held"; rm -f {t}/win/$4.*\n')
        exe(t / "sys32/robocopy.exe", 'cp -r "$1" "${2//\\\\//}"; exit 1\n')   # 1: files copied
        exe(t / "bin/wslpath", 'sleep 0.5; echo "$2"\n')   # widens the window between picking an instance and claiming it
        d = t / "desk"; (d / "home/.rsprox").mkdir(parents=True); (d / "home/.rlcustom/profiles2").mkdir(parents=True); (d / "jdk/bin").mkdir(parents=True)
        (d / "home/.rlcustom/settings.properties").write_text("a=1\n"); (d / "home/.rlcustom/profiles2/p.properties").write_text("openrunedevtools.port=7780\n")
        exe(d / "jdk/bin/java.exe", f'echo "$$ OsrsTestLauncher $*" > {t}/win/$$.jvm; echo $((7779 + ${{1##*run}})) > {t}/win/$$.port; pwd > {t}/win/$$.dir; mkdir held; touch held/f; chmod 555 held; exec sleep 30\n')   # a live JVM holds its files
        exe(d / "jdk/bin/jps.exe", f'for f in {t}/win/*.jvm; do [ -e "$f" ] || continue; if [ -e "${{f%.jvm}}.flaky" ]; then rm "${{f%.jvm}}.flaky"; echo "${{f##*/}} -- process information unavailable"; else cat "$f"; fi; done\n')
        (t / "devtools.jar").write_text("jar")
        src = (HERE.parent / "track.sh").read_text()
        s.fns = src[src.index("sys32="):src.index("\ntree() {")]
        s.env = {**os.environ, "PATH": f"{t / 'bin'}:{os.environ['PATH']}", "WINDOWS_SYSTEM32": str(t / "sys32"), "DESKTOP_DIR": str(d)}

    def tearDown(s):
        for f in s.t.glob("win/*.jvm"): subprocess.run(["kill", f.stem], capture_output=True)
        subprocess.run(["chmod", "-R", "u+w", s.t])
        shutil.rmtree(s.t, ignore_errors=True)

    def proc(s, pid, cls, marker=None, port=None):
        (s.t / f"win/{pid}.jvm").write_text(f"{pid} {cls} -Duser.home=C:\\x" + (f" -Dosrstest.marker=osrs-test-run{marker}" if marker else "") + "\n")
        if port: (s.t / f"win/{pid}.port").write_text(f"{port}\n")

    def sh(s, slug, body):
        return subprocess.Popen(["bash", "-c", f"set -euo pipefail; root={s.t}; slug={slug}; state={s.t}/state/{slug}; mkdir -p $state; http=1; game=2; modulus=3; jar={s.t}/devtools.jar\n{s.fns}\n{body}"], stdout=subprocess.PIPE, text=True, env=s.env)

    def killed(s):
        return sorted((s.t / "killed").read_text().split()) if (s.t / "killed").exists() else []

    def test_concurrent_ups_each_create_their_own_instances(s):  # five shared instances: concurrent ups raced and retried for two at once
        ps = [s.sh(f"s{k}", "desktop_start; echo $inst:$dt; desktop_start; echo $inst:$dt") for k in range(3)]
        got = [l for p in ps for l in p.communicate(timeout=60)[0].split()]
        s.assertEqual(len(set(got)), 6, got); s.assertEqual(s.killed(), [])
        for g in got:
            n, port = g[len("desktop"):].split(":")
            s.assertEqual((s.t / f"desk/run/{n}/home/.rlcustom/profiles2/p.properties").read_text(), f"openrunedevtools.port={port}\n")
            s.assertTrue((s.t / f"desk/run/{n}/home/.rlcustom/sideloaded-plugins/OpenRune-Developer-Tools.jar").exists())
        s.assertEqual(sorted((s.t / "desk/run" / g[len("desktop"):].split(":")[0] / "owner").read_text().strip() for g in got), ["s0", "s0", "s1", "s1", "s2", "s2"])

    def test_a_failed_windows_listing_is_asked_again_and_the_launcher_logs(s):  # a loaded host: netstat.exe through WSL interop failed or hung, up waited forever
        (s.t / "interop-fails").write_text("")
        p = s.sh("a", "desktop_start; echo $inst $dt"); out = p.communicate(timeout=60)[0].split()
        s.assertEqual((p.returncode, out), (0, ["desktop1", "7780"])); s.assertFalse((s.t / "interop-fails").exists())
        s.assertTrue((s.t / "state/a/desktop1.log").exists())   # the launcher's output, read when a desktop never opens its port
        s.assertIn('timeout 20 "$sys32/netstat.exe"', (HERE.parent / "track.sh").read_text())   # a hung listing ends and is asked again

    def test_a_port_another_program_holds_is_skipped_and_never_stopped(s):  # the user's own RuneLite on a harness port
        s.proc(500, "RuneLite", None, 7780)
        out = s.sh("b", "desktop_start; echo $inst $dt").communicate(timeout=60)[0].split()
        s.assertEqual(out, ["desktop2", "7781"]); s.assertEqual(s.killed(), []); s.assertFalse((s.t / "desk/run/1").exists())

    def test_down_stops_and_removes_exactly_its_own_instances(s):  # an orphan once took a judge's login keys
        for slug in ("a", "b", "a"): s.sh(slug, "desktop_start").communicate(timeout=60)
        s.proc(500, "RuneLite", None, 7790)
        mine = sorted(f.stem for f in s.t.glob("win/*.jvm") if any(f"osrs-test-run{n} " in f.read_text() for n in (1, 3)))
        s.sh("a", "desktops_remove").communicate(timeout=60)
        s.assertEqual(s.killed(), mine)
        s.assertEqual(sorted(p.name for p in (s.t / "desk/run").iterdir()), ["2"])
        s.assertTrue((s.t / "win/500.jvm").exists())

    def test_down_stops_a_jvm_jps_missed_before_it_removes_the_instance(s):  # quest-restlessghost: owner gone, three JVMs left holding run/4
        s.sh("a", "desktop_start").communicate(timeout=60)
        mine = sorted(f.stem for f in s.t.glob("win/*.jvm"))
        for p in mine: (s.t / f"win/{p}.flaky").write_text("")   # jps's first listing shows it as unavailable
        p = s.sh("a", "desktops_remove"); p.communicate(timeout=60)
        s.assertEqual(p.returncode, 0); s.assertEqual(s.killed(), mine); s.assertFalse((s.t / "desk/run/1").exists())

    def test_up_records_each_desktop_before_its_login(s):  # a failed login aborts up under set -e; restart and the tools read up.json
        up = (HERE.parent / "track.sh").read_text().split("\nup() {")[1].split("\n}")[0]
        s.assertLess(up.index('> "$state/up.json"'), up.index("desktop_login.py\" \"$dt\""))
        s.assertLess(up.index("down >/dev/null"), up.index("desktop_start"))


class DesktopLogin(unittest.TestCase):
    def fake(s, states):  # a devtools endpoint whose game state walks through states, one per read, staying on the last
        import desktop_login as d
        sent, states = [], list(states)
        def call(port, name, **a):
            sent.append((name, a))
            if name == "press_key" and len(a["key"]) > 1 and a["key"] not in ("ENTER", "ESCAPE", "SPACE", "TAB", "BACKSPACE"):
                raise d.Refused("Unknown key: " + a["key"])  # the endpoint's named keys
            return json.dumps({"gameState": states.pop(0) if len(states) > 1 else states[0]}) if name == "get_client_state" else "{}"
        s.addCleanup(setattr, d, "call", d.call); s.addCleanup(setattr, d.time, "sleep", d.time.sleep)
        d.call, d.time.sleep = call, lambda _: None
        return d, sent

    def test_login_screen_right_after_enter_is_not_a_result(s):  # a fresh account's first login read LOGIN_SCREEN before the attempt began
        d, _ = s.fake(["LOGIN_SCREEN"] * 4 + ["LOGGING_IN", "LOADING", "LOGGED_IN"])  # the wait, the two frame reads, then right after Enter
        s.assertEqual(d.login(1, "u", "p"), "LOGGED_IN")
        d, _ = s.fake(["LOGIN_SCREEN"] * 4 + ["LOGGING_IN", "LOGIN_SCREEN"])
        s.assertTrue(d.login(1, "u", "p").startswith("FAILED"))

    def test_a_refused_call_raises(s):  # press_key BACK_SPACE was refused as an unknown key and the login went on
        import desktop_login as d, io
        body = json.dumps({"result": {"isError": True, "content": [{"type": "text", "text": "Unknown key: BACK_SPACE"}]}}).encode()
        s.addCleanup(setattr, d.urllib.request, "urlopen", d.urllib.request.urlopen)
        d.urllib.request.urlopen = lambda *a, **k: io.BytesIO(body)
        with s.assertRaisesRegex(d.Refused, "Unknown key"):
            d.call(1, "press_key", key="BACK_SPACE")
        s.assertEqual(d.state(1), {})  # a refused state read is no state yet, read again

    def test_logout_uses_the_logout_button_not_a_command(s):  # the server has no ::drop; relog hung on it
        d, sent = s.fake(["LOGGED_IN", "LOGIN_SCREEN"])
        s.assertEqual(d.logout(1), "LOGIN_SCREEN")
        s.assertFalse([a for n, a in sent if n == "type_chat"])
        s.assertIn(("invoke_menu_action", {"param0": -1, "param1": d.LOGOUT_BUTTON, "type": "CC_OP", "identifier": 1, "option": "Logout"}), sent)

    def test_up_sets_members_while_its_characters_are_offline(s):  # the logout save wrote members back to false over an update made in game
        up = (HERE.parent / "track.sh").read_text().split("\nup() {")[1].split("\n}")[0]
        s.assertLess(up.index('desktop_login.py" "$(cut -d: -f2 <<< "$c")" logout'), up.index("  offline\n"))
        s.assertLess(up.index("  offline\n"), up.index("set members = true"))
        s.assertLess(up.index("set members = true"), up.rindex('desktop_login.py" "$(cut -d: -f2 <<< "${desktops[$i]}")"'))
        s.assertNotIn("::", (HERE / "desktop_login.py").read_text())


class Repo(unittest.TestCase):
    def setUp(s):
        s.repo = tempfile.mkdtemp()
        git(s.repo, "init", "-q", "-b", "main")
        git(s.repo, "config", "user.email", "t@t"); git(s.repo, "config", "user.name", "t")
        s.write(".data/gamevals-binary/max-ids.toml", "[max_ids]\nvarp = 100\narea = 10\n")
        s.write("content/a/gamevals.toml", "[gamevals.varp]\nquest_a = 101\n")
        s.write(".github/pull_request_template.md", "## Summary\n## Checklist\n")
        s.write("mod/src/main/kotlin/A.kt", "fun a() {}\n")
        s.commit("feat: base")
        git(s.repo, "update-ref", "refs/remotes/origin/main", "HEAD")
        s.base = git(s.repo, "rev-parse", "HEAD")
        s.env = {"CLONE": s.repo, "PINS": "x", "SLUG": "s1", "BASE": s.base}

    def tearDown(s):
        shutil.rmtree(s.repo, ignore_errors=True)

    def write(s, path, text):
        p = Path(s.repo) / path
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text)
        git(s.repo, "add", path)

    def commit(s, msg):
        git(s.repo, "commit", "-q", "-m", msg)
        return git(s.repo, "rev-parse", "HEAD")

    def row(s, script, *args, **env):
        r = subprocess.run([sys.executable, str(HERE / script), *args], capture_output=True, text=True, env={**os.environ, **s.env, **env})
        return r.returncode, r.stdout + r.stderr


class Gamevals(Repo):
    def test_clash_with_a_pin_and_assign(s):
        s.write("content/b/gamevals.toml", "[gamevals.varp]\nquest_b = 102\n")
        pin = s.commit("feat: pin takes 102")
        git(s.repo, "update-ref", "refs/remotes/fork/x", pin)
        git(s.repo, "reset", "-q", "--hard", s.base)
        s.write("content/c/gamevals.toml", "[gamevals.varp]\nquest_c = 102\nquest_d = -1\n")
        head = s.commit("feat: ours takes 102 too")
        rc, out = s.row("gamevals.py", "check", head)
        s.assertEqual(rc, 1); s.assertIn("varp.quest_c = 102 is varp.quest_b on x", out)
        rc, out = s.row("gamevals.py", "assign", s.repo)
        s.assertEqual(rc, 0); s.assertIn("varp.quest_d = 65535", out)
        s.assertIn("quest_d = 65535", (Path(s.repo) / "content/c/gamevals.toml").read_text())

    def test_missing_max_ids_fails_loudly(s):
        git(s.repo, "rm", "-q", ".data/gamevals-binary/max-ids.toml")
        head = s.commit("fix: no maxes")
        rc, out = s.row("gamevals.py", "check", head)
        s.assertEqual(rc, 1); s.assertIn("max-ids.toml missing", out)


class Text(Repo):
    def test_commits_texts_dupes_evidence(s):
        s.write("mod/src/main/kotlin/A.kt", "fun a() { invAdd(x) }\n")
        git(s.repo, "commit", "-q", "-m", "add a thing\n\nClaude-Session: abc\nCo-Authored-By: C <c@c>")
        head = git(s.repo, "rev-parse", "HEAD")
        ev = tempfile.mkdtemp()
        rc, out = s.row("text.py", "commits", HEAD=head)
        s.assertEqual(rc, 1); s.assertIn("not feat: or fix:", out); s.assertIn("Claude-Session trailer", out)
        rc, out = s.row("text.py", "dupes", HEAD=head)
        s.assertEqual(rc, 1); s.assertIn("item flow in mod/src/main/kotlin/A.kt: invAdd", out); s.assertIn("mod moves items with no test", out)
        rc, out = s.row("text.py", "evidence", EVIDENCE=ev)
        s.assertEqual(rc, 1); s.assertIn("accuracy.md missing", out)
        for n, t in (("accuracy.md", "spec"), ("pr-title.txt", "feat: x"), ("pr-body.md", "## Summary\nsee AlexPerrin/OpenRune-Server#9"), ("pr-comment.md", "Updated to " + head[:9])):
            (Path(ev) / n).write_text(t)
        rc, out = s.row("text.py", "texts", EVIDENCE=ev, HEAD=head)
        s.assertEqual(rc, 1); s.assertIn("lacks the template section 'Checklist'", out); s.assertIn("pr-body.md links a fork PR", out)
        (Path(ev) / "pr-body.md").write_text("## Summary\n## Checklist\n")
        rc, out = s.row("text.py", "texts", EVIDENCE=ev, HEAD=head)
        s.assertEqual((rc, out.strip()), (0, "texts: ok"))
        s.write("mod/src/test/kotlin/ATest.kt", "class ATest\n")
        head = s.commit("fix: test the flow")
        s.assertEqual(s.row("text.py", "dupes", HEAD=head)[0], 0)
        s.write("gen/src/main/kotlin/G.kt", "fun g() { invDel(x) }\n")
        s.write("gen/src/integration/kotlin/GTest.kt", "class GTest\n")
        head = s.commit("fix: an integration dupe test")
        s.assertEqual(s.row("text.py", "dupes", HEAD=head)[0], 0)
        shutil.rmtree(ev)

    def test_commits_after_an_existing_prs_head(s):
        s.write("mod/src/test/kotlin/T.kt", "x\n")
        public = s.commit("test: already public on the PR")
        git(s.repo, "update-ref", "refs/remotes/fork/content/x", public)
        s.write("mod/src/main/kotlin/A.kt", "fun a() { 1 }\n")
        head = s.commit("fix: the review finding")
        rc, out = s.row("text.py", "commits", HEAD=head)
        s.assertEqual(rc, 1); s.assertIn("already public", out)
        rc, out = s.row("text.py", "commits", HEAD=head, PR="9", PUBLIC_BRANCH="content/x")
        s.assertEqual(rc, 0, out)


class TestVerb(Repo):
    def test_runs_the_touched_modules_tests_and_spotless_with_the_copys_git_readable(s):  # a dev ran gradle by hand; spotless's JGit could not open the worktree
        s.write("mod/build.gradle.kts", "")
        s.write("other/build.gradle.kts", "")
        s.commit("feat: modules"); git(s.repo, "update-ref", "refs/remotes/origin/main", "HEAD")
        s.write("mod/src/main/kotlin/A.kt", "fun a() { 1 }\n")
        tc = s.commit("feat: touch mod")
        copy = Path(s.repo) / ".worktrees/s1-build"; git(s.repo, "worktree", "add", "-q", "--detach", str(copy), tc)
        root = Path(tempfile.mkdtemp()); s.addCleanup(shutil.rmtree, root); (root / "bin").mkdir(); (root / "st").mkdir()
        # a fake docker: the container's view of the copy's git (as spotless reads it), the gradle tasks, a test report
        (root / "bin/docker").write_text(f"""#!/bin/bash
for a in "$@"; do case $a in *:{copy}/.git:ro) gd=$(sed 's/^gitdir: //' "${{a%%:*}}") ;; esac; done
git --git-dir="$gd" rev-parse HEAD origin/main > {root}/git; git --git-dir="$gd" status --porcelain >> {root}/git
while [ "$1" != _ ]; do shift; done; shift; echo "$@" > {root}/tasks
mkdir -p {copy}/mod/build/test-results/test; echo '<testsuite name="ATest" tests="3" skipped="0" failures="0" errors="0" x>' > {copy}/mod/build/test-results/test/a.xml
""")
        (root / "bin/docker").chmod(0o755)
        src = (HERE.parent / "track.sh").read_text()
        fn = lambda name: f"{name}() {{" + src.split(f"\n{name}() {{")[1].split("\n}")[0] + "\n}"
        line = lambda pre: next(l for l in src.splitlines() if l.startswith(pre))
        script = "\n".join(["set -euo pipefail", f"slug=s1; repo={s.repo}; copy={copy}; state={root}/st; root={root}; T=t1; tc={tc}",
                            line("record()"), line("projects()"), line("projdir()"), "build() { :; }", fn("tests"), "tests"])
        r = subprocess.run(["bash", "-c", script], capture_output=True, text=True, env={**os.environ, "EVIDENCE": "", "PATH": f"{root}/bin:{os.environ['PATH']}"})
        s.assertEqual(r.returncode, 0, r.stderr + r.stdout)
        s.assertEqual((root / "tasks").read_text().split(), [":mod:test", ":mod:spotlessCheck"])
        s.assertEqual((root / "git").read_text().split(), [tc, git(s.repo, "rev-parse", "origin/main")])
        s.assertIn('<testsuite name="ATest" tests="3"', (root / "evidence/s1/test.log").read_text())
        s.assertTrue(json.loads((root / "st/test.json").read_text())["ok"])
        (copy / "other/build/test-results/test").mkdir(parents=True); (copy / "other/build/test-results/test/old.xml").write_text('<testsuite name="Old" tests="1" skipped="0" failures="1" errors="0">')
        r = subprocess.run(["bash", "-c", script + " :other"], capture_output=True, text=True, env={**os.environ, "EVIDENCE": "", "PATH": f"{root}/bin:{os.environ['PATH']}"})
        s.assertEqual(r.returncode, 0, r.stderr); s.assertEqual((root / "tasks").read_text().split(), [":other:test", ":other:spotlessCheck"])
        s.assertNotIn("Old", (root / "evidence/s1/test.log").read_text())   # a report this run did not write is never read

    def test_a_pack_directory_is_the_project_settings_names(s):  # quest-ernestthechicken: no module named ran :content:areas:city:draynor:pack:test, a project settings names draynor-pack
        fns = "\n".join(l for l in (HERE.parent / "track.sh").read_text().splitlines() if l.startswith(("projects()", "projdir()")))
        copy = tempfile.mkdtemp(); s.addCleanup(shutil.rmtree, copy)
        for d in ("content/quest", "content/areas/city/draynor", "content/areas/city/draynor/pack"):
            Path(copy, d).mkdir(parents=True); Path(copy, d, "build.gradle.kts").write_text("")
        files = "content/quest/src/main/kotlin/A.kt content/areas/city/draynor/src/test/kotlin/B.kt content/areas/city/draynor/pack/src/main/resources/c.toml README.md"
        r = subprocess.run(["bash", "-c", f'set -euo pipefail; copy={copy}\n{fns}\nprojects {files}; for p in $(projects {files}); do projdir $p; done'], capture_output=True, text=True)
        s.assertEqual(r.stdout.split(), [":content:areas:city:draynor", ":content:areas:city:draynor:draynor-pack", ":content:quest",
                                         "content/areas/city/draynor", "content/areas/city/draynor/pack", "content/quest"], r.stderr)


class Snapshot(Repo):
    def test_build_snapshot_keeps_tracked_files_under_ignored_paths(s):
        s.write(".gitignore", "generated/\n")
        p = Path(s.repo) / "generated/build.gradle.kts"; p.parent.mkdir(); p.write_text("x\n")
        git(s.repo, "add", "-f", "generated/build.gradle.kts"); s.commit("feat: a tracked file under an ignored path")
        git(s.repo, "worktree", "add", "-q", ".worktrees/s1")
        (Path(s.repo) / ".worktrees/s1/new.kt").write_text("y\n")
        root = tempfile.mkdtemp(); out = Path(root) / "ls"; change(root, "s1")
        (Path(root) / "hd.py").write_text(f"import subprocess,sys\nopen({str(out)!r},'w').write(subprocess.run(['git','-C',{s.repo!r},'ls-tree','-r','--name-only',sys.argv[3]],capture_output=True,text=True).stdout)\nsys.exit(1)\n")
        subprocess.run(["bash", str(HERE.parent / "track.sh"), "build", "s1"], capture_output=True, env={**os.environ, "CLONE": s.repo, "HD_ROOT": root, "T": ""})
        files = out.read_text().split()
        s.assertIn("generated/build.gradle.kts", files); s.assertIn("new.kt", files)
        shutil.rmtree(root)


class GateCi(Repo):
    def test_a_row_without_a_slug_pushes_gate_of_the_env_slug(s):
        fork = tempfile.mkdtemp(); git(fork, "init", "-q", "--bare"); git(s.repo, "remote", "add", "fork", fork)
        bin = Path(tempfile.mkdtemp()); (bin / "gh").write_text("#!/bin/sh\necho success/completed\n"); (bin / "gh").chmod(0o755)
        root = tempfile.mkdtemp(); change(root, "s1")
        r = subprocess.run(["bash", str(HERE.parent / "track.sh"), "gate-ci"], capture_output=True, text=True,
                           env={**os.environ, **s.env, "HD_ROOT": root, "TCOMMIT": s.base, "PATH": f"{bin}:{os.environ['PATH']}"})
        s.assertEqual(r.returncode, 0, r.stderr)
        s.assertEqual(git(fork, "rev-parse", "refs/heads/gate/s1"), s.base)
        s.assertTrue((Path(root) / "state/s1").is_dir())
        for d in (fork, bin, root): shutil.rmtree(d)


class Measure(unittest.TestCase):
    def test_measure_writes_under_the_goal_not_the_target(s):
        root = Path(tempfile.mkdtemp()); (root / "harness/goals").mkdir(parents=True)
        (root / "harness/goals/server-content.json").write_text("[]"); (root / "harness/goals/server-content.md").write_text("")
        r = subprocess.run(["bash", str(HERE.parent / "track.sh"), "measure", "server-content", "abc123"], capture_output=True, text=True, env={**os.environ, "HD_ROOT": str(root), "TREE": "t1"})
        s.assertEqual(r.returncode, 0, r.stderr)
        s.assertEqual(json.loads((root / "state/server-content/measure.json").read_text())["value"], 0.0)
        s.assertFalse((root / "state/abc123").exists())
        shutil.rmtree(root)

    def test_a_row_passes_by_its_unit_in_passes(s):
        root = Path(tempfile.mkdtemp()); (root / "harness/goals").mkdir(parents=True); (root / "harness/goals/server-content.md").write_text("")
        (root / "harness/goals/server-content.json").write_text(json.dumps([{"unit": "quest:a", "source": "x"}, {"unit": "quest:b", "source": "y"}]))
        r = subprocess.run(["bash", str(HERE.parent / "track.sh"), "measure", "server-content"], capture_output=True, text=True,
                           env={**os.environ, "HD_ROOT": str(root), "TREE": "t1", "PASSES": "quest:b skill:z"})
        s.assertEqual(r.returncode, 0, r.stderr)
        s.assertEqual(json.loads((root / "state/server-content/measure.json").read_text())["value"], 50.0)
        shutil.rmtree(root)


class JavConfig(unittest.TestCase):
    def test_template_names_local_hosts_only(s):
        import javcheck
        t = (HERE / "jav_config.ws.template").read_text(encoding="utf-8").replace("{{HTTP_BASE}}", "http://127.0.0.1:8080").replace("{{WORLD_HOST}}", "10.0.2.2")
        s.assertEqual(javcheck.foreign(t), [])
        s.assertEqual(javcheck.foreign('x=["https://legal.jagex.com/docs"T] param=19=1-a.apps.googleusercontent.com'), ["1-a.apps.googleusercontent.com", "legal.jagex.com"])


if __name__ == "__main__":
    unittest.main()


class RealmLikeLive(unittest.TestCase):  # Central seeds a dev realm: every login an administrator, unlike live
    t = (HERE.parent / "track.sh").read_text()
    up = t.split("\nup() {")[1].split("\n}")[0]
    fn = next(l for l in t.splitlines() if l.startswith("account()"))

    def test_up_turns_the_dev_realm_off_and_restarts_before_any_login(s):
        line = next(l for l in s.up.splitlines() if "dev_mode = 0" in l)
        s.assertLess(s.up.index(line), s.up.index("desktop_start"))
        call = lambda out: subprocess.run(["bash", "-c", f'set -euo pipefail\nsql() {{ echo "{out}"; }}\ndocker() {{ echo "docker $*" >&2; }}\nready() {{ echo "ready $*" >&2; }}\nslug=s\n{line}'], capture_output=True, text=True)
        r = call("1"); s.assertEqual(r.returncode, 0, r.stderr); s.assertIn("docker restart server-s", r.stderr); s.assertRegex(r.stderr, r"ready server-s \d")
        r = call(""); s.assertEqual(r.returncode, 0, r.stderr); s.assertEqual(r.stderr, "")   # already live (RESUME): no restart

    def test_up_sets_the_realm_xp_rates_to_live(s):  # the dev realm seeds player xp at 15000 hundredths: a reward shows 150x
        line = next(l for l in s.up.splitlines() if "dev_mode = 0" in l)
        for col in ("player_xp_rate_in_hundreds", "global_xp_rate_in_hundreds"):
            s.assertIn(f"{col} = 100", line); s.assertIn(f"{col} <> 100", line)   # set, and a 150x realm alone still restarts

    def test_every_account_is_a_player_unless_staff_names_its_desktop(s):  # STAFF=1's account once read rights '' and every cheat was refused
        line = "\n".join(l for l in s.up.splitlines() if l.startswith("  rights=") or "update accounts set rights" in l)
        s.assertLess(s.up.index("  offline"), s.up.index(line))
        login = s.up.rindex('desktop_login.py" "$(cut -d: -f2 <<< "${desktops[$i]}")"')
        s.assertLess(s.up.index(line), login); s.assertLess(login, s.up.index("rights <> $rights"))   # read back once the logins ran
        q = lambda staff: subprocess.run(["bash", "-c", f'set -euo pipefail\nsql() {{ echo "$1"; }}\n{s.fn}\nslug=s\n{line.replace(">/dev/null", "")}'],
                                         capture_output=True, text=True, env={**os.environ, **({"STAFF": staff} if staff else {})}).stdout
        name = lambda i: subprocess.run(["bash", "-c", f'{s.fn}\nslug=s; account {i}'], capture_output=True, text=True).stdout.strip()
        s.assertIn(f"lower(account_name) = '{name(0)}' then", q(None))   # no desktop is numbered 0
        s.assertIn(f"lower(account_name) = '{name(2)}' then 'ADMINISTRATOR' else ''", q("2"))
        s.assertIn(f"like '{name('')}%'", q("2"))


class KeyOnCachedBuild(unittest.TestCase):  # a cached or:<T> left state/<slug>/key empty: docker made the mount sources directories and the server exited at boot
    t = (HERE.parent / "track.sh").read_text()
    fn = "keys() {" + t.split("\nkeys() {")[1].split("\n}")[0] + "\n}"

    def test_up_makes_a_key_pair_before_the_server_runs_and_keeps_an_existing_one(s):
        up = s.t.split("\nup() {")[1].split("\n}")[0]
        s.assertLess(up.index("  keys "), up.index('docker run -d --name "server-$slug"'))
        s.assertNotIn("state/key", s.t.split("\nbuild() {")[1].split("\n}")[0])   # no key comes from a build: one source for every up
        st = Path(tempfile.mkdtemp()); s.addCleanup(shutil.rmtree, st)
        (st / "key/game.key").mkdir(parents=True); (st / "key/client.key").mkdir()   # what docker leaves after an up with no key
        run = lambda: subprocess.run(["bash", "-c", f"set -euo pipefail\nstate={st}\n{s.fn}\nkeys"], capture_output=True, text=True)
        r = run(); s.assertEqual(r.returncode, 0, r.stderr)
        game, client = (st / "key/game.key").read_text(), (st / "key/client.key").read_text()
        s.assertTrue(game.startswith("-----BEGIN PRIVATE KEY-----"))   # PKCS#8: the server's RsaProvider reads it
        mod = subprocess.run(["openssl", "rsa", "-in", str(st / "key/game.key"), "-noout", "-modulus"], capture_output=True, text=True).stdout.split("=")[1].strip().lower()
        s.assertEqual(client, f"Exponent: 10001\nModulus: {mod}")
        s.assertEqual(len(mod), 256)
        r = run(); s.assertEqual(r.returncode, 0, r.stderr); s.assertEqual((st / "key/game.key").read_text(), game)   # an existing key is kept
