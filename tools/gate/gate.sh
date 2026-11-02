#!/usr/bin/env bash
# tools/gate/gate.sh <verb> <slug> [arg]: the server track's gate and every verb a contract row runs (tools/track.sh dispatches
# them here); the build is tools/build/build.sh's, sourced. hd keys a gate by the build key and the hash of tools/gate/ and the
# files the contract's rows name. Env from hd: SLUG HEAD T (the build key) G (the gate key) TCOMMIT BASE CLONE EVIDENCE CENTRAL HD_ROOT. Verbs:
#   test [MODULE...]  build, then each module's tests and spotlessCheck on the build copy (no module: those holding a file
#                     the change's tree changes from origin/main); log evidence/<slug>/test.log, result state/<slug>/test.json
#   spotless          spotlessCheck alone on those modules at TCOMMIT (a contract row; no TCOMMIT: the working copy's T), no build;
#                     log evidence/<slug>/spotless.log, result state/<slug>/spotless.json
#   gate TCOMMIT      the build, then every check: and gate: row of the contract from the build copy (`hd rows`), recorded under G
#   gate-ci           fork CI and Formatting green on gate/<slug> (blocks until both conclude: one conditional read of the
#                     sha's runs, free while unchanged; a GitHub quota refusal holds until its reset) at a child of TCOMMIT whose
#                     workflows first move the runner's ephemeral ports above the game port 43594 (an outgoing socket on it
#                     fails the boot's bind), and with CENTRAL build that Central in, as build does (the change's head unchanged)
#                     and whose CI restores the game data from the fork release asset game-data-<game.example.yml blob12>
#                     (seeded once from or:<T>) on an actions/cache miss, never openrs2; a run failed at a step the head does not
#                     touch is re-run once its source answers, and after 3 attempts the row exits 75: held, no result
. "$(dirname "$0")/../build/build.sh"   # verb slug arg self(tools/build) repo root state dev copy atomic record tree central build
projects() { local f d; for f; do d=$(dirname "$f"); while [ "$d" != . ] && [ ! -f "$copy/$d/build.gradle.kts" ]; do d=$(dirname "$d"); done; [ "$d" = . ] || { [ "${d##*/}" = pack ] && d=${d%/pack}/$(basename "${d%/pack}")-pack; echo ":${d//\//:}"; }; done | sort -u; }   # the Gradle projects holding these files: settings.gradle.kts names a pack directory <parent>-pack
projdir() { local d=${1#:}; d=${d//://}; [ "${d##*/}" = "$(basename "$(dirname "$d")")-pack" ] && d=$(dirname "$d")/pack; echo "$d"; }   # a project's directory
tests() { build; gradle_check test "$@"; }
spotless() {  # the check row: spotlessCheck on every module T changes from origin/main, so a lint the ratchet sees in a touched file fails the hand-over, not CI; no build (the gate's holds the build lock)
  tree; [ -d "$copy" ] || git -C "$repo" worktree add -q --detach "$copy" "$tc"; gradle_check spotless
}
gradle_check() {  # gradle_check test|spotless [MODULE...]: spotless's JGit cannot open a linked worktree: the container sees the copy's .git as a plain repo of T, its objects borrowed
  local kind=$1; shift; git -C "$copy" reset -q --hard "$tc"; git -C "$copy" clean -fdq   # apart: a failed reset in an && list would not stop the verb
  local mods=("$@") f d g=$state/$kind.git out=${EVIDENCE:-$root/evidence/$slug} mb changed
  if [ ${#mods[@]} = 0 ]; then   # each read on its own line, so one that fails stops the verb instead of reading as no module touched
    mb=$(git -C "$repo" merge-base origin/main "$tc"); changed=$(git -C "$repo" diff --name-only "$mb" "$tc")
    mapfile -t mods < <(projects $changed)
  fi
  if [ ${#mods[@]} = 0 ]; then [ "$kind" = spotless ] && { echo "no module touched"; record true spotless <<< "no module touched"; return 0; }
    echo "no module touched: tools/track.sh test <slug> MODULE..."; return 2; fi
  echo "modules: ${mods[*]}"
  rm -rf "$g"; git init -q --bare "$g"; git --git-dir="$g" config core.bare false; git --git-dir="$g" config core.worktree "$copy"
  echo "$repo/.git/objects" > "$g/objects/info/alternates"
  git --git-dir="$g" update-ref refs/remotes/origin/main "$(git -C "$repo" rev-parse origin/main)"; git --git-dir="$g" update-ref --no-deref HEAD "$tc"
  git -C "$copy" update-index -q --refresh || true; cp "$(git -C "$copy" rev-parse --absolute-git-dir)/index" "$g/index"
  echo "gitdir: $g" > "$state/$kind.gitfile"; mkdir -p "$out"
  for d in "${mods[@]}"; do rm -rf "$copy/$(projdir "$d")/build/test-results/test"; done   # the report is this run's
  docker run --rm -v "gradle-$slug:/gradle" -v or-dl:/dl eclipse-temurin:21 chown 1000:1000 /gradle /dl   # creates or reuses the slug's volumes: one docker makes is root's, the run is 1000 (spotless runs on a slug never built)
  central; docker run --rm --name "$kind-$slug" --cpus=4 --memory=5g -u 1000:1000 -e HOME=/gradle/home -e GRADLE_USER_HOME=/gradle -e CDIR="$cdir" \
    -v "gradle-$slug:/gradle" -v or-dl:/dl "${cvols[@]}" -v "$repo/.git:$repo/.git:ro" -v "$g:$g:ro" -v "$copy:$copy" -v "$state/$kind.gitfile:$copy/.git:ro" -w "$copy" \
    eclipse-temurin:21 bash -c 'oc=$(getent passwd "$(id -u)" | cut -d: -f6)/.openrune/caches; mkdir -p $oc && cp -an /dl/. $oc/ 2>/dev/null || true
      ./gradlew --console=plain --build-cache --no-configuration-cache --max-workers=4 -Dorg.gradle.jvmargs=-Xmx1536m${CDIR:+ --init-script /central.init.gradle} --continue "$@"' _ \
    $([ "$kind" = test ] && printf '%s:test ' "${mods[@]}") $(printf '%s:spotlessCheck ' "${mods[@]}") > "$out/$kind.log" 2>&1 && ok=true || ok=false
  for d in "${mods[@]}"; do cat "$copy/$(projdir "$d")"/build/test-results/test/*.xml 2>/dev/null | grep -o '<testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' || true; done >> "$out/$kind.log"
  record "$ok" "$kind" < "$out/$kind.log"; grep -E -A1 '^<testsuite|went wrong|violations|FAILED|BUILD' "$out/$kind.log" | grep -v '^--$' || true; [ "$ok" = true ]
}
gate() {
  tc=$arg; build; git -C "$copy" reset -q --hard "$tc" && git -C "$copy" clean -fdq
  out=$(HD_ROOT=$root python3 "$root/hd.py" rows "$slug" "$tc" "$copy" 2>&1) && rc=0 || rc=$?
  [ $rc != 75 ] || { echo "$out"; exit 75; }   # a row held (its source down): no result, hd starts the gate again
  T=${G:-$T}; record "$([ $rc = 0 ] && echo true || echo false)" gate <<< "$out"
}
gate_sha() {  # a child of TCOMMIT whose workflows move the ephemeral ports off the game port, and with CENTRAL hold that Central tree at .central/ built in
  set -e   # a command substitution does not inherit it
  idx=$state/gate-ci.index   # the change's own index (gates of other changes run at once), read-tree replaces it: no temp dir a killed gate leaves behind
  GIT_INDEX_FILE=$idx git -C "$repo" read-tree "$TCOMMIT"
  step='      - name: Ephemeral ports above the game port (tools/gate/gate.sh gate-ci)
        run: sysctl net.ipv4.ip_local_port_range; sudo sysctl -w net.ipv4.ip_local_port_range="49152 65535"'
  if [ -n "${CENTRAL:-}" ]; then
    IFS=: read -r cpath ccommit <<< "$CENTRAL"; git -C "$repo" fetch -q "$cpath" "$ccommit"
    GIT_INDEX_FILE=$idx git -C "$repo" read-tree --prefix=.central/ "$ccommit"
    GIT_INDEX_FILE=$idx git -C "$repo" update-index --add --cacheinfo "100644,$(git -C "$repo" hash-object -w "$self/central.init.gradle"),.central.init.gradle"
    step="$step"'
      - name: Central of the gate tree (tools/gate/gate.sh gate-ci)
        run: |
          mv .central "$RUNNER_TEMP/central"; echo "CDIR=$RUNNER_TEMP/central" >> "$GITHUB_ENV"
          mkdir -p "${GRADLE_USER_HOME:-$HOME/.gradle}/init.d"; mv .central.init.gradle "${GRADLE_USER_HOME:-$HOME/.gradle}/init.d/central.gradle"'
  fi
  for f in ci format; do f=.github/workflows/$f.yml
    y=$(git -C "$repo" show "$TCOMMIT:$f" | awk -v step="$step" '{print} /uses: gradle\/actions\/setup-gradle@/{print step; n++} END{exit n!=1}') || { echo "$f: no single setup-gradle step to follow" >&2; return 1; }
    y=$(game_data "$y")
    GIT_INDEX_FILE=$idx git -C "$repo" update-index --cacheinfo "100644,$(printf '%s\n' "$y" | git -C "$repo" hash-object -w --stdin),$f"
  done
  git -C "$repo" commit-tree "$(GIT_INDEX_FILE=$idx git -C "$repo" write-tree)" -p "$TCOMMIT" -m "gate: $TCOMMIT${CENTRAL:+ with Central ${ccommit:-}}"
}
gate_ci() {  # pushes T (gate_sha) to gate/<slug>; the newest run of each workflow at that sha concludes green, or the row fails;
  # a run failed at a step the head does not touch is the source's, not the head's: re-run once that source answers; held (exit 75: hd records no result) after 3 attempts
  R=$(git -C "$repo" remote get-url fork | sed -E 's#.*github.com[:/]##; s#\.git$##'); seed
  sha=$(gate_sha); git -C "$repo" push -q --force fork "$sha:refs/heads/gate/$slug"
  q="repos/$R/actions/runs?branch=gate/$slug&head_sha=$sha&per_page=100" etag= runs= ran= rr=
  while :; do
    read_runs || return 1; ok=0
    for wf in CI Formatting; do
      read -r _ st id att <<< "$(grep -m1 "^$wf " <<< "$runs" || true)"   # newest first
      case $st in success/*) ok=$((ok + 1)) ;;
        failure/completed|cancelled/completed|timed_out/completed) case " $rr " in *" $id.$att "*) continue ;; esac   # its re-run asked for: GitHub has not yet shown it
          f=$(failed_step "$id")
          if ! infra "$f"; then echo "$wf $st at '$f' on gate/$slug at $sha (T $TCOMMIT)"; return 1; fi
          [ "$att" -lt 3 ] || { echo "$wf failed at '$f' in $att attempts on gate/$slug at $sha, a step the head does not touch: no result"; exit 75; }
          echo "$wf failed at '$f', a step the head does not touch: re-run once its source answers"; answers "$f"
          if gh run rerun "$id" --failed -R "$R"; then rr="$rr $id.$att"; fi ;;
        */completed) echo "$wf $st on gate/$slug at $sha (T $TCOMMIT)"; return 1 ;;
        "") case " $ran " in *" $wf "*) ;; *) if gh workflow run "$wf" -R "$R" --ref "gate/$slug"; then ran="$ran $wf"; fi ;; esac ;; esac
    done
    [ $ok = 2 ] && break
    sleep 30   # the next read is conditional: while no run changes GitHub answers 304, which costs no quota
  done; echo "CI and Formatting green at $sha (T $TCOMMIT)"
}
game_data() {  # workflow $1 with a step restoring the game data from the fork release asset game-data-<k> on an actions/cache miss, so the gate never downloads openrs2's cache
  K=$(git -C "$repo" rev-parse -q --verify "$TCOMMIT:game.example.yml" | cut -c1-12 || true) python3 -c '
import os, sys
y, k = sys.argv[1], os.environ["K"]
gen = "      - name: Generate cache and gameval sources\n"
if not k or "id: gamecache" not in y or gen not in y:
    print(y); sys.exit()
step = ("      - name: Game data of the gate (tools/gate/gate.sh gate-ci)\n        id: gamedata\n        if: steps.gamecache.outputs.cache-hit != \x27true\x27\n"
        "        continue-on-error: true\n        env:\n          GH_TOKEN: ${{ github.token }}\n"
        f"        run: mkdir -p .data && gh release download game-data -R \"$GITHUB_REPOSITORY\" -p game-data-{k}.tar.gz -O - | tar -xz -C .data\n\n")
y = y.replace(gen, step + gen, 1)
y = y.replace("if: steps.gamecache.outputs.cache-hit != \x27true\x27\n        run: ./gradlew :or-cache:freshCache",
              "if: steps.gamecache.outputs.cache-hit != \x27true\x27 && steps.gamedata.outcome != \x27success\x27\n        run: ./gradlew :or-cache:freshCache")
y = y.replace("if: steps.gamecache.outputs.cache-hit == \x27true\x27\n", "if: steps.gamecache.outputs.cache-hit == \x27true\x27 || steps.gamedata.outcome == \x27success\x27\n")
print(y)' "$1"
}
seed() {  # the fork release asset game-data-<k> (k: game.example.yml's blob, which names the revision and so openrs2's cache id) from the image of T, once per k: CI reads it on an actions/cache miss
  local k a f; k=$(git -C "$repo" rev-parse -q --verify "$TCOMMIT:game.example.yml" | cut -c1-12) && [ -n "$k" ] || return 0; a=game-data-$k.tar.gz; f=$state/$a
  gh release view game-data -R "$R" --json assets -q '.assets[].name' 2>/dev/null | grep -qx "$a" && return 0
  docker image inspect "or:${T:-}" >/dev/null 2>&1 || { echo "no or:${T:-} to seed $a from: CI downloads the cache from openrs2"; return 0; }
  docker run --rm --entrypoint tar "or:$T" -cz -C /app/.data cache/LIVE gamevals-binary/gamevals.dat gamevals-binary/gamevals_generated.dat > "$f.tmp" || { rm -f "$f.tmp"; echo "seeding $a failed"; return 0; }
  mv -f "$f.tmp" "$f"
  gh release view game-data -R "$R" >/dev/null 2>&1 || gh release create game-data -R "$R" --prerelease --title "game data" --notes "The harness gate's game data, one asset per game.example.yml" || true
  gh release upload game-data "$f" -R "$R" --clobber || echo "seeding $a failed: CI downloads the cache from openrs2"; rm -f "$f"
}
failed_step() {  # the first step of run $1 that failed or was stopped (a cancel, ci.yml's timeout) (one read per such run)
  until gh api "repos/$R/actions/runs/$1/jobs" -q '[.jobs[].steps[] | select(.conclusion == "failure" or .conclusion == "cancelled") | .name][0] // ""'; do sleep 30; done
}
infra() {  # step $1 reads nothing the head changes: the runner's setup, the cache's download and generation when the head changes no cache input
  case $1 in "Set up job"|"Checkout repository"|"Set up JDK"*|"Set up Gradle"|"Cache game data"|*"(tools/gate/gate.sh gate-ci)"|"Post "*|"Complete job") return 0 ;;
    "Generate cache and gameval sources"|"Rebuild sources from cached game data")
      [ -n "${BASE:-}" ] && [ -n "${HEAD:-}" ] || return 1
      ! git -C "$repo" diff --name-only "$BASE...$HEAD" | grep -qE 'or-cache/|/pack/|gamevals|raw-cache|game\.example\.yml' ;;
    *) return 1 ;; esac
}
answers() {  # blocks until the source step $1 reads answers: GitHub for the runner's own steps (it answered this read), openrs2 for the cache's
  case $1 in "Generate cache"*) until curl -sfI -o /dev/null https://archive.openrs2.org/; do sleep 30; done ;; esac
}
read_runs() {  # $runs: "<workflow> <conclusion>/<status> <id> <attempt>" of each run at sha, newest first, read with If-None-Match the last ETag (one read
  # for both workflows; a 304 leaves $runs and costs no quota). A quota refusal holds the row until GitHub's reset, never fails it.
  local code tag reset body s
  while :; do
    { read -r code tag reset; body=$(cat); } < <(gh api -i ${etag:+-H "If-None-Match: $etag"} "$q" 2>/dev/null | python3 -c '
import json, re, sys, time
h, _, b = sys.stdin.read().replace("\r\n", "\n").partition("\n\n"); l = h.splitlines()
m = re.match(r"HTTP/\S+ (\d{3})", l[0] if l else ""); code = m.group(1) if m else "000"
k = {a.strip().lower(): v.strip() for a, _, v in (x.partition(":") for x in l[1:])}
if code in ("403", "429") and (k.get("x-ratelimit-remaining") == "0" or "rate limit" in b.lower()):  # GitHub states its reset: retry-after, else x-ratelimit-reset, else its documented minute
    print("quota -", int(time.time()) + int(k["retry-after"]) if k.get("retry-after", "").isdigit() else k.get("x-ratelimit-reset") or int(time.time()) + 60)
else:
    print(code, k.get("etag", "-"), 0)
    if code == "200":
        for r in json.loads(b)["workflow_runs"]: print(r["name"].replace(" ", "_"), (r["conclusion"] or "") + "/" + r["status"], r.get("id", 0), r.get("run_attempt", 1))
')
    case $code in 200) etag=$tag runs=$body; return ;; 304) return ;;
      quota) echo "GitHub refused $q on quota: held until its reset $(date -ud "@$reset" +%TZ)"; s=$((reset - $(date +%s))); if [ $s -gt 0 ]; then sleep $s; fi ;;
      ""|000|5??) sleep 30 ;;   # GitHub did not answer: read again
      *) echo "$q: HTTP $code"; return 1 ;; esac
  done
}

case $verb in gate|gate-ci) "${verb//-/_}" ;; test) arg= tests "${@:3}" ;; spotless) arg=${TCOMMIT:-} spotless ;;
  *) echo "verbs: test spotless gate gate-ci" >&2; exit 2 ;; esac
