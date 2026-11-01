#!/usr/bin/env bash
# tools/track.sh <verb> <slug> [arg]: the server track (docs/ARCHITECTURE.md of osrs-harness). Env from hd: SLUG HEAD T
# TCOMMIT BASE CLONE EVIDENCE PINS HD_ROOT CENTRAL (HEADS NAMES for ship). Verbs:
#   build [TCOMMIT]   the image or:<T> of the tree T (a dev: the working copy snapshotted, T from `hd tree`); cached by T;
#                     CENTRAL=<Central clone>:<commit> builds that Central in place of the published dev.or2 modules
#                     (server/central.init.gradle substitutes each, :central-app included)
#   up [N]            server-<slug> and http-<slug> from or:<T> on the change's ports (quest mode: QUEST_MODE, else the change's front
#                     matter quest-mode, else respect-progress; or assume-completed), N desktops logged in,
#                     each loading the live client's devtools jar (CLIENT_TRACK: the client track's script, its build of slug live);
#                     a fresh database, or with RESUME=1 the accounts as the change's last down saved them; the realm as live
#                     (dev_mode off), every account a member at rank 0, or with STAFF=N the Nth desktop's an administrator
#   restart           restarts server-<slug>, its game.yml and database kept, and logs up's desktops in again
#   down              stops the desktops, waits until the server has saved and dropped their characters, removes the containers
#   gate TCOMMIT      every check: and gate: row of the contract from the build copy (`hd rows`)
#   gate-ci           fork CI and Formatting green on gate/<slug> at TCOMMIT (blocks until both conclude)
#   ship TCOMMIT      the integration tree: build, the deploy image with the live key, boot check, deploy, or roll back
#   measure GOAL      the content inventory's share of rows whose unit is in PASSES
#   log <slug> PATTERN  the change's server log since the run's marker (slug `live`: the live server)
#   recordings <slug> REGEX  the RSProx transcripts holding a packet type or text
# Windows locations, run through WSL interop (override in the environment):
#   WINDOWS_SYSTEM32  /mnt/c/Windows/System32   netstat.exe, taskkill.exe
#   DESKTOP_DIR       /mnt/c/osrs-test          the first desktop; the Nth is DESKTOP_DIR<N> (jdk/bin/java.exe, jps.exe)
set -euo pipefail
verb=$1; slug=${2:-${SLUG:-}}; arg=${3:-}   # a contract row passes no slug: hd exports SLUG
self=$(cd "$(dirname "$0")" && pwd)
repo=${CLONE:-$HOME/src/OpenRune-Server}
root=${HD_ROOT:-$HOME/osrs-harness}
[ -f "$root/harness/changes/$slug.md" ] || [ -f "$root/harness/goals/$slug.md" ] || case $slug in live|integration) ;; *) echo "no change or goal \"$slug\": tools/track.sh <verb> <slug> [arg]" >&2; exit 2 ;; esac
state=$root/state/$slug; mkdir -p "$state"
dev=$repo/.worktrees/$slug; copy=$repo/.worktrees/$slug-build
live_image=osrs-openrune:241; compose_dir=$self/server
sidecar_data=${SIDECAR_DATA:-$HOME/src/openrune-work/server-tools/sidecar/data}
record() { python3 -c 'import json,sys;print(json.dumps({"T":sys.argv[1],"ok":sys.argv[2]=="true","detail":sys.stdin.read()[-2000:]}))' "$T" "$1" > "$state/$2.json"; }
ready() {  # ready <container> [since]: blocks until "Server ready in" or the container stops
  while ! docker logs ${2:+--since "$2"} "$1" 2>&1 | grep -q "Server ready in"; do
    [ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" = true ] || { echo "$1 is not running"; return 1; }; sleep 5
  done
}
port() { echo $(( 20000 + 16#$(printf %s "$slug" | sha1sum | cut -c1-3) * 2 + $1 )); }   # game +0, http +1; shortcut: two slugs may hash alike, pick from up.json when it happens
quest_mode() { m=${QUEST_MODE:-$(awk '/^---$/{n++; next} n==1 && /^quest-mode:/{print $2}' "$root/harness/changes/$slug.md" 2>/dev/null)}; echo "${m:-respect-progress}"; }   # QUEST_MODE, else the change's front matter quest-mode, else respect-progress
quests() { case $1 in respect-progress|assume-completed) ;; *) echo "quest-mode \"$1\": respect-progress or assume-completed" >&2; return 1 ;; esac   # quests MODE: stdin game.yml with quest-requirements mode MODE
  y=$(sed -E "/^[[:space:]]*quest-requirements:/,/^[[:space:]]*mode:/ s/(mode:[[:space:]]*).*/\1\"$1\"/"); grep -q "mode: \"$1\"" <<< "$y" || { echo "game.yml has no quest-requirements mode" >&2; return 1; }; printf '%s\n' "$y"; }
account() { echo "h$(printf %s "$slug" | sha1sum | cut -c1-9)${1:-}"; }   # the Nth desktop's login name and password (no N: the prefix of this slug's names): 11 characters (a login name takes 12 at most), listed in the slug's up.json
mine="select c.id from account_characters c join accounts a on a.id = c.account_id where lower(a.account_name) like '$(account)%'"   # this slug's characters
sql() { local pg; pg=$(docker logs "server-$slug" 2>&1 | grep -oE 'jdbc:postgresql://localhost:[0-9]+' | tail -1 | grep -oE '[0-9]+$')
  docker run --rm --network "container:server-$slug" postgres:16-alpine psql -h 127.0.0.1 -p "$pg" -U postgres -d postgres -tAq -c "$1"; }
offline() { until [ "$(sql "select count(*) from main.character_progress where online_central_world_id is not null and character_id in ($mine)")" = 0 ]; do sleep 1; done; }   # each character saved at its logout

sys32=${WINDOWS_SYSTEM32:-/mnt/c/Windows/System32}; desk=${DESKTOP_DIR:-/mnt/c/osrs-test}
need() { [ -x "$1" ] || { echo "$1 not found: set $2" >&2; exit 2; }; }   # need <program> <the variable that locates it>
tcp() { "$sys32/netstat.exe" -ano -p TCP | tr -d '\r' | awk '$4=="LISTENING"{n=split($2,a,":");print a[n], $5}'; }   # "<port> <Windows PID>" of each Windows listening socket
marked() { "$desk/jdk/bin/jps.exe" -v | tr -d '\r' | awk -v m="-Dosrstest.marker=osrs-test-$1" '{for (f = 3; f <= NF; f++) if ($f == m) {print $1, $2; next}}'; }   # "<Windows PID> <main class>" of instance N's JVMs: only desktop_start sets the marker
owned() {  # the desktops a live run's up.json lists (its server container runs), one "desktopN:port[:pids]" per line
  for f in "$root"/state/*/up.json; do
    [ -e "$f" ] && [ "$(docker inspect -f '{{.State.Running}}' "server-$(basename "$(dirname "$f")")" 2>/dev/null)" = true ] || continue
    python3 -c 'import json,sys;d=json.load(open(sys.argv[1]));print("\n".join(d.get("desktops", d.get("clients", "")).split()))' "$f"   # shortcut: reads an up.json written before the rename to desktops; drop "clients" once no live run's up.json carries it
  done
}
# The desktops: the staged dirs DESKTOP_DIR, DESKTOP_DIR2 .. DESKTOP_DIR5 (JDK, RSProx launcher, RuneLite; the devtools
# plugin is the live client's build, copied in at each start), devtools port 7779+N for the Nth, run through WSL interop by absolute path. Every JVM of instance N
# carries -Dosrstest.marker=osrs-test-N; nothing without it is ever stopped (the user's own RuneLite).
desktop_start() {  # under state/desktop.lock: stops the desktops no live run owns, starts the first instance no desktop answers on;
  # sets inst dt pids (desktopN, its devtools port, the launcher's and the port's Windows PIDs) and keeps the lock until the caller lists the desktop in up.json
  local i d dp w own busy
  need "$sys32/netstat.exe" WINDOWS_SYSTEM32; need "$desk/jdk/bin/java.exe" DESKTOP_DIR; need "$desk/jdk/bin/jps.exe" DESKTOP_DIR
  exec 9> "$root/state/desktop.lock"; flock 9
  own=$(owned | cut -d: -f1)
  for i in 1 2 3 4 5; do grep -qx "desktop$i" <<< "$own" || desktop_stop "desktop$i"; done   # the sweep: a desktop no live run lists is an orphan
  busy=$(tcp | cut -d' ' -f1)
  for i in 1 2 3 4 5; do d=$desk${i#1}; dp=$((7779 + i)); grep -qx "desktop$i" <<< "$own" || grep -qx "$dp" <<< "$busy" || [ -n "$(marked "$i")" ] || break; d=; done
  [ -n "$d" ] || { echo "no free desktop instance" >&2; return 1; }
  mkdir -p "$d/home/.rlcustom/sideloaded-plugins" && cp "$jar" "$d/home/.rlcustom/sideloaded-plugins/OpenRune-Developer-Tools.jar"
  w=$(wslpath -w "$d")
  printf '%s\n' "- name: OpenRune Test - $slug" "  jav_config_url: http://127.0.0.1:$http/jav_config.ws" "  modulus: $modulus" "  revision: 241.1" \
    "  game_server_port: $game" "  runelite_bootstrap_commithash: e617bef1a7f5c39fbe8768994d27d638941efd5d" "  binary_folder: openrune-qa" > "$d/home/.rsprox/proxy-targets.yaml"
  (cd "$d" && JAVA_TOOL_OPTIONS="-Duser.home=$w\\home -Dosrstest.marker=osrs-test-$i" WSLENV=${WSLENV:+$WSLENV:}JAVA_TOOL_OPTIONS \
    exec setsid "$d/jdk/bin/java.exe" "-Dosrstest.marker=osrs-test-$i" "-Dosrstest.portskip=$((20 * (i - 1)))" "-Dosrstest.target=OpenRune Test - $slug" \
    -cp "$w\\rsprox\\launcher;$w\\rsprox\\lib\\*" OsrsTestLauncher) < /dev/null > /dev/null 2>&1 9>&- &   # nothing may hold the caller's pipe or the lock
  w=$!
  until tcp | grep -q "^$dp "; do   # the devtools endpoint answers, or the desktop died
    kill -0 "$w" 2>/dev/null || { echo "desktop $i exited" >&2; return 1; }; sleep 2
  done
  inst=desktop$i; dt=$dp
  pids=$( { marked "$i" | awk '$2=="OsrsTestLauncher"{print $1}'; tcp | awk -v p="$dp" '$1==p{print $2}'; } | paste -sd, -)
}
desktop_stop() {  # desktopN [pids]: the process tree of each recorded Windows PID, or with none every JVM of the instance; only JVMs carrying its marker
  local i=${1#desktop} m p t=${2:-}; need "$sys32/taskkill.exe" WINDOWS_SYSTEM32; need "$desk/jdk/bin/jps.exe" DESKTOP_DIR
  m=$(marked "$i" | cut -d' ' -f1)
  t=${t//,/ }; for p in ${t:-$m}; do grep -qx "$p" <<< "$m" && "$sys32/taskkill.exe" /F /T /PID "$p" > /dev/null 2>&1 || true; done
}

devtools_jar() {  # sets jar: the devtools classes jar of the client track's build of slug live (origin/HEAD of the client), cached by its T
  local c=${CLIENT_TRACK:-$HOME/osrs-plugins-reverse/.worktrees/harness-v8/tools/track.sh} tc
  tc=$(git -C "$(dirname "$c")" rev-parse "origin/HEAD^{commit}")
  flock "$root/state/client-live.lock" env -u T -u EVIDENCE HD_ROOT="$root" "$c" build live "$tc" >&2   # one build copy for every up
  jar=$root/evidence/live/$(git -C "$(dirname "$c")" rev-parse "$tc^{tree}").devtools-desktop.jar
  [ -f "$jar" ] || { echo "no desktop devtools jar $jar" >&2; return 1; }
}

tree() {  # TCOMMIT and T: the pass gives them; a dev's build snapshots the working copy and asks hd
  if [ -n "$arg" ]; then tc=$arg; T=${T:-$(git -C "$repo" rev-parse "$tc^{tree}")}; return; fi
  [ -d "$dev" ] || git -C "$repo" worktree add -q "$dev" -B "dev/$slug" "$(git -C "$repo" rev-parse -q --verify "fork/dev/$slug" || echo origin/main)"
  idx=$(git -C "$dev" rev-parse --git-path snapshot-index); rm -f "$idx"   # from HEAD: add -A alone drops tracked files under ignored paths
  GIT_INDEX_FILE=$idx git -C "$dev" read-tree HEAD && GIT_INDEX_FILE=$idx git -C "$dev" add -A . && snap=$(git -C "$dev" commit-tree "$(GIT_INDEX_FILE=$idx git -C "$dev" write-tree)" -p HEAD -m snapshot)
  read -r tc T cen < <(HD_ROOT=$root python3 "$root/hd.py" tree "$slug" "$snap"); CENTRAL=${CENTRAL:-$cen}
}
build() {
  tree
  if docker image inspect "or:$T" >/dev/null 2>&1; then echo "cached or:$T"; record true build <<< "cached"; return; fi
  if [ -d "$copy" ]; then git -C "$copy" reset -q --hard "$tc" && git -C "$copy" clean -fdq; else git -C "$repo" worktree add -q --detach "$copy" "$tc"; fi
  [ -f "$copy/game.yml" ] || cp "$copy/game.example.yml" "$copy/game.yml"
  ck=$(git -C "$copy" ls-tree -r "$tc" | grep -E 'or-cache/|/pack/|gamevals|raw-cache' | sha1sum | cut -c1-12)   # the cache's inputs
  log=$state/build-$T.log
  vols=(-v "gradle-$slug:/gradle" -v or-dl:/dl -v or-cache-store:/store); cdir=
  if [ -n "${CENTRAL:-}" ]; then  # the change's Central dependencies, merged by hd, built in (server/central.init.gradle)
    IFS=: read -r cpath ccommit <<< "$CENTRAL"; cdir=$cpath/.worktrees/$slug-central
    if [ -d "$cdir" ]; then git -C "$cdir" reset -q --hard "$ccommit" && git -C "$cdir" clean -fdq; else git -C "$cpath" worktree add -q --detach "$cdir" "$ccommit"; fi
    vols+=(-v "$cpath/.git:$cpath/.git:ro" -v "$cdir:$cdir" -v "$self/server/central.init.gradle:/central.init.gradle:ro")
  fi
  docker run --rm "${vols[@]}" eclipse-temurin:21 chown 1000:1000 /gradle /dl /store   # docker makes a new volume root's; the build runs as 1000
  docker run --rm --name "build-$slug" --cpus=4 --memory=5g -u 1000:1000 -e HOME=/gradle/home -e GRADLE_USER_HOME=/gradle -e CK="$ck" -e CDIR="$cdir" \
    "${vols[@]}" -v "$repo/.git:$repo/.git:ro" -v "$copy:$copy" -w "$copy" \
    eclipse-temurin:21 bash -c '
      set -e; mkdir -p "$HOME"; g="./gradlew --console=plain --build-cache --no-configuration-cache --max-workers=4 -Dorg.gradle.jvmargs=-Xmx1536m${CDIR:+ --init-script /central.init.gradle}"
      mkdir -p ~/.openrune/caches && cp -an /dl/. ~/.openrune/caches/ 2>/dev/null || true
      s=/store/s-$CK   # every output of the cache step: api/generated is compiled, the gamevals are read at boot
      if [ -d $s ]; then cp -a $s/. .; else
        $g :or-cache:freshCache :or-cache:mergePluginGamevals :or-cache:buildCache
        rm -rf $s.tmp && mkdir -p $s.tmp && cp -a --parents .data/cache .data/gamevals .data/gamevals-binary api/generated $s.tmp/ && mv $s.tmp $s
        for z in ~/.openrune/caches/*/*/*-disk.zip; do [ -e "$z" ] || continue; d=/dl/$(basename $(dirname $(dirname $z)))/$(basename $(dirname $z)); mkdir -p $d; cp -n $z $d/; done
      fi
      $g generateRsa :server:app:installDist -x test' > "$log" 2>&1 || { record false build < "$log"; echo "build failed: $log"; return 1; }
  st=$copy/.image; rm -rf "$st"; mkdir -p "$st/data"
  cp -a "$copy/server/app/build/install/app" "$st/server"; cp "$copy/game.yml" "$st/"
  cp -a "$copy/.data/." "$st/data/"; rm -rf "$st/data/client.key" "$st/data/game.key" "$st/data/postgres"
  mkdir -p "$state/key" && cp "$copy/.data/game.key" "$copy/.data/client.key" "$state/key/"
  docker build -q -f "$self/server/Dockerfile" -t "or:$T" "$st" >> "$log" 2>&1
  record true build < "$log"; echo "built or:$T"
}
up() {
  n=${arg:-1}; T=${T:-$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["T"])' "$state/build.json")}
  docker image inspect "or:$T" >/dev/null 2>&1 || { echo "no image or:$T: build first"; return 1; }
  devtools_jar   # the jar every desktop of this up loads
  game=$(port 0); http=$(port 1)
  down >/dev/null   # a previous up's desktops and containers
  docker run --rm --entrypoint cat "or:$T" /app/game.yml | quests "$(quest_mode)" > "$state/game.yml"   # upstream's default assume-completed skips every quest check
  [ -n "${RESUME:-}" ] || rm -rf "$state/postgres"; mkdir -p -m 700 "$state/postgres"   # fresh, or RESUME: the last down's (owner uid 1000, the server's)
  docker run -d --name "server-$slug" --cpus=4 --memory=4g -p "$game:43594" -v "$state/postgres:/app/.data/postgres" \
    -v "$state/key/game.key:/app/.data/game.key:ro" -v "$state/key/client.key:/app/.data/client.key:ro" -v "$state/game.yml:/app/game.yml:ro" "or:$T" >/dev/null
  docker run -d --name "http-$slug" -p "$http:8080" -v "$sidecar_data:/data:ro" -v "$self/server/jav_config.ws.template:/app/protocol/jav_config.ws.template:ro" \
    -e WORLD_HOST=127.0.0.1 -e HTTP_PUBLIC_PORT="$http" -e WORLD_ID=255 -e SAVE_DIR=/tmp/saves -e JAV_CONFIG_TEMPLATE=/app/protocol/jav_config.ws.template \
    -e LOCAL_AUTH=false -e PROOF_OF_WORK=false -e VALIDATE_CRCS=false osrs-rsps-test:241 >/dev/null
  ready "server-$slug"
  # Central seeds a dev realm (every login an administrator); the server reads its realm at boot: as live, then restarted
  if [ -n "$(sql "update realms set dev_mode = 0 where dev_mode <> 0 returning 1")" ]; then since=$(date -u +%FT%TZ); docker restart "server-$slug" >/dev/null; ready "server-$slug" "$since"; fi
  python3 "$self/server/javcheck.py" "http://127.0.0.1:$http/jav_config.ws" || { down; return 1; }   # no desktop is pointed at a foreign host
  modulus=$(openssl rsa -in "$state/key/game.key" -noout -modulus | cut -d= -f2 | tr A-F a-f)
  desktops=(); accounts=()
  for i in $(seq 1 "$n"); do
    desktop_start
    desktops+=("$inst:$dt:$pids"); accounts+=("$(account "$i")")
    printf '{"game": %s, "http": %s, "desktops": "%s", "accounts": "%s"}\n' "$game" "$http" "${desktops[*]}" "${accounts[*]}" > "$state/up.json"   # before the login: down stops exactly the desktops this up started
    flock -u 9   # listed: a later sweep sees this desktop owned
    python3 "$self/server/desktop_login.py" "$dt" "$(account "$i")" "$(account "$i")"      # creates the account on first login; prints LOGGED_IN or FAILED: reason
  done
  # members and rights are read at login and members written by the save at logout: log out, wait until no character of this up is online, update, log in
  for c in "${desktops[@]}"; do python3 "$self/server/desktop_login.py" "$(cut -d: -f2 <<< "$c")" logout; done
  offline
  sql "update account_characters set members = true where id in ($mine)" >/dev/null
  sql "update accounts set rights = case when lower(account_name) = '$(account "${STAFF:-0}")' then 'ADMINISTRATOR' else '' end where lower(account_name) like '$(account)%'" >/dev/null   # players as on live; STAFF=N: the Nth desktop's account an administrator
  for i in "${!desktops[@]}"; do python3 "$self/server/desktop_login.py" "$(cut -d: -f2 <<< "${desktops[$i]}")" "${accounts[$i]}" "${accounts[$i]}"; done
  echo "up: game $game http $http desktops ${desktops[*]}"
}
restart() {  # the server container restarted with its game.yml and database; the desktops of up logged in again
  since=$(date -u +%FT%TZ); docker restart "server-$slug" >/dev/null; ready "server-$slug" "$since"
  d=$(python3 -c 'import json,sys;d=json.load(open(sys.argv[1]));print(d["desktops"], "|", d["accounts"])' "$state/up.json")
  read -ra ds <<< "${d%%|*}"; read -ra as <<< "${d#*|}"
  for i in "${!ds[@]}"; do python3 "$self/server/desktop_login.py" "$(cut -d: -f2 <<< "${ds[$i]}")" "${as[$i]}" "${as[$i]}"; done
  echo "restarted: quest-requirements $(sed -n '/quest-requirements:/,/mode:/p' "$state/game.yml" | grep -o 'mode:.*')"
}
down() {
  for c in $(python3 -c 'import json,sys;d=json.load(open(sys.argv[1]));print(d.get("desktops", d.get("clients", "")))' "$state/up.json" 2>/dev/null); do
    (desktop_stop "$(cut -d: -f1 <<< "$c")" "$(cut -d: -f3 <<< "$c")") || true   # a missing program still lets the containers go
  done
  if [ "$(docker inspect -f '{{.State.Running}}' "server-$slug" 2>/dev/null)" = true ] && docker logs "server-$slug" 2>&1 | grep -q "Server ready in"; then offline; fi   # the server drops each disconnected character and saves it: RESUME starts there
  docker rm -f "server-$slug" "http-$slug" >/dev/null 2>&1 || true; rm -f "$state/up.json"; echo "down"
}
gate() {
  tc=$arg; build; git -C "$copy" reset -q --hard "$tc" && git -C "$copy" clean -fdq
  if out=$(HD_ROOT=$root python3 "$root/hd.py" rows "$slug" "$tc" "$copy" 2>&1); then record true gate <<< "$out"; else record false gate <<< "$out"; fi
}
gate_ci() {  # pushes T to gate/<slug>; a run of each workflow at that sha concludes green, or the row fails
  git -C "$repo" push -q --force fork "$TCOMMIT:refs/heads/gate/$slug"
  for wf in CI Formatting; do
    while :; do
      st=$(gh run list -R "$(git -C "$repo" remote get-url fork | sed -E 's#.*github.com[:/]##; s#\.git$##')" --branch "gate/$slug" --limit 20 \
             --json headSha,conclusion,status,workflowName -q ".[] | select(.headSha==\"$TCOMMIT\" and .workflowName==\"$wf\") | (.conclusion // \"\") + \"/\" + .status" | head -1)
      case $st in success/*) break ;; "") gh workflow run "$wf" -R "$(git -C "$repo" remote get-url fork | sed -E 's#.*github.com[:/]##; s#\.git$##')" --ref "gate/$slug"; sleep 30 ;;
        */completed) echo "$wf $st on gate/$slug at $TCOMMIT"; return 1 ;; *) sleep 30 ;; esac
    done
  done; echo "CI and Formatting green at $TCOMMIT"
}
ship() {  # the integration tree → the live test server
  slug=integration; state=$root/state/live; mkdir -p "$state"; copy=$repo/.worktrees/integration-build; arg=${arg:-$TCOMMIT}
  build
  cat > "$state/Dockerfile" <<EOF
FROM or:$T
COPY --from=$live_image --chown=1000:1000 /app/.data/game.key /app/.data/client.key /app/.data/
EOF
  docker build -q -f "$state/Dockerfile" -t "osrs-openrune:$T" "$state" >/dev/null
  a=$(docker run --rm --entrypoint sha256sum "$live_image" /app/.data/client.key | cut -c1-16); b=$(docker run --rm --entrypoint sha256sum "osrs-openrune:$T" /app/.data/client.key | cut -c1-16)
  [ "$a" = "$b" ] && echo "key hash: match" || { echo "key hash: MISMATCH"; return 4; }
  docker rm -f "boot-$T" >/dev/null 2>&1 || true
  docker run -d --name "boot-$T" --tmpfs /app/.data/postgres:uid=1000,gid=1000,mode=0700 "osrs-openrune:$T" >/dev/null
  ready "boot-$T" || { docker rm -f "boot-$T" >/dev/null; return 5; }; docker rm -f "boot-$T" >/dev/null
  prev=$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1])).get("tree","none"))' "$state/tree.json" 2>/dev/null || echo none)
  docker tag "$live_image" "osrs-openrune:rollback-${prev:0:12}"; docker tag "osrs-openrune:$T" "$live_image"
  since=$(date -u +%FT%TZ)
  (cd "$compose_dir" && docker compose --env-file switch.env up -d --no-deps openrune http http-desktop) >/dev/null
  if ready osrs-openrune "$since"; then
    python3 -c 'import json,sys;json.dump({"tree":sys.argv[1],"commit":sys.argv[2],"heads":sys.argv[3].split(),"names":sys.argv[4].split(),"rollback":sys.argv[5]},open(sys.argv[6],"w"))' \
      "$T" "$arg" "${HEADS:-}" "${NAMES:-}" "rollback-${prev:0:12}" "$state/tree.json"; echo "live: $T"
    for c in osrs-openrune-http osrs-openrune-http-desktop; do python3 "$self/server/javcheck.py" "http://127.0.0.1:$(docker port "$c" 8080 | head -1 | cut -d: -f2)/jav_config.ws" || return 6; done
  else
    docker tag "osrs-openrune:rollback-${prev:0:12}" "$live_image"; (cd "$compose_dir" && docker compose --env-file switch.env up -d --no-deps openrune) >/dev/null
    echo "deploy of $T failed: rolled back to ${prev:0:12}"; return 1
  fi
}
measure() {  # the goal's inventory: share of rows whose unit is in PASSES (hd: named by a live change with a passing verdict)
  python3 - "$root/harness/goals/server-content.json" "$state/measure.json" "${TREE:-}" "${PASSES:-}" <<'EOF'
import json, sys
rows, passes = json.load(open(sys.argv[1])), set(sys.argv[4].split())
value = 100.0 * sum(1 for r in rows if r["unit"] in passes) / len(rows) if rows else 0.0
json.dump({"tree": sys.argv[3], "value": value, "report": sys.argv[1]}, open(sys.argv[2], "w")); print(f"server-content: {value:g}")
EOF
}
log() { c=server-$slug; [ "$slug" = live ] && c=osrs-openrune; m=$state/log-mark; docker logs ${m:+--since "$(cat "$m" 2>/dev/null || echo 1h)"} "$c" 2>&1 | grep -E "${arg:-.}" || true; date -u +%FT%TZ > "$m"; }
recordings() { grep -lE "$arg" "${RSPROX:-$HOME/osrs-plugins-reverse/re/ref/rsprox/txt}"/*.txt 2>/dev/null | xargs -rn1 basename || echo "no transcript holds /$arg/"; }

case $verb in build|up|restart|down|gate|gate-ci|ship|measure|log|recordings) "${verb//-/_}" ;; *) echo "verbs: build up restart down gate gate-ci ship measure log recordings" >&2; exit 2 ;; esac
