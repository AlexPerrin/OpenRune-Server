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
  if out=$(HD_ROOT=$root python3 "$root/hd.py" rows "$slug" "$tc" "$copy" 2>&1); then ok=true; else ok=false; fi
  T=${G:-$T}; record "$ok" gate <<< "$out"
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
    GIT_INDEX_FILE=$idx git -C "$repo" update-index --cacheinfo "100644,$(printf '%s\n' "$y" | git -C "$repo" hash-object -w --stdin),$f"
  done
  git -C "$repo" commit-tree "$(GIT_INDEX_FILE=$idx git -C "$repo" write-tree)" -p "$TCOMMIT" -m "gate: $TCOMMIT${CENTRAL:+ with Central ${ccommit:-}}"
}
gate_ci() {  # pushes T (gate_sha) to gate/<slug>; the newest run of each workflow at that sha concludes green, or the row fails
  sha=$(gate_sha); git -C "$repo" push -q --force fork "$sha:refs/heads/gate/$slug"
  R=$(git -C "$repo" remote get-url fork | sed -E 's#.*github.com[:/]##; s#\.git$##')
  q="repos/$R/actions/runs?branch=gate/$slug&head_sha=$sha&per_page=100" etag= runs= ran=
  while :; do
    read_runs || return 1; ok=0
    for wf in CI Formatting; do
      st=$(grep -m1 "^$wf " <<< "$runs" | cut -d' ' -f2 || true)   # newest first
      case $st in success/*) ok=$((ok + 1)) ;; */completed) echo "$wf $st on gate/$slug at $sha (T $TCOMMIT)"; return 1 ;;
        "") case " $ran " in *" $wf "*) ;; *) if gh workflow run "$wf" -R "$R" --ref "gate/$slug"; then ran="$ran $wf"; fi ;; esac ;; esac
    done
    [ $ok = 2 ] && break
    sleep 30   # the next read is conditional: while no run changes GitHub answers 304, which costs no quota
  done; echo "CI and Formatting green at $sha (T $TCOMMIT)"
}
read_runs() {  # $runs: "<workflow> <conclusion>/<status>" of each run at sha, newest first, read with If-None-Match the last ETag (one read
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
        for r in json.loads(b)["workflow_runs"]: print(r["name"].replace(" ", "_"), (r["conclusion"] or "") + "/" + r["status"])
')
    case $code in 200) etag=$tag runs=$body; return ;; 304) return ;;
      quota) echo "GitHub refused $q on quota: held until its reset $(date -ud "@$reset" +%TZ)"; s=$((reset - $(date +%s))); if [ $s -gt 0 ]; then sleep $s; fi ;;
      ""|000|5??) sleep 30 ;;   # GitHub did not answer: read again
      *) echo "$q: HTTP $code"; return 1 ;; esac
  done
}

case $verb in gate|gate-ci) "${verb//-/_}" ;; test) arg= tests "${@:3}" ;; spotless) arg=${TCOMMIT:-} spotless ;;
  *) echo "verbs: test spotless gate gate-ci" >&2; exit 2 ;; esac
