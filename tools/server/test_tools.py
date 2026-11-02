"""The contract's script rows against a throwaway git repo: python3 -m unittest tools/server/test_tools.py"""
import json, os, shutil, subprocess, sys, tempfile, threading, unittest
from pathlib import Path
SUITE_TMP = tempfile.TemporaryDirectory(prefix="tools-suite-", ignore_cleanup_errors=True)   # every temp dir of these tests and of each process they
tempfile.tempdir = os.environ["TMPDIR"] = SUITE_TMP.name                          # start (mktemp) is under it, removed at the end, a failed test's too

for k in ("SLUG", "HEAD", "T", "G", "TCOMMIT", "BASE", "EVIDENCE", "BUILD", "CLONE", "TRACK", "PUBLIC_BRANCH", "PR", "PINS", "HD_ROOT", "CENTRAL", "TOOLS", "NAMES", "ROWS", "TOOLS_ROOT", "TOOLS_SRC"):
    os.environ.pop(k, None)   # hd's env of the gate that runs these tests is not the tests': a T it set named every build log
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
SRC = "\n".join((HERE.parent / f).read_text() for f in ("track.sh", "build/build.sh", "gate/gate.sh"))   # the track script and the code a build and a gate run


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



class Log(unittest.TestCase):
    def setUp(s):
        s.root = Path(tempfile.mkdtemp()); change(s.root, "x"); (s.root / "bin").mkdir()

    def tearDown(s):
        shutil.rmtree(s.root)

    def run_log(s, docker, pattern):  # track.sh log against a fake docker on PATH, one state dir across calls
        (s.root / "bin/docker").write_text("#!/bin/sh\n" + docker); (s.root / "bin/docker").chmod(0o755)
        return subprocess.run(["bash", str(HERE.parent / "track.sh"), "log", "x", pattern], capture_output=True, text=True,
                              env={**os.environ, "HD_ROOT": str(s.root), "PATH": f"{s.root / 'bin'}:{os.environ['PATH']}"})

    def test_every_search_reads_the_running_servers_lines(s):  # a '.' search after an ERROR search printed nothing: a marker advanced by each call
        docker = 'case $1 in inspect) exit 0;; logs) [ "$2" = --since ] && exit 0; printf "12:00 [INFO] - Server ready in 8s\\n12:01 [WARN] - slow tick\\n";; esac\n'
        for pattern in ("ERROR|WARN", ".", "."):
            r = s.run_log(docker, pattern); s.assertEqual(r.returncode, 0, r.stderr)
            s.assertIn("slow tick", r.stdout)
        s.assertIn("Server ready", s.run_log(docker, ".").stdout)

    def test_no_container_is_an_error_not_a_clean_log(s):
        r = s.run_log("exit 1\n", "ERROR")
        s.assertEqual(r.returncode, 1); s.assertIn("no container server-x", r.stderr)

class Transcribe(unittest.TestCase):
    def test_every_filter_is_on_and_recordings_finds_the_packet(s):  # RSProx's default filters hid clan packets: recordings CLANSETTINGS found nothing
        import gzip
        root = Path(tempfile.mkdtemp()); change(root, "x"); fake = root / "fake"; fake.mkdir()
        for d in ("txt", "bin"): (root / "rsprox" / d).mkdir(parents=True)
        (root / "rsprox/bin/7.bin").write_bytes(gzip.compress(b"rec"))
        (fake / "javap").write_text("#!/bin/sh\nprintf '  public static final net.rsprox.shared.filters.PropertyFilter CLANSETTINGS_FULL;\\n  public static final net.rsprox.shared.filters.PropertyFilter IF_BUTTON;\\n'\n")
        # the fake transcriber writes its input and the active filter set: the transcript holds a packet only when its filter is true
        (fake / "java").write_text('#!/bin/sh\nh=${1#-Duser.home=}; b=$h/.rsprox/binary; { cat "$b/7.bin"; echo; cat "$h"/.rsprox/filters/*; grep -q "CLANSETTINGS_FULL=true" "$h"/.rsprox/filters/* && echo "clansettings_full"; } > "$b/7.txt"\n')
        for f in ("javap", "java"): (fake / f).chmod(0o755)
        env = {**os.environ, "HD_ROOT": str(root), "RSPROX": str(root / "rsprox/txt"), "PATH": f"{fake}:{os.environ['PATH']}"}
        r = subprocess.run(["bash", str(HERE.parent / "track.sh"), "transcribe", "x"], capture_output=True, text=True, env=env)
        s.assertEqual(r.returncode, 0, r.stderr)
        t = (root / "rsprox/txt/7.txt").read_text()
        s.assertEqual(t, "rec\nversion=0\ncreationtime=1\nname=All\nactive=true\nCLANSETTINGS_FULL=true\nIF_BUTTON=true\nclansettings_full\n")
        r = subprocess.run(["bash", str(HERE.parent / "track.sh"), "recordings", "x", "CLANSETTINGS"], capture_output=True, text=True, env=env)
        s.assertEqual(r.stdout, "7.txt\n")
        shutil.rmtree(root)


class NoPowerShell(unittest.TestCase):
    def test_windows_programs_run_from_bash_by_absolute_path(s):  # Windows refuses unsigned .ps1 from the UNC path; a lost PATH loses bare .exe names
        s.assertEqual(list(HERE.parent.rglob("*.ps1")), [])
        src = "\n".join(l for l in SRC.splitlines() if not l.lstrip().startswith("#"))
        s.assertNotIn("powershell", src.lower())
        import re
        s.assertEqual(re.findall(r'(?:^|[\s(|;&])[\w.-]+\.exe\b', src), [])


class CacheStore(unittest.TestCase):
    def test_a_store_hit_restores_every_output_of_the_cache_step(s):  # a hit without api/generated fails :api:music:compileKotlin
        line = next(l for l in SRC.splitlines() if "cp -a --parents" in l)
        for out in (".data/cache ", ".data/gamevals ", ".data/gamevals-binary ", "api/generated "):
            s.assertIn(out, line)


class Central(unittest.TestCase):
    def test_build_and_test_include_that_central_at_its_commit(s):  # a server change needing a Central change built, then tested, against the published dev.or2
        src = SRC
        c = src.split("\ncentral() {")[1].split("\n}")[0]
        s.assertIn('read -r cpath ccommit <<< "$CENTRAL"', c)
        s.assertIn('worktree add -q --detach "$cdir" "$ccommit"', c)
        s.assertIn('-v "$cdir:$cdir"', c)
        for verb in ("build", "gradle_check"):
            b = src.split(f"\n{verb}() {{")[1].split("\n}")[0]
            for want in ("central;", '"${cvols[@]}"', 'CDIR="$cdir"', "${CDIR:+ --init-script /central.init.gradle}"):
                s.assertIn(want, b, verb)
        init = (HERE.parent / "build/central.init.gradle").read_text()  # openrune-central is named unlike its project: substituted explicitly
        for m, p in (("openrune-central", "central-app"), ("central-common", "central-common"), ("central-worldlink", "central-worldlink")):
            s.assertIn(f"substitute module('dev.or2:{m}') using project(':{p}')", init)
        s.assertIn("CENTRAL=${CENTRAL:-$cen}", SRC)


class SidecarData(unittest.TestCase):
    def test_compose_gets_the_sidecar_dir_the_script_resolves(s):  # ship's compose mounted ":/data:ro": SIDECAR_DATA was a shell variable only
        line = next(l for l in SRC.splitlines() if "SIDECAR_DATA:-" in l)
        env = {k: v for k, v in os.environ.items() if k != "SIDECAR_DATA"}
        for extra, want in (({"HOME": "/h"}, "/h/src/openrune-work/server-tools/sidecar/data"), ({"SIDECAR_DATA": "/d"}, "/d")):
            r = subprocess.run(["bash", "-c", line + '\nbash -c \'echo "$SIDECAR_DATA $0"\' "$sidecar_data"'], capture_output=True, text=True, env={**env, **extra})
            s.assertEqual(r.stdout.split(), [want, want])


class BuildLock(unittest.TestCase):
    def test_a_second_build_of_a_slug_waits_then_reuses_the_image(s):  # a dev's build was refused: the pass's build-<slug> container was running
        b = SRC.split("\nbuild() {")[1].split("\n}")[0]
        s.assertIn('{ flock 9; build_locked; } 9> "$state/build.lock"', b)
        s.assertTrue(SRC.split("\nbuild_locked() {")[1].lstrip().startswith('if docker image inspect "or:$T"'))

    def test_the_lock_ends_with_the_build(s):  # a gate's 20 minutes of gate-ci held it: rekeyed builds and a dev's build waited behind it
        d = tempfile.mkdtemp(); line = next(l for l in SRC.splitlines() if l.startswith("build() {"))
        r = subprocess.run(["bash", "-c", f"set -e; state={d}; tree() {{ :; }}; build_locked() {{ flock -n {d}/build.lock true && exit 3; :; }}\n{line}\nbuild; flock -n {d}/build.lock echo free"],
                           capture_output=True, text=True)
        s.assertEqual((r.returncode, r.stdout.strip()), (0, "free"))   # held during build_locked, free once build returns, the shell still alive
        shutil.rmtree(d)


class BuildAttempts(unittest.TestCase):
    def setUp(s):
        s.root = Path(tempfile.mkdtemp()); s.addCleanup(shutil.rmtree, s.root); change(s.root, "x"); (s.root / "bin").mkdir()
        s.repo = s.root / "repo"; s.repo.mkdir(); git(s.repo, "init", "-q"); (s.repo / "game.example.yml").write_text("game: {}\n"); (s.repo / "settings.gradle.kts").write_text(""); (s.repo / "or-cache").mkdir(); (s.repo / "or-cache/x").write_text("")
        git(s.repo, "add", "-A"); git(s.repo, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "t"); s.tc = git(s.repo, "rev-parse", "HEAD")
        # docker: no image cached, the Gradle container fails
        (s.root / "bin/docker").write_text('#!/bin/sh\ncase "$*" in "image inspect"*) exit 1;; *"--name build-x"*) echo "gradle: boom $$"; exit 1;; esac\n'); (s.root / "bin/docker").chmod(0o755)

    def build(s):
        return subprocess.run(["bash", str(HERE.parent / "track.sh"), "build", "x", s.tc], capture_output=True, text=True,
                              env={**os.environ, "HD_ROOT": str(s.root), "CLONE": str(s.repo), "PATH": f"{s.root / 'bin'}:{os.environ['PATH']}", "CENTRAL": ""})

    def test_two_attempts_keep_two_logs_each_named_in_the_output(s):  # a deploy's retry overwrote the first attempt's log: its failure was gone
        outs = [s.build(), s.build()]
        T = git(s.repo, "rev-parse", f"{s.tc}^{{tree}}"); logs = [s.root / f"state/x/build-{T}-{n}.log" for n in (1, 2)]
        for r, log in zip(outs, logs):
            s.assertNotEqual(r.returncode, 0); s.assertIn(f"build failed: {log}", r.stdout)
            s.assertIn("gradle: boom", log.read_text())
        s.assertNotEqual(logs[0].read_text(), logs[1].read_text())

    def test_a_run_reads_its_own_copy_of_the_tools(s):  # the tools checkout moved under a running deploy: its Dockerfile vanished
        s.build()
        copies = [d for d in (s.root / "state/tools").iterdir() if not d.name.startswith(".")]
        s.assertEqual(len(copies), 1); s.assertTrue((copies[0] / "tools/build/Dockerfile").exists())
        s.build(); s.assertEqual(len(list((s.root / "state/tools").iterdir())), 1)   # one copy per content


class ToolsShipUnderARun(unittest.TestCase):
    def test_a_tools_ship_never_changes_a_running_builds_tools(s):  # a deploy's image build lost its Dockerfile to a tools ship mid-run
        root = Path(tempfile.mkdtemp()); s.addCleanup(shutil.rmtree, root); change(root, "x"); (root / "bin").mkdir()
        repo = root / "repo"; repo.mkdir(); git(repo, "init", "-q"); (repo / "game.example.yml").write_text(""); (repo / "settings.gradle.kts").write_text(""); (repo / "or-cache").mkdir(); (repo / "or-cache/x").write_text("")
        git(repo, "add", "-A"); git(repo, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "t"); tc = git(repo, "rev-parse", "HEAD")
        tools = root / "checkout/tools"; shutil.copytree(HERE.parent, tools, ignore=shutil.ignore_patterns("__pycache__"))   # the tools branch's checkout
        go = root / "go"; os.mkfifo(go)
        # docker: the Gradle step blocks on the fifo and leaves the install; the image build needs its -f file
        (root / "bin/docker").write_text(f'''#!/bin/bash
case "$*" in "image inspect"*) exit 1;;
  *"--name build-x"*) while [ "$1" != -w ]; do shift; done; mkdir -p "$2/server/app/build/install/app" "$2/.data"; read _ < {go}; exit 0;;
  build*) while [ "$1" != -f ]; do shift; done; [ -f "$2" ] || {{ echo "open $2: no such file"; exit 1; }};;
esac\n'''); (root / "bin/docker").chmod(0o755)
        p = subprocess.Popen(["bash", str(tools / "track.sh"), "build", "x", tc], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                             env={**os.environ, "HD_ROOT": str(root), "CLONE": str(repo), "PATH": f"{root / 'bin'}:{os.environ['PATH']}", "CENTRAL": ""})
        threading.Thread(target=lambda: (p.wait(), os.close(os.open(go, os.O_RDONLY | os.O_NONBLOCK))), daemon=True).start()   # a build that ends early never holds the fifo open
        with open(go, "w") as f:   # opens once the build waits in Gradle: the ship moves the checkout now
            shutil.rmtree(tools / "build"); f.write("\n")
        out = p.communicate()[0]
        s.assertEqual(p.returncode, 0, out); s.assertIn("built or:", out)


class CacheDownload(unittest.TestCase):
    def test_the_download_is_kept_from_the_jvms_home(s):  # the cache tool writes to user.home (the passwd home), so ~ with HOME set kept nothing: every new cache input downloaded 213 MB
        g = SRC
        s.assertNotIn("~/.openrune", g)
        s.assertEqual(g.count('oc=$(getent passwd "$(id -u)" | cut -d: -f6)/.openrune/caches'), 2)
        s.assertIn('for z in $oc/*/*/*-disk.zip', g)


class ShipFailure(unittest.TestCase):
    def test_a_failing_step_names_itself(s):
        f = SRC.split("\nship() {")[1].split("\n}")[0]
        r = subprocess.run(["bash", "-c", "set -euo pipefail\nship() {" + f.split("\n")[0] + "\n" + f.split("\n")[1] + "\n  false\n}\narg=t; ship"], capture_output=True, text=True)
        s.assertNotEqual(r.returncode, 0); s.assertIn("ship of t failed: false (exit 1)", r.stdout)


class DesktopDevtools(unittest.TestCase):
    def test_each_desktop_loads_the_live_clients_devtools_jar(s):  # the client track's build, not a jar staged by hand
        t = SRC
        f = t.split("\ndevtools_jar() {")[1].split("\n}")[0]
        s.assertIn('"$c" build live "$tc"', f); s.assertIn("-u T -u EVIDENCE", f)
        s.assertIn('jar=$root/evidence/live/$(git -C "$(dirname "$c")" rev-parse "$tc^{tree}").devtools-desktop.jar', f)
        s.assertIn("  devtools_jar", t.split("\nup() {")[1].split("\n}")[0])
        start = t.split("\ndesktop_start() {")[1].split("\n}")[0]
        s.assertLess(start.index('cp "$jar" "$d/home/.rlcustom/sideloaded-plugins/OpenRune-Developer-Tools.jar"'), start.index("exec setsid"))


class AccountName(unittest.TestCase):
    def test_each_login_name_fits_the_client_and_is_unique_per_slug_and_client(s):  # the client refuses a login name over 12 characters
        fn = next(l for l in SRC.splitlines() if l.startswith("account()"))
        name = lambda slug, i: subprocess.run(["bash", "-c", f'{fn}\nslug=$1; account $2', "-", slug, str(i)], capture_output=True, text=True).stdout.strip()
        names = {name(slug, i) for slug in ("harness-no-powershell", "harness-server-account-name-length") for i in (1, 5)}
        s.assertEqual(len(names), 4)
        for n in names: s.assertRegex(n, r"^[a-z][a-z0-9]{1,11}$")

    def test_without_an_index_it_is_the_prefix_of_every_name_under_set_u(s):  # up's members update calls account with no argument
        fn = next(l for l in SRC.splitlines() if l.startswith("account()"))
        r = subprocess.run(["bash", "-c", f'set -euo pipefail\n{fn}\nslug=s; echo "$(account) $(account 1) $(account 5)"'], capture_output=True, text=True)
        s.assertEqual(r.returncode, 0, r.stderr)
        prefix, *names = r.stdout.split()
        for n in names: s.assertEqual(n, prefix + n[len(prefix):]); s.assertEqual(len(n), len(prefix) + 1)


class QuestMode(unittest.TestCase):
    def test_up_runs_the_image_config_with_the_changes_quest_mode(s):  # upstream's assume-completed let the range cook before Cook's Assistant; a cannon needs it while main has no Dwarf Cannon quest
        src = SRC
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
        src = SRC
        up = src.split("\nup() {")[1].split("\n}")[0]; restart = src.split("\nrestart() {")[1].split("\n}")[0]
        s.assertIn('-v "$state/postgres:/app/.data/postgres"', up); s.assertNotIn("--tmpfs", up)
        s.assertIn('docker restart "server-$slug"', restart); s.assertNotIn("docker run", restart); s.assertNotIn("> \"$state/game.yml\"", restart)


class Resume(unittest.TestCase):
    def test_down_saves_the_characters_and_resume_up_keeps_them(s):  # each up wiped the account: a long quest review replayed the quest every run
        src = SRC
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
        exe(t / "sys32/netsh.exe", 'printf "Start Port    End Port\\r\\n----------    --------\\r\\n     50000       50059     *\\r\\n"\n')
        exe(t / "sys32/robocopy.exe", 'cp -r "$1" "${2//\\\\//}"; exit 1\n')   # 1: files copied
        exe(t / "bin/wslpath", 'sleep 0.5; echo "$2"\n')   # widens the window between picking an instance and claiming it
        d = t / "desk"; (d / "home/.rsprox").mkdir(parents=True); (d / "home/.rlcustom/profiles2").mkdir(parents=True); (d / "jdk/bin").mkdir(parents=True)
        (d / "home/.rlcustom/settings.properties").write_text("a=1\n"); (d / "home/.rlcustom/profiles2/p.properties").write_text("openrunedevtools.port=7780\n")
        exe(d / "jdk/bin/java.exe", f'echo "$$ OsrsTestLauncher $*" > {t}/win/$$.jvm; echo $((7779 + ${{1##*run}})) > {t}/win/$$.port; pwd > {t}/win/$$.dir; mkdir held; touch held/f; chmod 555 held; exec sleep 30\n')   # a live JVM holds its files
        exe(d / "jdk/bin/javac.exe", f'echo "$*" >> {t}/javac; touch "${{5//\\\\//}}/OsrsTestLauncher.class"\n')
        exe(d / "jdk/bin/jps.exe", f'for f in {t}/win/*.jvm; do [ -e "$f" ] || continue; if [ -e "${{f%.jvm}}.flaky" ]; then rm "${{f%.jvm}}.flaky"; echo "${{f##*/}} -- process information unavailable"; else cat "$f"; fi; done\n')
        (t / "devtools.jar").write_text("jar")
        src = SRC
        s.fns = src[src.index("sys32="):src.index("\nkeys() {")]
        s.env = {**os.environ, "PATH": f"{t / 'bin'}:{os.environ['PATH']}", "WINDOWS_SYSTEM32": str(t / "sys32"), "DESKTOP_DIR": str(d)}

    def tearDown(s):
        for f in s.t.glob("win/*.jvm"): subprocess.run(["kill", f.stem], capture_output=True)
        subprocess.run(["chmod", "-R", "u+w", s.t])
        shutil.rmtree(s.t, ignore_errors=True)

    def proc(s, pid, cls, marker=None, port=None):
        (s.t / f"win/{pid}.jvm").write_text(f"{pid} {cls} -Duser.home=C:\\x" + (f" -Dosrstest.marker=osrs-test-run{marker}" if marker else "") + "\n")
        if port: (s.t / f"win/{pid}.port").write_text(f"{port}\n")

    def sh(s, slug, body, **kw):
        return subprocess.Popen(["bash", "-c", f"set -euo pipefail; root={s.t}; slug={slug}; self={HERE.parent}; state={s.t}/state/{slug}; mkdir -p $state; http=1; game=2; modulus=3; jar={s.t}/devtools.jar\n{s.fns}\n{body}"], stdout=subprocess.PIPE, text=True, env=s.env, **kw)

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
        s.assertIn('timeout 20 "$sys32/netstat.exe"', SRC)   # a hung listing ends and is asked again

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

    @unittest.skipUnless(shutil.which("systemd-run") and subprocess.run(["systemctl", "--user", "is-system-running"], capture_output=True).stdout.strip() in (b"running", b"degraded"), "no systemd user manager")
    def test_each_desktop_jvm_is_recorded_under_the_runs_slice_until_down(s):  # osrs-harness R5: a run's end stops its Linux processes; a Windows JVM outlives them
        sl = f"hd-t{os.getpid()}.slice"
        p = subprocess.Popen(["systemd-run", "--user", "--scope", "-q", f"--slice={sl}", "bash", "-c", f"set -euo pipefail; root={s.t}; slug=a; self={HERE.parent}; state={s.t}/state/a; mkdir -p $state; http=1; game=2; modulus=3; jar={s.t}/devtools.jar\n{s.fns}\ndesktop_start"], env=s.env)
        p.wait(timeout=60)
        mine = sorted(f.stem for f in s.t.glob("win/*.jvm"))
        s.assertEqual((p.returncode, sorted(f.name for f in (s.t / "state/winpids" / sl).iterdir())), (0, mine))
        s.assertEqual((s.t / "state/winpids" / sl / mine[0]).read_text(), "java.exe\n")
        s.sh("a", "desktops_remove").communicate(timeout=60)
        s.assertEqual(list((s.t / "state/winpids" / sl).iterdir()), [])   # down ended them: nothing left for hd to stop
        subprocess.run(["systemctl", "--user", "stop", sl])

    def test_down_stops_a_jvm_jps_missed_before_it_removes_the_instance(s):  # quest-restlessghost: owner gone, three JVMs left holding run/4
        s.sh("a", "desktop_start").communicate(timeout=60)
        mine = sorted(f.stem for f in s.t.glob("win/*.jvm"))
        for p in mine: (s.t / f"win/{p}.flaky").write_text("")   # jps's first listing shows it as unavailable
        p = s.sh("a", "desktops_remove"); p.communicate(timeout=60)
        s.assertEqual(p.returncode, 0); s.assertEqual(s.killed(), mine); s.assertFalse((s.t / "desk/run/1").exists())

    def test_rsprox_ports_skip_every_range_windows_refuses_a_bind_on(s):  # mirrored WSL reserved the boot's Linux ephemeral range 40525-44620 on Windows: every launcher failed to bind 43600
        base = lambda ranges: subprocess.run(["bash", "-c", s.fns + "\nskip_base"], input=ranges, capture_output=True, text=True, env=s.env)
        s.assertEqual(base("32768 40000\n50000 50059\n").stdout, "0\n")
        s.assertEqual(base("40525 44620\n44621 44700\n50000 50059\n").stdout, f"{44701 - 43600}\n")   # past the first range, then past the next
        s.assertEqual(base("40525 65535\n").returncode, 1)
        p = s.sh("a", "desktop_start; echo $inst $dt"); out = p.communicate(timeout=60)[0].split()
        lo, hi = map(int, Path("/proc/sys/net/ipv4/ip_local_port_range").read_text().split())
        skip = int(next(s.t.glob("win/*.jvm")).read_text().split("-Dosrstest.portskip=")[1].split()[0])
        s.assertEqual(out, ["desktop1", "7780"])
        s.assertTrue(all(q < lo or q > hi for q in (43600 + skip, 43701 + skip)), (skip, lo, hi))
        s.assertTrue(all(q < 50000 or q > 50059 for q in range(43600 + skip, 43752 + skip)))
        s.assertIn("OsrsTestLauncher.java", (s.t / "javac").read_text())   # the tools tree's launcher, which takes any skip
        s.assertNotIn("skip > 50", (HERE / "OsrsTestLauncher.java").read_text())

    def test_a_launcher_whose_start_throws_fails_up_with_its_exception(s):  # RSProx's netty threads kept the failed launcher alive and up waited forever
        (s.t / "desk/jdk/bin/java.exe").write_text("#!/bin/bash\necho 'Exception in thread \"main\" java.net.BindException: Address already in use: bind'; echo '[osrs-test] REFUSING: launcher failed'; exit 2\n")
        p = s.sh("a", "desktop_start", stderr=subprocess.PIPE)
        err = p.communicate(timeout=60)[1]
        s.assertEqual(p.returncode, 1); s.assertIn("BindException", err)
        src = (HERE / "OsrsTestLauncher.java").read_text()
        s.assertIn("catch (Throwable e)", src); s.assertIn('die("launcher failed: " + e)', src)   # the launcher exits on any start exception

    def test_the_desktop_takes_jav_config_from_openrune_endpoint_alone(s):  # the user's word: one variable points every Jagex endpoint at our server
        java = s.t / "desk/jdk/bin/java.exe"
        java.write_text(java.read_text().replace("#!/bin/bash\n", f'#!/bin/bash\necho "$OPENRUNE_ENDPOINT $WSLENV" > {s.t}/env; ', 1))   # before its port: desktop_start returns once the port answers
        p = s.sh("a", "desktop_start"); p.communicate(timeout=60)
        s.assertEqual(p.returncode, 0)
        ep, wslenv = (s.t / "env").read_text().split()
        s.assertEqual(ep, "http://127.0.0.1:1"); s.assertIn("OPENRUNE_ENDPOINT", wslenv.split(":"))
        s.assertIn(f"  jav_config_url: {ep}/jav_config.ws\n", (s.t / "desk/run/1/home/.rsprox/proxy-targets.yaml").read_text())
        src = (HERE / "OsrsTestLauncher.java").read_text()
        s.assertIn('System.getenv("OPENRUNE_ENDPOINT")', src); s.assertIn('getJavConfigUrl().equals(endpoint + "/jav_config.ws")', src)

    def test_up_records_each_desktop_before_its_login(s):  # a failed login aborts up under set -e; restart and the tools read up.json
        up = SRC.split("\nup() {")[1].split("\n}")[0]
        s.assertLess(up.index('| atomic "$state/up.json"'), up.index('login "$dt"'))
        s.assertLess(up.index("down >/dev/null"), up.index("desktop_start"))


class DesktopLogin(unittest.TestCase):
    def fake(s, states, widgets=([{"childIndex": 0, "itemId": 6512, "itemQuantity": 1, "selfHidden": True}],), inv=None):  # a devtools endpoint whose game state walks through states, one per read, staying on the last; get_widget of the inventory walks through widgets likewise; inv: container 93's items (None: not loaded)
        import desktop_login as d
        sent, states, widgets = [], list(states), list(widgets)
        def call(port, name, **a):
            sent.append((name, a))
            if name == "press_key" and len(a["key"]) > 1 and a["key"] not in ("ENTER", "ESCAPE", "SPACE", "TAB", "BACKSPACE"):
                raise d.Refused("Unknown key: " + a["key"])  # the endpoint's named keys
            if name == "get_widget":
                w = widgets.pop(0) if len(widgets) > 1 else widgets[0]
                if w is None:
                    raise d.Refused("Widget not found: 149.0")
                return json.dumps({"dynamicChildren": w})
            if name == "get_inventory":
                if inv is None:
                    raise d.Refused("Item container 93 is not loaded")
                return json.dumps({"items": inv})
            return json.dumps({"gameState": states.pop(0) if len(states) > 1 else states[0]}) if name == "get_client_state" else "{}"
        s.addCleanup(setattr, d, "call", d.call); s.addCleanup(setattr, d.time, "sleep", d.time.sleep)
        d.call, d.time.sleep = call, lambda _: None
        return d, sent

    def test_login_screen_right_after_enter_is_not_a_result(s):  # a fresh account's first login read LOGIN_SCREEN before the attempt began
        d, _ = s.fake(["LOGIN_SCREEN"] * 4 + ["LOGGING_IN", "LOADING", "LOGGED_IN"])  # the wait, the two frame reads, then right after Enter
        s.assertEqual(d.login(1, "u", "p"), "LOGGED_IN")
        d, _ = s.fake(["LOGIN_SCREEN"] * 4 + ["LOGGING_IN", "LOGIN_SCREEN"])
        s.assertTrue(d.login(1, "u", "p").startswith("FAILED"))

    def test_logged_in_only_once_the_inventory_widget_shows_container_93(s):  # cannon-354-return-after-destroy-3: after up the widget stayed empty over a full container 93
        coins = [{"childIndex": 0, "itemId": 995, "itemQuantity": 5, "selfHidden": False}, {"childIndex": 1, "itemId": 6512, "itemQuantity": 1, "selfHidden": True}]
        held = [{"slot": 0, "itemId": 995, "quantity": 5}]
        d, sent = s.fake(["LOGGED_IN"], widgets=[None, [], coins], inv=held)   # the widget is built after LOGGED_IN
        s.assertEqual(d.login(1, "u", "p"), "LOGGED_IN")
        s.assertEqual(sum(n == "get_widget" for n, _ in sent), 3)
        d, _ = s.fake(["LOGGED_IN"])   # a fresh account: no container, every slot empty
        s.assertEqual(d.token(1), "LOGGED_IN")
        d, _ = s.fake(["LOGGED_IN"], inv=held)
        s.assertEqual(d.login(1, "u", "p"), "FAILED: the inventory widget shows {} but container 93 holds {0: (995, 5)}")
        d, _ = s.fake(["LOGGED_IN"], widgets=[[dict(coins[0], selfHidden=False, hidden=True)]], inv=held)   # another side tab open hides the slots through their parent alone
        s.assertEqual(d.login(1, "u", "p"), "LOGGED_IN")

    def test_a_refused_call_raises(s):  # press_key BACK_SPACE was refused as an unknown key and the login went on
        import desktop_login as d, io
        body = json.dumps({"result": {"isError": True, "content": [{"type": "text", "text": "Unknown key: BACK_SPACE"}]}}).encode()
        s.addCleanup(setattr, d.urllib.request, "urlopen", d.urllib.request.urlopen)
        d.urllib.request.urlopen = lambda *a, **k: io.BytesIO(body)
        with s.assertRaisesRegex(d.Refused, "Unknown key"):
            d.call(1, "press_key", key="BACK_SPACE")
        s.assertEqual(d.state(1), {})  # a refused state read is no state yet, read again

    def test_a_key_the_starting_client_did_not_take_is_resent_once_it_answers(s):  # one RESUME=1 up: 'devtools endpoint on 7780: <urlopen error timed out>'
        import desktop_login as d, io
        sent, slow = [], iter([True, True, True])   # the press_key, then two state reads, time out connecting
        def urlopen(req, timeout):
            name = json.loads(req.data)["params"]["name"]; sent.append(name)
            if next(slow, False):
                raise d.urllib.error.URLError(TimeoutError("timed out"))
            return io.BytesIO(json.dumps({"result": {"content": [{"type": "text", "text": '{"gameState": "LOGIN_SCREEN"}'}]}}).encode())
        s.addCleanup(setattr, d.urllib.request, "urlopen", d.urllib.request.urlopen); s.addCleanup(setattr, d.time, "sleep", d.time.sleep)
        d.urllib.request.urlopen, d.time.sleep = urlopen, lambda _: None
        d.call(1, "press_key", key="ESCAPE")
        s.assertEqual(sent, ["press_key", "get_client_state", "get_client_state", "get_client_state", "press_key"])   # resent only after a state read answered
        d.urllib.request.urlopen = lambda *a, **k: (_ for _ in ()).throw(d.urllib.error.URLError(ConnectionRefusedError()))
        with s.assertRaises(d.urllib.error.URLError):   # a refused connection (the desktop gone) is no start: it fails
            d.call(1, "press_key", key="ESCAPE")

    def test_logout_uses_the_logout_button_not_a_command(s):  # the server has no ::drop; relog hung on it
        d, sent = s.fake(["LOGGED_IN", "LOGIN_SCREEN"])
        s.assertEqual(d.logout(1), "LOGIN_SCREEN")
        s.assertFalse([a for n, a in sent if n == "type_chat"])
        s.assertIn(("invoke_menu_action", {"param0": -1, "param1": d.LOGOUT_BUTTON, "type": "CC_OP", "identifier": 1, "option": "Logout"}), sent)

    def test_up_sets_members_while_its_characters_are_offline(s):  # the logout save wrote members back to false over an update made in game
        up = SRC.split("\nup() {")[1].split("\n}")[0]
        s.assertLess(up.index('desktop_login.py" "$(cut -d: -f2 <<< "$c")" logout'), up.index("  offline\n"))
        s.assertLess(up.index("  offline\n"), up.index("set members = true"))
        s.assertLess(up.index("set members = true"), up.rindex('login "$(cut -d: -f2 <<< "${desktops[$i]}")"'))
        s.assertNotIn("::", (HERE / "desktop_login.py").read_text())

    def test_a_login_that_made_another_account_logs_in_again(s):  # quest-restlessghost-9: a lost key made a stray first account, the rights update missed the real one and the relogin made it with rights ''
        t = SRC; fn = t[t.index("login() {"):t.index("offline() {")]
        for site in ('login "$dt"', 'login "$(cut -d: -f2 <<< "${desktops[$i]}")"', 'login "$(cut -d: -f2 <<< "${ds[$i]}")"'):
            s.assertIn(site, t)   # every login of up and restart
        s.assertEqual(t.count("desktop_login.py\" \"$1\" \"$2\""), 1)   # no login but through it
        made = lambda *rows: subprocess.run(["bash", "-c", f'set -euo pipefail\nf=$(mktemp); trap "rm -f $f" EXIT; printf "%s\\n" {" ".join(repr(r) for r in rows)} > $f\nsql() {{ case $1 in *max*) echo 0 ;; *) head -1 $f; sed -i 1d $f ;; esac; }}   # each read in its own subshell: the rows a file\npython3() {{ echo "py $3"; }}\nself=.\n{fn}\nlogin 7786 h1'],
                                            capture_output=True, text=True).stdout
        s.assertEqual(made("h1"), "py h1\n")
        s.assertEqual(made("h1x h1", "h1"), "py h1\nlogin on 7786 made another account than h1: logging in again\npy logout\npy h1\n")
        s.assertEqual(made("h", "h1"), "py h1\nlogin on 7786 made another account than h1: logging in again\npy logout\npy h1\n")   # no row of the account yet

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
        (Path(ev) / "pr-body.md").write_text(""); (Path(ev) / "pr-comment.md").write_text("> why?\n\n@rev because.")   # a reply on an open PR
        s.assertEqual(s.row("text.py", "texts", EVIDENCE=ev, HEAD=head, PR="7")[0], 0)
        s.assertEqual(s.row("text.py", "evidence", EVIDENCE=ev, PR="7")[0], 0)
        s.assertEqual(s.row("text.py", "evidence", EVIDENCE=ev)[0], 1)
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
        git(s.repo, "checkout", "-q", "-b", "side", public); s.write("mod/src/main/kotlin/B.kt", "x\n"); main = s.commit("fix: main moved")
        git(s.repo, "checkout", "-q", "-"); git(s.repo, "merge", "-q", "--no-edit", main)   # the track's `merge main in`: no subject of its own
        rc, out = s.row("text.py", "commits", HEAD=git(s.repo, "rev-parse", "HEAD"), PR="9", PUBLIC_BRANCH="content/x")
        s.assertEqual(rc, 0, out)

    def test_dupes_after_an_existing_prs_head(s):  # cannon-354: the dev ref at #354's live head failed on the whole PR's item flows
        s.write("mod/src/main/kotlin/A.kt", "fun a() { invAdd(x) }\n")
        public = s.commit("feat: already public on the PR")
        git(s.repo, "update-ref", "refs/remotes/fork/content/x", public)
        s.assertEqual(s.row("text.py", "dupes", HEAD=public)[0], 1)
        s.assertEqual(s.row("text.py", "dupes", HEAD=public, PR="9", PUBLIC_BRANCH="content/x"), (0, "dupes: ok\n"))
        s.write("mod/src/main/kotlin/A.kt", "fun a() { invAdd(x) }\nfun b() { invDel(y) }\n")
        head = s.commit("fix: the review finding")
        rc, out = s.row("text.py", "dupes", HEAD=head, PR="9", PUBLIC_BRANCH="content/x")
        s.assertEqual(rc, 1); s.assertIn("item flow in mod/src/main/kotlin/A.kt: invDel\n", out)


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
case " $* " in *" chown "*) exit 0 ;; esac
for a in "$@"; do case $a in *:{copy}/.git:ro) gd=$(sed 's/^gitdir: //' "${{a%%:*}}") ;; esac; done
git --git-dir="$gd" rev-parse HEAD origin/main > {root}/git; git --git-dir="$gd" status --porcelain >> {root}/git
while [ "$1" != _ ]; do shift; done; shift; echo "$@" > {root}/tasks
mkdir -p {copy}/mod/build/test-results/test; echo '<testsuite name="ATest" tests="3" skipped="0" failures="0" errors="0" x>' > {copy}/mod/build/test-results/test/a.xml
""")
        (root / "bin/docker").chmod(0o755)
        src = SRC
        fn = lambda name: f"{name}() {{" + src.split(f"\n{name}() {{")[1].split("\n}")[0] + "\n}"
        line = lambda pre: next(l for l in src.splitlines() if l.startswith(pre))
        script = "\n".join(["set -euo pipefail", f"slug=s1; repo={s.repo}; copy={copy}; state={root}/st; root={root}; T=t1; tc={tc}",
                            line("atomic()"), line("record()"), line("projects()"), line("projdir()"), "build() { :; }", fn("central"), fn("gradle_check"), line("tests()"), "tests"])
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

    def test_the_spotless_row_fails_on_old_lint_in_a_touched_file(s):  # server-npc-examine-without-overlay failed CI Formatting three times: the ratchet saw a touched file's old lint
        s.write("mod/build.gradle.kts", ""); s.write("mod/src/main/kotlin/B.kt", "fun  b()  {}\n")   # old lint, on main before the change
        s.write("other/build.gradle.kts", "")
        s.commit("feat: modules"); git(s.repo, "update-ref", "refs/remotes/origin/main", "HEAD")
        s.write("mod/src/main/kotlin/B.kt", "fun  b()  {}\nfun c() {}\n")
        tc = s.commit("feat: touch B")
        copy = Path(s.repo) / ".worktrees/s1-build"
        root = Path(tempfile.mkdtemp()); s.addCleanup(shutil.rmtree, root); (root / "bin").mkdir(); (root / "st").mkdir()
        # a fake gradle in docker: spotlessCheck of a module whose file holds the double space fails, as the ratchet does on a touched file;
        # the slug was never built: its volume is docker's new root-owned one until the verb chowns it to the run's user
        (root / "bin/docker").write_text(f"""#!/bin/bash
case " $* " in *" -v gradle-s1:/gradle "*" chown 1000:1000 /gradle "*) touch {root}/owned; exit 0 ;; esac
[ -e {root}/owned ] || {{ echo "/gradle: Permission denied"; exit 1; }}
while [ "$1" != _ ]; do shift; done; shift; echo "$@" > {root}/tasks
for t; do m=${{t#:}}; m=${{m%:spotlessCheck}}; ! grep -rq 'fun  ' {copy}/$m/src 2>/dev/null || {{ echo "> Task :$m:spotlessCheck FAILED: format violations"; exit 1; }}; done
""")
        (root / "bin/docker").chmod(0o755)
        src = SRC
        fn = lambda name: f"{name}() {{" + src.split(f"\n{name}() {{")[1].split("\n}")[0] + "\n}"
        line = lambda pre: next(l for l in src.splitlines() if l.startswith(pre))
        script = "\n".join(["set -euo pipefail", f"slug=s1; repo={s.repo}; copy={copy}; state={root}/st; root={root}; T=t1",
                            line("atomic()"), line("record()"), line("projects()"), line("projdir()"), "build() { echo build >&2; exit 9; }",
                            f"tree() {{ tc={tc}; }}", fn("central"), fn("gradle_check"), fn("spotless"), "spotless"])
        run = lambda: subprocess.run(["bash", "-c", script], capture_output=True, text=True, env={**os.environ, "EVIDENCE": "", "PATH": f"{root}/bin:{os.environ['PATH']}"})
        r = run()
        s.assertEqual(r.returncode, 1, r.stderr + r.stdout)
        s.assertEqual((root / "tasks").read_text().split(), [":mod:spotlessCheck"])   # no test task, only the touched module
        s.assertIn("violations", (root / "evidence/s1/spotless.log").read_text())
        s.assertFalse(json.loads((root / "st/spotless.json").read_text())["ok"])
        tree = git(s.repo, "rev-parse", f"{tc}^{{tree}}")   # a tree for TCOMMIT (as a gate's T once was): the row fails, never "no module touched"
        r = subprocess.run(["bash", "-c", script.replace(tc, tree)], capture_output=True, text=True, env={**os.environ, "EVIDENCE": "", "PATH": f"{root}/bin:{os.environ['PATH']}"})
        s.assertNotEqual(r.returncode, 0); s.assertNotIn("no module touched", r.stdout)
        s.write("mod/src/main/kotlin/B.kt", "fun b() {}\nfun c() {}\n"); tc2 = s.commit("fix: the lint"); script = script.replace(tc, tc2)
        r = run()
        s.assertEqual(r.returncode, 0, r.stderr + r.stdout)
        s.assertTrue(json.loads((root / "st/spotless.json").read_text())["ok"])

    def test_a_pack_directory_is_the_project_settings_names(s):  # quest-ernestthechicken: no module named ran :content:areas:city:draynor:pack:test, a project settings names draynor-pack
        fns = "\n".join(l for l in SRC.splitlines() if l.startswith(("projects()", "projdir()")))
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
    WF = "jobs:\n  b:\n    steps:\n      - uses: actions/checkout@v7\n      - name: Set up Gradle\n        uses: gradle/actions/setup-gradle@v6\n      - run: ./gradlew test\n"
    RUNS = staticmethod(lambda st: f'HTTP/2.0 200 OK\nEtag: W/"{st or "running"}"\n\n' + json.dumps({"workflow_runs": [{"name": w, "status": "completed" if st else "in_progress", "conclusion": st or None} for w in ("CI", "Formatting")]}))
    GREEN = [RUNS("success")]

    @staticmethod
    def fake_gh(bin, answers):  # `gh api` prints answers[n] at its n-th call (the last repeats), each call's args in bin/calls; sleep is free
        for i, a in enumerate(answers): (bin / f"a{i}").write_text(a)
        (bin / "gh").write_text(f'#!/bin/sh\nd=$(dirname "$0"); echo "$*" >> "$d/calls"; case "$1 $2" in "release view") cat "$d/assets" 2>/dev/null; exit 0 ;; esac; [ "$1" = api ] || exit 0\ncase "$2" in */jobs) cat "$d/step"; exit 0 ;; esac\n'
                                f'n=$(grep -c "^api" "$d/calls"); [ $n -gt {len(answers)} ] && n={len(answers)}; cat "$d/a$((n-1))"; case $(head -1 "$d/a$((n-1))") in *" 200 "*) ;; *) exit 1 ;; esac\n')
        (bin / "sleep").write_text('#!/bin/sh\necho "$1" >> "$(dirname "$0")/sleeps"\n')
        for f in ("gh", "sleep"): (bin / f).chmod(0o755)

    def gate(s, answers, step="", env={}):  # gate-ci of s1 against a fake GitHub (step: a failed run's failed step): (result, gh calls, sleeps)
        for f in ("ci", "format"): s.write(f".github/workflows/{f}.yml", s.WF)
        t = s.commit("ci")
        fork = tempfile.mkdtemp(); git(fork, "init", "-q", "--bare"); git(s.repo, "remote", "add", "fork", fork)
        bin = Path(tempfile.mkdtemp()); s.fake_gh(bin, answers); (bin / "step").write_text(step + "\n")
        root = tempfile.mkdtemp(); change(root, "s1")
        r = subprocess.run(["bash", str(HERE.parent / "track.sh"), "gate-ci"], capture_output=True, text=True,
                           env={**os.environ, **s.env, "HD_ROOT": root, "TCOMMIT": t, "PATH": f"{bin}:{os.environ['PATH']}", **env})
        out = [(bin / f).read_text().splitlines() if (bin / f).exists() else [] for f in ("calls", "sleeps")]
        for d in (fork, bin, root): shutil.rmtree(d)
        return r, *out

    def test_status_is_one_conditional_read_free_while_unchanged(s):  # 30 s polls of gh run list per workflow per gate drained the 5000/h quota
        r, calls, _ = s.gate([s.RUNS(None), "HTTP/2.0 304 Not Modified\n\n", "HTTP/2.0 304 Not Modified\n\n", s.RUNS("success")])
        s.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        api = [c for c in calls if c.startswith("api")]
        s.assertEqual(len(api), 4); s.assertTrue(all("actions/runs?branch=gate/s1&head_sha=" in c for c in api))
        s.assertNotIn("If-None-Match", api[0]); s.assertTrue(all('If-None-Match: W/"running"' in c for c in api[1:]), api)  # a 304 keeps the ETag
        s.assertFalse([c for c in calls if c.startswith(("run list", "workflow run"))])  # runs were listed: nothing dispatched

    def test_a_quota_refusal_holds_the_row_until_githubs_reset(s):  # genie-355-bank-lamp's gate failed 'HTTP 403: API rate limit exceeded'
        r, calls, sleeps = s.gate(['HTTP/2.0 403 Forbidden\nX-Ratelimit-Remaining: 0\nX-Ratelimit-Reset: 9999999999\n\n{"message":"API rate limit exceeded"}', s.RUNS("success")])
        s.assertEqual(r.returncode, 0, r.stdout + r.stderr); s.assertIn("held until its reset", r.stdout)
        s.assertGreater(int(sleeps[0]), 10 ** 9)

    def test_a_failed_run_fails_the_row_and_no_run_dispatches_each_workflow_once(s):
        r, calls, _ = s.gate(['HTTP/2.0 200 OK\nEtag: "0"\n\n{"workflow_runs": []}', 'HTTP/2.0 304 Not Modified\n\n', s.RUNS("failure")])
        s.assertEqual(r.returncode, 1); s.assertIn("CI failure/completed", r.stdout)
        s.assertEqual(sorted(c.split()[2] for c in calls if c.startswith("workflow run")), ["CI", "Formatting"])

    FAILED = staticmethod(lambda att, c="failure": f'HTTP/2.0 200 OK\nEtag: W/"f{att}"\n\n' + json.dumps({"workflow_runs": [
        {"name": "CI", "status": "completed", "conclusion": c, "id": 7, "run_attempt": att}, {"name": "Formatting", "status": "completed", "conclusion": "success", "id": 8}]}))

    def test_a_run_failed_outside_the_head_is_re_run_not_the_heads_fail(s):  # GitHub's checkout failed, not the head: CI runs 41357682052, 41357793032
        r, calls, _ = s.gate([s.FAILED(1), s.FAILED(1), s.RUNS("success")], step="Checkout repository")
        s.assertEqual(r.returncode, 0, r.stdout + r.stderr); s.assertIn("a step the head does not touch: re-run", r.stdout)
        s.assertEqual([c for c in calls if c.startswith("run rerun")], ["run rerun 7 --failed -R " + calls[0].split("repos/")[1].split("/actions")[0]])  # once per attempt

    def test_a_run_stopped_in_the_cache_step_is_re_run_not_the_heads_fail(s):  # ci.yml's 30-min timeout cancelled the openrs2 download: CI runs 41364734674, 41362166155
        r, calls, _ = s.gate([s.FAILED(1, "cancelled"), s.FAILED(1, "cancelled"), s.RUNS("success")], step="Cache game data")
        s.assertEqual(r.returncode, 0, r.stdout + r.stderr); s.assertEqual(len([c for c in calls if c.startswith("run rerun 7")]), 1)

    def test_a_cache_step_failure_is_the_heads_when_the_head_changes_its_input(s):
        base = git(s.repo, "rev-parse", "HEAD"); s.write("or-cache/x", "y"); head = s.commit("touches the cache")
        r, calls, _ = s.gate([s.FAILED(1)], step="Generate cache and gameval sources", env={"BASE": base, "HEAD": head})
        s.assertEqual(r.returncode, 1); s.assertIn("CI failure/completed at 'Generate cache and gameval sources'", r.stdout)
        s.assertFalse([c for c in calls if c.startswith("run rerun")])

    def test_a_third_attempt_failed_outside_the_head_holds_with_no_result(s):
        r, calls, _ = s.gate([s.FAILED(3)], step="Set up Gradle")
        s.assertEqual(r.returncode, 75, r.stdout + r.stderr); s.assertIn("no result", r.stdout)

    CI = ("jobs:\n  b:\n    steps:\n      - name: Set up Gradle\n        uses: gradle/actions/setup-gradle@v6\n\n      - name: Cache game data\n        id: gamecache\n        uses: actions/cache@v6\n\n"
          "      - name: Generate cache and gameval sources\n        if: steps.gamecache.outputs.cache-hit != 'true'\n        run: ./gradlew :or-cache:freshCache :or-cache:mergePluginGamevals\n\n"
          "      - name: Rebuild sources from cached game data\n        if: steps.gamecache.outputs.cache-hit == 'true'\n        run: ./gradlew :or-cache:buildCache\n")

    def test_ci_reads_the_game_data_from_the_fork_release_seeded_once_from_the_image(s):  # openrs2 answered HTTP 500 on :or-cache:freshCache: a head's FAIL
        s.write(".github/workflows/ci.yml", s.CI); s.write(".github/workflows/format.yml", s.WF); s.write("game.example.yml", "revision: 241\n"); t = s.commit("ci")
        k = git(s.repo, "rev-parse", f"{t}:game.example.yml")[:12]
        fork = tempfile.mkdtemp(); git(fork, "init", "-q", "--bare"); git(s.repo, "remote", "add", "fork", fork)
        bin = Path(tempfile.mkdtemp()); s.fake_gh(bin, s.GREEN); root = tempfile.mkdtemp(); change(root, "s1")
        (bin / "docker").write_text('#!/bin/sh\necho "$*" >> "$(dirname "$0")/docker.calls"; [ "$1" = run ] && echo tarball; exit 0\n'); (bin / "docker").chmod(0o755)
        gate = lambda: subprocess.run(["bash", str(HERE.parent / "track.sh"), "gate-ci"], capture_output=True, text=True,
                                      env={**os.environ, **s.env, "HD_ROOT": root, "TCOMMIT": t, "T": "tk", "PATH": f"{bin}:{os.environ['PATH']}"})
        r = gate(); s.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        calls = (bin / "calls").read_text()
        s.assertIn(f"release upload game-data {root}/state/s1/game-data-{k}.tar.gz", calls); s.assertIn("run --rm --entrypoint tar or:tk", (bin / "docker.calls").read_text())
        y = git(fork, "show", "gate/s1:.github/workflows/ci.yml")
        s.assertLess(y.index(f"-p game-data-{k}.tar.gz"), y.index("Generate cache and gameval sources"))
        s.assertIn("cache-hit != 'true' && steps.gamedata.outcome != 'success'\n        run: ./gradlew :or-cache:freshCache", y)   # openrs2 only when neither holds it
        s.assertIn("cache-hit == 'true' || steps.gamedata.outcome == 'success'", y)
        (bin / "assets").write_text(f"game-data-{k}.tar.gz\n"); (bin / "calls").unlink(); gate()
        s.assertNotIn("release upload", (bin / "calls").read_text())                 # seeded once per game.example.yml
        for d in (fork, bin, root): shutil.rmtree(d)

    def test_a_row_without_a_slug_pushes_gate_of_the_env_slug(s):  # its CI moves the runner's ephemeral ports above 43594 before gradle: run 41312919759's boot bind failed
        for f in ("ci", "format"): s.write(f".github/workflows/{f}.yml", s.WF)
        t = s.commit("ci")
        fork = tempfile.mkdtemp(); git(fork, "init", "-q", "--bare"); git(s.repo, "remote", "add", "fork", fork)
        bin = Path(tempfile.mkdtemp()); s.fake_gh(bin, s.GREEN)
        root = tempfile.mkdtemp(); change(root, "s1")
        r = subprocess.run(["bash", str(HERE.parent / "track.sh"), "gate-ci"], capture_output=True, text=True,
                           env={**os.environ, **s.env, "HD_ROOT": root, "TCOMMIT": t, "PATH": f"{bin}:{os.environ['PATH']}"})
        s.assertEqual(r.returncode, 0, r.stderr)
        g = git(fork, "rev-parse", "refs/heads/gate/s1"); s.assertEqual(git(fork, "rev-parse", f"{g}^"), t)
        s.assertEqual(git(fork, "diff", "--name-only", t, g).split(), [".github/workflows/ci.yml", ".github/workflows/format.yml"])
        for f in ("ci", "format"):
            y = git(fork, "show", f"{g}:.github/workflows/{f}.yml")
            s.assertLess(y.index('sudo sysctl -w net.ipv4.ip_local_port_range="49152 65535"'), y.index("./gradlew test"), f)
        s.assertTrue((Path(root) / "state/s1").is_dir())
        for d in (fork, bin, root): shutil.rmtree(d)

    def test_with_central_ci_runs_a_child_of_t_that_builds_that_central_in(s):  # server-token-auth's CI resolved Central 2.0.3 from the network: unpublished
        for f in ("ci", "format"): s.write(f".github/workflows/{f}.yml", s.WF)
        t = s.commit("ci")
        central = tempfile.mkdtemp(); git(central, "init", "-q"); (Path(central) / "settings.gradle.kts").write_text("// central\n")
        git(central, "add", "."); git(central, "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q", "-m", "c"); c = git(central, "rev-parse", "HEAD")
        fork = tempfile.mkdtemp(); git(fork, "init", "-q", "--bare"); git(s.repo, "remote", "add", "fork", fork)
        bin = Path(tempfile.mkdtemp()); s.fake_gh(bin, s.GREEN)
        root = tempfile.mkdtemp(); change(root, "s1")
        r = subprocess.run(["bash", str(HERE.parent / "track.sh"), "gate-ci"], capture_output=True, text=True,
                           env={**os.environ, **s.env, "HD_ROOT": root, "TCOMMIT": t, "CENTRAL": f"{central}:{c}", "PATH": f"{bin}:{os.environ['PATH']}"})
        s.assertEqual(r.returncode, 0, r.stderr)
        g = git(fork, "rev-parse", "refs/heads/gate/s1")
        s.assertNotEqual(g, t); s.assertEqual(git(fork, "rev-parse", f"{g}^"), t)  # the change's head and T unchanged
        s.assertEqual(git(fork, "rev-parse", f"{g}:.central"), git(central, "rev-parse", f"{c}^{{tree}}"))
        s.assertEqual(git(fork, "show", f"{g}:.central.init.gradle"), (HERE.parent / "build/central.init.gradle").read_text().strip())
        for f in ("ci", "format"):
            y = git(fork, "show", f"{g}:.github/workflows/{f}.yml")
            s.assertLess(y.index("setup-gradle@v6"), y.index('mv .central "$RUNNER_TEMP/central"'), f); s.assertLess(y.index("init.d/central.gradle"), y.index("./gradlew test"), f)
            s.assertIn('echo "CDIR=$RUNNER_TEMP/central" >> "$GITHUB_ENV"', y)
        s.assertEqual(git(fork, "diff", "--name-only", t, g).split(), sorted([".central.init.gradle", ".central/settings.gradle.kts", ".github/workflows/ci.yml", ".github/workflows/format.yml"]))
        for d in (fork, bin, root, central): shutil.rmtree(d)

    def test_two_gates_at_once_each_use_their_own_index(s):  # five gate-ci rows failed on 'gate-index.lock': File exists, one index per repo
        for f in ("ci", "format"): s.write(f".github/workflows/{f}.yml", s.WF)
        t = s.commit("ci")
        fork = tempfile.mkdtemp(); git(fork, "init", "-q", "--bare"); git(s.repo, "remote", "add", "fork", fork)
        bin = Path(tempfile.mkdtemp()); s.fake_gh(bin, s.GREEN)
        root = tempfile.mkdtemp(); change(root, "s1"); change(root, "s2")
        gd = Path(s.repo) / ".git"; (gd / "gate-index.lock").write_text("")  # what the old shared index left for a concurrent gate
        tmp = tempfile.mkdtemp()   # a gate leaves no temp dir behind, killed or not: its index is the change's own state file
        ps = [subprocess.Popen(["bash", str(HERE.parent / "track.sh"), "gate-ci", k], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
                               env={**os.environ, **s.env, "HD_ROOT": root, "TCOMMIT": t, "PATH": f"{bin}:{os.environ['PATH']}", "TMPDIR": tmp}) for k in ("s1", "s2")]
        for p in ps: s.assertEqual(p.wait(), 0, p.stderr.read())
        s.assertEqual(os.listdir(tmp), []); s.assertTrue((Path(root) / "state/s1/gate-ci.index").exists())
        for k in ("s1", "s2"): s.assertEqual(git(fork, "rev-parse", f"refs/heads/gate/{k}^"), t)
        s.assertEqual(sorted(x.name for x in gd.iterdir() if x.name.startswith("gate-index")), ["gate-index.lock"])
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
    t = SRC
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
        login = s.up.rindex('login "$(cut -d: -f2 <<< "${desktops[$i]}")"')
        s.assertLess(s.up.index(line), login); s.assertLess(login, s.up.index("rights <> $rights"))   # read back once the logins ran
        q = lambda staff: subprocess.run(["bash", "-c", f'set -euo pipefail\nsql() {{ echo "$1"; }}\n{s.fn}\nslug=s\n{line.replace(">/dev/null", "")}'],
                                         capture_output=True, text=True, env={**os.environ, **({"STAFF": staff} if staff else {})}).stdout
        name = lambda i: subprocess.run(["bash", "-c", f'{s.fn}\nslug=s; account {i}'], capture_output=True, text=True).stdout.strip()
        s.assertIn(f"lower(account_name) = '{name(0)}' then", q(None))   # no desktop is numbered 0
        s.assertIn(f"lower(account_name) = '{name(2)}' then 'ADMINISTRATOR' else ''", q("2"))
        s.assertIn(f"like '{name('')}%'", q("2"))


class KeyOnCachedBuild(unittest.TestCase):  # a cached or:<T> left state/<slug>/key empty: docker made the mount sources directories and the server exited at boot
    t = SRC
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


class TokenLogin(unittest.TestCase):  # a test desktop's token login (auth type 7): server-token-auth verifies the compact token's signature, expiry and account
    def test_mint_signs_a_compact_token_with_the_secret_game_yml_holds(s):
        import base64, hashlib, hmac, time, issuer as t
        d = Path(tempfile.mkdtemp()); s.addCleanup(shutil.rmtree, d)
        block = t.key(d / "a"); t.key(d / "b")
        secret = base64.b64decode(block.split('secret: "')[1].split('"')[0]); s.assertEqual(len(secret), 32)
        def verifies(tok):
            acc, exp, mac = tok.split(".")
            return hmac.compare_digest(mac, t.b64(hmac.new(secret, f"{acc}.{exp}".encode(), hashlib.sha256).digest()))
        tok = t.mint(d / "a", "hab12", exp=-5)
        acc, exp, _ = tok.split("."); s.assertEqual(acc, "hab12"); s.assertLess(int(exp), time.time())
        s.assertTrue(verifies(tok)); s.assertLessEqual(len(t.mint(d / "a", "h" * 12)), 90)   # the client's RSA login block holds about 90 bytes
        s.assertFalse(verifies(t.mint(d / "b", "hab12")))   # another secret: a forged token
        s.assertEqual(t.key(d / "a"), block)   # the secret is made once

    def test_tokens_answers_the_token_written_last(s):
        import http.server, threading, urllib.request, issuer as t
        d = Path(tempfile.mkdtemp()); s.addCleanup(shutil.rmtree, d); t.GameSession.dir = d
        srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), t.GameSession); threading.Thread(target=srv.serve_forever, daemon=True).start(); s.addCleanup(srv.shutdown)
        get = lambda: json.load(urllib.request.urlopen(f"http://127.0.0.1:{srv.server_port}/game-session/v1/tokens", data=b""))
        (d / "token").write_text("a.b.c\n"); s.assertEqual(get(), {"token": "a.b.c"})
        (d / "token").write_text("x.y.z\n"); s.assertEqual(get(), {"token": "x.y.z"})

    def test_the_apk_signs_in_and_gets_the_official_game_session_shapes(s):
        import base64, http.server, threading, urllib.error, urllib.parse, urllib.request, issuer as t
        d = Path(tempfile.mkdtemp()); s.addCleanup(shutil.rmtree, d); t.key(d); (d / "account").write_text("hab12\n"); t.GameSession.dir = d
        srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), t.GameSession); threading.Thread(target=srv.serve_forever, daemon=True).start(); s.addCleanup(srv.shutdown)
        base = f"http://127.0.0.1:{srv.server_port}/"
        class Stay(urllib.request.HTTPRedirectHandler):
            redirect_request = lambda *a: None
        def call(path, body=None, bearer=None, form=False):
            r = urllib.request.Request(base + path, data=body if body is None or form else json.dumps(body).encode(), headers={"Authorization": f"Bearer {bearer}"} if bearer else {})
            try:
                return json.load(urllib.request.build_opener(Stay).open(r))
            except urllib.error.HTTPError as e:
                return e.code, e.headers.get("Location")
        disc = call(".well-known/openid-configuration"); s.assertEqual(disc["issuer"], base); s.assertEqual(disc["token_endpoint"], base + "oauth2/token")
        code, loc = call("oauth2/auth?redirect_uri=app%3A%2Fcb&state=s%201&nonce=n1"); s.assertEqual(code, 302)
        q = dict(urllib.parse.parse_qsl(urllib.parse.urlsplit(loc).query)); s.assertEqual(q["state"], "s 1")
        idt = call("oauth2/token", urllib.parse.urlencode({"code": q["code"], "client_id": "c"}).encode(), form=True)["id_token"]
        claims = json.loads(base64.urlsafe_b64decode(idt.split(".")[1] + "==")); s.assertEqual((claims["sub"], claims["aud"], claims["nonce"]), ("hab12", "c", "n1"))
        s.assertEqual(call("game-session/v1/sessions", {"idToken": idt[:-2] + "xx"})[0], 401)   # a forged idToken
        sid = call("game-session/v1/sessions", {"idToken": idt})["sessionId"]
        s.assertEqual(call("game-session/v1/accounts", bearer="nope")[0], 401)
        (acc,) = call("game-session/v1/accounts", bearer=sid); s.assertEqual(acc["displayName"], "hab12")
        s.assertIsInstance(acc["accountId"], int); s.assertIsInstance(acc["userHash"], int)
        s.assertEqual(call("game-session/v1/tokens", b"", form=True)["token"].split(".")[0], "hab12")   # no DIR/token: a fresh one for DIR/account

    def test_up_token_points_the_desktop_at_the_issuer_and_down_removes_it(s):
        sh = SRC
        up = sh.split("\nup() {")[1].split("\n}")[0]; down = sh.split("\ndown() {")[1].split("\n}")[0]
        s.assertIn('issuer.py" key "$state/token" >> "$state/game.yml"', up)
        s.assertIn("param=\\1=https://127.0.0.1:", up)   # the client casts params 11 and 22 connections to https
        s.assertIn('-v "$tpl:/app/protocol/jav_config.ws.template:ro"', up)
        s.assertIn('"token-$slug"', down)
        s.assertIn('"-Dosrstest.token=${tok:-false}"', sh); s.assertIn("JX_SESSION_ID:JX_CHARACTER_ID:JX_DISPLAY_NAME", sh)

    def test_live_takes_the_apks_tokens_from_the_issuer_whose_secret_its_game_yml_holds(s):  # the sidecar's LOCAL_AUTH tokens were unsigned
        import javcheck
        compose = (HERE / "docker-compose.yml").read_text(); ship = SRC.split("\nship() {")[1].split("\n}")[0]
        s.assertNotIn('LOCAL_AUTH: "true"', compose); s.assertIn('"${ISSUER_DIR}/jav_config.ws.template:/app/protocol/jav_config.ws.template:ro"', compose)
        s.assertIn('"${ISSUER_DIR}:/t:ro"', compose); s.assertIn("command: [python3, /issuer.py, serve, /t]", compose)
        s.assertIn("up -d --no-deps openrune http http-desktop issuer", ship); s.assertIn("COPY --chown=1000:1000 game.yml /app/game.yml", ship)
        s.assertIn('python3 "$self/server/issuer.py" key "$ISSUER_DIR"; } > "$state/game.yml"', ship)
        d = Path(tempfile.mkdtemp()); s.addCleanup(shutil.rmtree, d)
        line = next(l for l in ship.splitlines() if "param=(11|20|22|28)" in l)
        subprocess.run(["bash", "-c", line], check=True, env={**os.environ, "self": str(HERE.parent), "ISSUER_DIR": str(d)})
        t = (d / "jav_config.ws.template").read_text(encoding="utf-8"); orig = (HERE / "jav_config.ws.template").read_text(encoding="utf-8")
        params = dict(l[6:].split("=", 1) for l in t.splitlines() if l.startswith("param="))
        s.assertEqual({k: params[k] for k in ("11", "20", "22", "28")}, dict.fromkeys(("11", "20", "22", "28"), "http://{{WORLD_HOST}}:8083/"))
        s.assertEqual(len(t.splitlines()), len(orig.splitlines())); s.assertEqual(params["17"], "{{HTTP_BASE}}/slr.ws?order=LPWM")
        s.assertEqual(javcheck.foreign(t.replace("{{HTTP_BASE}}", "http://10.0.2.2:8080").replace("{{WORLD_HOST}}", "10.0.2.2")), [])

    def test_the_launcher_refuses_a_jagex_access_or_refresh_token_in_every_mode(s):
        src = (HERE / "OsrsTestLauncher.java").read_text()
        s.assertIn('"JX_ACCESS_TOKEN", "JX_REFRESH_TOKEN"', src)
        s.assertIn('boolean session = v.equals("JX_SESSION_ID") || v.equals("JX_CHARACTER_ID") || v.equals("JX_DISPLAY_NAME");', src)
        s.assertIn("token && session ? System.getenv(v) == null : System.getenv(v) != null", src)
        s.assertIn('-cp "$t\\\\rsprox\\\\$l;', SRC)   # up runs this tree's launcher, compiled from this source
