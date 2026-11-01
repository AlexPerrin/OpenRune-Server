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
    def test_up_runs_the_image_config_with_every_quest_requirement_checked(s):  # upstream's assume-completed let the range cook before Cook's Assistant
        src = (HERE.parent / "track.sh").read_text()
        fn = next(l for l in src.splitlines() if l.startswith("quests()"))
        yml = 'game:\n  pvp:\n    mode: "off"\n  quest-requirements:\n    # Bypass quest gates by default.\n    mode: "assume-completed"\n    virtual-lines: []\n  other:\n    mode: "on"\n'   # upstream game.example.yml's shape
        run = lambda text: subprocess.run(["bash", "-c", f"set -euo pipefail\n{fn}\nquests"], input=text, capture_output=True, text=True)
        r = run(yml); s.assertEqual(r.returncode, 0, r.stderr)
        s.assertEqual([l for l in r.stdout.splitlines() if l.strip().startswith("mode:")], ['    mode: "off"', '    mode: "respect-progress"', '    mode: "on"'])
        s.assertEqual(len(r.stdout.splitlines()), len(yml.splitlines()))
        s.assertNotEqual(run("game:\n  name: x\n").returncode, 0)
        up = src.split("\nup() {")[1].split("\n}")[0]
        s.assertIn('| quests > "$state/game.yml"', up); s.assertIn('-v "$state/game.yml:/app/game.yml:ro"', up)


class DesktopInstance(unittest.TestCase):
    """A fake Windows: each process is win/<pid>.jvm (its jps line) and win/<pid>.port (the port it listens on)."""
    def setUp(s):
        s.t = t = Path(tempfile.mkdtemp()); (t / "state").mkdir(); (t / "bin").mkdir(); (t / "sys32").mkdir(); (t / "win").mkdir()
        exe = lambda p, body: (p.write_text("#!/bin/bash\n" + body), p.chmod(0o755))
        exe(t / "sys32/netstat.exe", f'for f in {t}/win/*.port; do [ -e "$f" ] && echo "  TCP 127.0.0.1:$(cat $f) 0.0.0.0:0 LISTENING $(basename $f .port)"; done; true\n')
        exe(t / "sys32/taskkill.exe", f'echo "$4" >> {t}/killed; rm -f {t}/win/$4.*\n')
        exe(t / "bin/wslpath", 'sleep 0.5; echo "$2"\n')   # widens the window between picking an instance and listing it
        exe(t / "bin/docker", f'[ -e {t}/live-$4 ] && echo true || echo false\n')   # inspect -f <fmt> server-<slug>
        for i in range(1, 6):
            d = t / f"desk{'' if i == 1 else i}"; (d / "home/.rsprox").mkdir(parents=True); (d / "jdk/bin").mkdir(parents=True)
            exe(d / "jdk/bin/java.exe", f'echo "$$ OsrsTestLauncher $*" > {t}/win/$$.jvm; echo $((7779 + ${{1##*-}})) > {t}/win/$$.port; exec sleep 30\n')
            exe(d / "jdk/bin/jps.exe", f'cat {t}/win/*.jvm 2>/dev/null; true\n')
        src = (HERE.parent / "track.sh").read_text()
        s.fns = src[src.index("sys32="):src.index("\ntree() {")]
        s.env = {**os.environ, "PATH": f"{t / 'bin'}:{os.environ['PATH']}", "WINDOWS_SYSTEM32": str(t / "sys32"), "DESKTOP_DIR": str(t / "desk")}

    def tearDown(s):
        for f in s.t.glob("win/*.jvm"): subprocess.run(["kill", f.stem], capture_output=True)
        shutil.rmtree(s.t, ignore_errors=True)

    def proc(s, pid, cls, marker=None, port=None):
        (s.t / f"win/{pid}.jvm").write_text(f"{pid} {cls} -Duser.home=C:\\x" + (f" -Dosrstest.marker=osrs-test-{marker}" if marker else "") + "\n")
        if port: (s.t / f"win/{pid}.port").write_text(f"{port}\n")

    def up(s, slug, desktops, live=True):
        (s.t / "state" / slug).mkdir(); (s.t / "state" / slug / "up.json").write_text(json.dumps({"desktops": desktops}))
        if live: (s.t / f"live-server-{slug}").touch()

    def sh(s, slug, body):
        return subprocess.Popen(["bash", "-c", f"set -euo pipefail; root={s.t}; slug={slug}; http=1; game=2; modulus=3\n{s.fns}\n{body}"], stdout=subprocess.PIPE, text=True, env=s.env)

    def killed(s):
        return sorted((s.t / "killed").read_text().split()) if (s.t / "killed").exists() else []

    def test_concurrent_ups_take_distinct_instances(s):  # two ups once took the same desktop and one acted on the other's server
        start = 'desktop_start; mkdir -p "$root/state/$slug"; echo "{\\"desktops\\": \\"$inst:$dt:$pids\\"}" > "$root/state/$slug/up.json"; touch "$root/live-server-$slug"; echo $inst'
        ps = [s.sh(f"s{k}", start) for k in range(3)]
        got = [p.communicate(timeout=60)[0].split()[0] for p in ps]
        s.assertEqual(len(set(got)), 3, got); s.assertEqual(s.killed(), [])

    def test_a_desktop_no_live_run_lists_is_stopped_and_no_unmarked_jvm_is_touched(s):  # an orphan on desktop4 took a judge's login keys
        s.proc(100, "OsrsTestLauncher", 1, 7780); s.up("a", "desktop1:7780:100")     # a live run's desktop
        s.proc(200, "RuneLite", 2, 7781); s.up("gone", "desktop2:7781:200", live=False)   # its run's server is gone
        s.proc(500, "RuneLite", None, 7782)                                          # the user's own RuneLite on a harness port
        s.proc(400, "RuneLite", 4, 7783); s.proc(401, "Launcher", 4)                 # no up.json lists it
        out = s.sh("b", "desktop_start; echo $inst $dt").communicate(timeout=60)[0].split()
        s.assertEqual(s.killed(), ["200", "400", "401"])
        s.assertEqual(out, ["desktop2", "7781"])
        s.assertTrue((s.t / "win/500.jvm").exists()); s.assertTrue((s.t / "win/100.jvm").exists())

    def test_a_live_up_json_from_before_the_rename_still_owns_its_desktop(s):  # field "clients", written before "desktops"
        (s.t / "state/old").mkdir(); (s.t / "state/old/up.json").write_text(json.dumps({"clients": "desktop1:7780:100"})); (s.t / "live-server-old").touch()
        s.up("new", "desktop2:7781:200")
        s.assertEqual(sorted(s.sh("b", "owned").communicate(timeout=60)[0].split()), ["desktop1:7780:100", "desktop2:7781:200"])

    def test_an_instance_is_claimed_only_when_no_desktop_answers_on_it(s):  # whatever a pid file says
        for i in range(1, 6): s.up(f"r{i}", f"desktop{i}:{7779 + i}:{i}") if i != 3 else None
        s.proc(500, "RuneLite", None, 7782)                                          # not marked: never stopped, its port stays taken
        p = s.sh("b", "desktop_start"); p.communicate(timeout=60)
        s.assertNotEqual(p.returncode, 0); s.assertEqual(s.killed(), [])

    def test_stop_takes_the_recorded_pids_that_carry_the_marker(s):  # a PID reused by another program is never stopped
        s.proc(200, "OsrsTestLauncher", 2); s.proc(201, "RuneLite", 2, 7781); s.proc(999, "RuneLite", None, 7781)
        s.sh("b", "desktop_stop desktop2 200,201,999").communicate(timeout=60)
        s.assertEqual(s.killed(), ["200", "201"])
        s.proc(300, "Launcher", 3); s.proc(301, "RuneLite", 3); (s.t / "killed").unlink()
        s.sh("b", "desktop_stop desktop3").communicate(timeout=60)              # an up.json from before the PIDs were recorded
        s.assertEqual(s.killed(), ["300", "301"]); s.assertTrue((s.t / "win/999.jvm").exists())

    def test_up_records_each_desktop_before_its_login(s):  # a failed login aborts up under set -e; down must still stop that desktop
        up = (HERE.parent / "track.sh").read_text().split("\nup() {")[1].split("\n}")[0]
        s.assertLess(up.index('> "$state/up.json"'), up.index("flock -u 9"))
        s.assertLess(up.index('> "$state/up.json"'), up.index("desktop_login.py\" \"$dt\""))
        s.assertLess(up.index("down >/dev/null"), up.index("desktop_start"))


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
