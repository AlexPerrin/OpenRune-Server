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


class DesktopInstance(unittest.TestCase):
    def test_concurrent_ups_take_distinct_instances(s):  # two ups once took the same client and one acted on the other's server
        src = (HERE.parent / "track.sh").read_text()
        fns = src[src.index("sys32="):src.index("desktop_stop()")]
        t = Path(tempfile.mkdtemp()); (t / "state").mkdir(); (t / "bin").mkdir(); (t / "sys32").mkdir()
        exe = lambda p, body: (p.write_text("#!/bin/bash\n" + body), p.chmod(0o755))
        exe(t / "sys32/netstat.exe", f'for i in 1 2 3 4 5; do [ -e {t}/desk${{i#1}}/listening ] && echo "  TCP 0.0.0.0:$((7779 + i)) 0.0.0.0:0 LISTENING"; done; true\n')
        exe(t / "bin/wslpath", 'sleep 0.5; echo "$2"\n')   # widens the window between picking an instance and claiming it
        for i in range(1, 6):
            d = t / f"desk{'' if i == 1 else i}"; (d / "home/.rsprox").mkdir(parents=True); (d / "jdk/bin").mkdir(parents=True)
            exe(d / "jdk/bin/java.exe", "touch listening; exec sleep 30\n")
        env = {**os.environ, "PATH": f"{t / 'bin'}:{os.environ['PATH']}", "WINDOWS_SYSTEM32": str(t / "sys32"), "DESKTOP_DIR": str(t / "desk")}
        run = lambda: subprocess.Popen(["bash", "-c", f"set -euo pipefail; root={t}; slug=s; http=1; game=2; modulus=3\n{fns}\ndesktop_start", ], stdout=subprocess.PIPE, text=True, env=env)
        ps = [run() for _ in range(3)]
        got = [p.communicate(timeout=60)[0].split()[0] for p in ps]
        [subprocess.run(["kill", f.read_text().strip()]) for f in t.glob("desk*/rsprox.pid")]; shutil.rmtree(t, ignore_errors=True)
        s.assertEqual(len(set(got)), 3, got)

    def test_up_records_each_client_before_its_login(s):  # a failed login aborts up under set -e; down must still stop that client
        up = (HERE.parent / "track.sh").read_text().split("\nup() {")[1].split("\n}")[0]
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
