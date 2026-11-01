#!/usr/bin/env bash
# tools/track.sh <verb> <slug> [arg]: the server track (docs/ARCHITECTURE.md of osrs-harness). Env from hd: SLUG HEAD T
# TCOMMIT BASE CLONE EVIDENCE PINS HD_ROOT CENTRAL (HEADS NAMES for ship). Verbs:
#   build [TCOMMIT]   the image or:<T> of the tree T (a dev: the working copy snapshotted, T from `hd tree`); cached by T;
#                     CENTRAL=<Central clone>:<commit> builds and tests with that Central in place of the published dev.or2 modules
#                     (server/central.init.gradle substitutes each, :central-app included)
#   up [N]            server-<slug> and http-<slug> from or:<T> on the change's ports (quest mode: QUEST_MODE, else the change's front
#                     matter quest-mode, else respect-progress; or assume-completed), N desktops logged in,
#                     each loading the live client's devtools jar (CLIENT_TRACK: the client track's script, its build of slug live);
#                     a fresh database, or with RESUME=1 the accounts as the change's last down saved them; the realm as live
#                     (dev_mode off, xp at 1x), every account a member at rank 0, or with STAFF=N the Nth desktop's an administrator
#   up token          up 1, the desktop's login a token login (auth type 7) for its account: token-<slug> (server/issuer.py) is the
#                     issuer of compact signed tokens whose secret game.yml's token-login block holds and jav_config param 11
#                     names (https: the client takes no other)
#   token [TOKEN]     the token the desktop's next login sends (none: a fresh valid one); server/issuer.py mint makes expired
#                     ones (--exp -5), another account's, and forged ones (another key DIR)
#   restart           restarts server-<slug>, its game.yml and database kept, and logs up's desktops in again
#   down              stops the desktops, waits until the server has saved and dropped their characters, removes the containers (token-<slug> too)
#   test [MODULE...]  build, then each module's tests and spotlessCheck on the build copy (no module: those holding a file
#                     the change's tree changes from origin/main); log evidence/<slug>/test.log, result state/<slug>/test.json
#   gate TCOMMIT      every check: and gate: row of the contract from the build copy (`hd rows`)
#   gate-ci           fork CI and Formatting green on gate/<slug> (blocks until both conclude) at a child of TCOMMIT whose
#                     workflows first move the runner's ephemeral ports above the game port 43594 (an outgoing socket on it
#                     fails the boot's bind), and with CENTRAL build that Central in, as build does (the change's head unchanged)
#   ship TCOMMIT      the integration tree: build, the deploy image with the live key, boot check, deploy, or roll back
#   measure GOAL      the content inventory's share of rows whose unit is in PASSES
#   log <slug> PATTERN  the lines of the change's server container matching PATTERN (slug `live`: the live server)
#   recordings <slug> REGEX  the RSProx transcripts holding a packet type or text (any case: CLANSETTINGS finds clansettings_delta)
#   transcribe <slug> [ID...]  rewrites those recordings' transcripts (none: every one) with every RSProx filter on (its defaults
#                     hide clan and friends-chat packets): RSPROX/../bin/<id>.bin (gzip) to RSPROX/<id>.txt, RSProx 1.0.5 in RSPROX_RUNTIME
# Windows locations, run through WSL interop (override in the environment):
#   WINDOWS_SYSTEM32  /mnt/c/Windows/System32   netstat.exe, taskkill.exe, robocopy.exe
#   DESKTOP_DIR       /mnt/c/osrs-test          the desktops' template; each up's instances are DESKTOP_DIR/run/N (jdk/bin/java.exe, jps.exe)
set -euo pipefail
verb=$1; slug=${2:-${SLUG:-}}; arg=${3:-}   # a contract row passes no slug: hd exports SLUG
self=$(cd "$(dirname "$0")" && pwd)
repo=${CLONE:-$HOME/src/OpenRune-Server}
root=${HD_ROOT:-$HOME/osrs-harness}
[ -f "$root/harness/changes/$slug.md" ] || [ -f "$root/harness/goals/$slug.md" ] || case $slug in live|integration) ;; *) echo "no change or goal \"$slug\": tools/track.sh <verb> <slug> [arg]" >&2; exit 2 ;; esac
state=$root/state/$slug; mkdir -p "$state"
dev=$repo/.worktrees/$slug; copy=$repo/.worktrees/$slug-build
live_image=osrs-openrune:241; compose_dir=$self/server
export SIDECAR_DATA=${SIDECAR_DATA:-$HOME/src/openrune-work/server-tools/sidecar/data}; sidecar_data=$SIDECAR_DATA  # compose mounts ${SIDECAR_DATA}
atomic() { cat > "$1.$$.tmp"; if [ -s "$1.$$.tmp" ]; then mv -f "$1.$$.tmp" "$1"; else rm -f "$1.$$.tmp"; return 1; fi; }   # a state record whole or not at all: hd reads it at any moment
record() { python3 -c 'import json,sys;print(json.dumps({"T":sys.argv[1],"ok":sys.argv[2]=="true","detail":sys.stdin.read()[-2000:]}))' "$T" "$1" | atomic "$state/$2.json"; }
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
login() {  # login <devtools port> <account>: logs that desktop in as the account (a FAILED login fails up and restart: one whose inventory widget does not show container 93 too); a lost key types another name, whose first login makes a stray account (the rights update then missed the real row): until this login made no row but the account's, out and in again
  local m; while :; do m=$(sql "select coalesce(max(id), 0) from accounts")
    if [ -n "${tok:-}" ]; then python3 "$self/server/desktop_login.py" "$1" token; else python3 "$self/server/desktop_login.py" "$1" "$2" "$2"; fi   # prints LOGGED_IN or FAILED: reason
    [ "$(sql "select string_agg(lower(account_name), ' ') from accounts where id > $m or lower(account_name) = '$2'")" = "$2" ] && return
    echo "login on $1 made another account than $2: logging in again"; python3 "$self/server/desktop_login.py" "$1" logout; done; }
offline() { until [ "$(sql "select count(*) from main.character_progress where online_central_world_id is not null and character_id in ($mine)")" = 0 ]; do sleep 1; done; }   # each character saved at its logout

sys32=${WINDOWS_SYSTEM32:-/mnt/c/Windows/System32}; desk=${DESKTOP_DIR:-/mnt/c/osrs-test}
need() { [ -x "$1" ] || { echo "$1 not found: set $2" >&2; exit 2; }; }   # need <program> <the variable that locates it>
# a loaded host's WSL interop can leave a Windows program hanging (accept4 failed 110): each listing ends, failed, and is asked again
tcp() { timeout 20 "$sys32/netstat.exe" -ano -p TCP | tr -d '\r' | awk '$4=="LISTENING"{n=split($2,a,":");print a[n], $5}'; }   # "<port> <Windows PID>" of each Windows listening socket
# RSProx binds jav_config on 43600+skip and proxies on 43701+skip; a bind in a range Windows reserves fails (WSL's mirrored
# networking reserves the Linux ephemeral range, which moves each boot; Windows' own excluded ranges): stdin "<lo> <hi>" per range
skip_base() { awk '{lo[NR] = $1; hi[NR] = $2} END {for (b = 0; 43751 + b <= 65535; b++) {ok = 1; for (r in lo) if (lo[r] <= 43751 + b && hi[r] >= 43600 + b) {ok = 0; b = hi[r] - 43600; break}; if (ok) {print b; exit}}; exit 1}'; }   # the least skip whose 51 instances' ports avoid every range
marked() { "$desk/jdk/bin/jps.exe" -v | tr -d '\r' | awk -v m="-Dosrstest.marker=osrs-test-run$1" '{for (f = 3; f <= NF; f++) if ($f == m) {print $1, $2; next}}'; }   # "<Windows PID> <main class>" of instance N's JVMs: only desktop_start sets the marker
# The desktops: each up creates its own instances. DESKTOP_DIR is the template (JDK, RSProx and its launcher, a staged
# home); instance N is the dir DESKTOP_DIR/run/N, claimed by its mkdir and naming its up's slug in run/N/owner, with its
# own home copied from the template's (devtools plugin: the live client's build), devtools port 7779+N and RSProx ports
# moved up by skip_base + N-1. Every JVM of instance N carries -Dosrstest.marker=osrs-test-runN;
# nothing without it is ever stopped (the user's own RuneLite). down stops and removes the instances its slug owns.
desktop_start() {  # sets inst dt (desktopN, its devtools port)
  local i d dp w t l busy ex base ep
  need "$sys32/netstat.exe" WINDOWS_SYSTEM32; need "$sys32/netsh.exe" WINDOWS_SYSTEM32; need "$sys32/robocopy.exe" WINDOWS_SYSTEM32; need "$desk/jdk/bin/java.exe" DESKTOP_DIR; need "$desk/jdk/bin/jps.exe" DESKTOP_DIR
  mkdir -p "$desk/run"; until busy=$(tcp); do sleep 1; done; busy=$(cut -d' ' -f1 <<< "$busy")
  until ex=$(timeout 20 "$sys32/netsh.exe" interface ipv4 show excludedportrange protocol=tcp); do sleep 1; done
  base=$({ cat /proc/sys/net/ipv4/ip_local_port_range; tr -d '\r' <<< "$ex" | awk '$1 ~ /^[0-9]+$/ && $2 ~ /^[0-9]+$/ {print $1, $2}'; } | skip_base) || { echo "no RSProx ports outside the reserved ranges" >&2; return 1; }
  for i in $(seq 1 51); do d=$desk/run/$i; dp=$((7779 + i))
    grep -qx "$dp" <<< "$busy" || ! mkdir "$d" 2>/dev/null || break; d=   # mkdir is the claim: no two ups get one N
  done
  [ -n "$d" ] || { echo "no free desktop instance" >&2; return 1; }
  echo "$slug" > "$d/owner"; w=$(wslpath -w "$d")
  "$sys32/robocopy.exe" "$(wslpath -w "$desk/home")" "$w\\home" /E /MT:16 /NFL /NDL /NJH /NJS /NP /XD screenshots logs > /dev/null || [ $? -lt 8 ]   # robocopy: below 8 is success
  sed -i "s/^openrunedevtools\.port=.*/openrunedevtools.port=$dp/" "$d"/home/.rlcustom/settings.properties "$d"/home/.rlcustom/profiles2/*.properties
  mkdir -p "$d/home/.rlcustom/sideloaded-plugins" && cp "$jar" "$d/home/.rlcustom/sideloaded-plugins/OpenRune-Developer-Tools.jar"
  ep=http://127.0.0.1:$http   # OPENRUNE_ENDPOINT: the launcher takes jav_config from it alone, and jav_config names every other endpoint
  printf '%s\n' "- name: OpenRune Test - $slug" "  jav_config_url: $ep/jav_config.ws" "  modulus: $modulus" "  revision: 241.1" \
    "  game_server_port: $game" "  runelite_bootstrap_commithash: e617bef1a7f5c39fbe8768994d27d638941efd5d" "  binary_folder: openrune-qa" > "$d/home/.rsprox/proxy-targets.yaml"
  t=$(wslpath -w "$desk"); l=launcher-$(sha1sum < "$self/server/OsrsTestLauncher.java" | cut -c1-12)   # this tools tree's launcher, compiled once into the template
  [ -f "$desk/rsprox/$l/OsrsTestLauncher.class" ] || { rm -rf "$desk/rsprox/$l.$$" && mkdir -p "$desk/rsprox/$l.$$" \
    && "$desk/jdk/bin/javac.exe" -nowarn -cp "$t\\rsprox\\lib\\*" -d "$t\\rsprox\\$l.$$" "$(wslpath -w "$self/server/OsrsTestLauncher.java")" >&2 \
    && { mv -T "$desk/rsprox/$l.$$" "$desk/rsprox/$l" 2>/dev/null || rm -rf "$desk/rsprox/$l.$$"; }; }   # a concurrent up may have placed it first
  jx=JAVA_TOOL_OPTIONS:OPENRUNE_ENDPOINT; jo=
  if [ -n "${tok:-}" ]; then  # the client's Jagex session variables; the JDK's CAs plus the issuer's certificate (RSProx fetches RuneLite over public https)
    jx+=:JX_SESSION_ID:JX_CHARACTER_ID:JX_DISPLAY_NAME; cp "$desk/jdk/lib/security/cacerts" "$d/home/token-trust.p12"; cp "$state/token/tls.pem" "$d/home/token-tls.pem"
    "$desk/jdk/bin/keytool.exe" -importcert -noprompt -alias harness-issuer -storepass changeit -keystore "$w\\home\\token-trust.p12" -file "$w\\home\\token-tls.pem" >/dev/null
    jo=" -Djavax.net.ssl.trustStore=$w\\home\\token-trust.p12 -Djavax.net.ssl.trustStorePassword=changeit"
  fi
  (cd "$d" && JAVA_TOOL_OPTIONS="-Duser.home=$w\\home -Dosrstest.marker=osrs-test-run$i$jo" WSLENV=${WSLENV:+$WSLENV:}$jx \
    OPENRUNE_ENDPOINT=$ep JX_SESSION_ID=harness JX_CHARACTER_ID=1 JX_DISPLAY_NAME="$(account 1)" \
    exec setsid "$desk/jdk/bin/java.exe" "-Dosrstest.marker=osrs-test-run$i" "-Dosrstest.portskip=$((base + i - 1))" "-Dosrstest.target=OpenRune Test - $slug" \
    "-Dosrstest.token=${tok:-false}" -cp "$t\\rsprox\\$l;$t\\rsprox\\lib\\*" OsrsTestLauncher) < /dev/null > "$state/desktop$i.log" 2>&1 &   # nothing may hold the caller's pipe
  w=$!
  until tcp | grep -q "^$dp "; do   # the devtools endpoint answers, or the desktop died
    kill -0 "$w" 2>/dev/null || { echo "desktop $i exited: $(grep -m1 -E 'REFUSING|Exception' "$state/desktop$i.log" || tail -3 "$state/desktop$i.log")" >&2; return 1; }; sleep 2
  done
  inst=desktop$i; dt=$dp
}
desktops_remove() {  # stops every JVM carrying the marker of an instance this slug owns, then removes the instance, its owner last (a failed down re-runs)
  local o d i p; need "$sys32/taskkill.exe" WINDOWS_SYSTEM32; need "$desk/jdk/bin/jps.exe" DESKTOP_DIR
  for o in "$desk"/run/*/owner; do
    [ -f "$o" ] && [ "$(cat "$o")" = "$slug" ] || continue; d=${o%/owner}; i=${d##*/}
    # jps can list a live JVM as "process information unavailable": a file it still holds keeps the loop stopping marked JVMs
    until [ -z "$(marked "$i")" ] && find "$d" -mindepth 1 -maxdepth 1 ! -name owner -exec rm -rf {} + 2>/dev/null; do
      for p in $(marked "$i" | cut -d' ' -f1); do "$sys32/taskkill.exe" /F /T /PID "$p" > /dev/null 2>&1 || true; done; sleep 1
    done
    rm -rf "$d"
  done
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
central() {  # the change's Central dependencies, merged by hd, built in (server/central.init.gradle): cdir and cvols for build and tests
  cdir= cvols=(); [ -n "${CENTRAL:-}" ] || return 0
  IFS=: read -r cpath ccommit <<< "$CENTRAL"; cdir=$cpath/.worktrees/$slug-central
  if [ -d "$cdir" ]; then git -C "$cdir" reset -q --hard "$ccommit" && git -C "$cdir" clean -fdq; else git -C "$cpath" worktree add -q --detach "$cdir" "$ccommit"; fi
  cvols=(-v "$cpath/.git:$cpath/.git:ro" -v "$cdir:$cdir" -v "$self/server/central.init.gradle:/central.init.gradle:ro")
}
build() {
  tree
  exec 9> "$state/build.lock"; flock 9   # one build of a slug at a time (the pass's and its dev's): the second waits, then reuses a cached or:<T>
  if docker image inspect "or:$T" >/dev/null 2>&1; then echo "cached or:$T"; record true build <<< "cached"; return; fi
  if [ -d "$copy" ]; then git -C "$copy" reset -q --hard "$tc" && git -C "$copy" clean -fdq; else git -C "$repo" worktree add -q --detach "$copy" "$tc"; fi
  [ -f "$copy/game.yml" ] || cp "$copy/game.example.yml" "$copy/game.yml"
  if grep -q '\.m2-rsprot' "$copy/settings.gradle.kts"; then  # local/android pins an rsprot published only to the clone's .m2-rsprot
    [ -d "$repo/.m2-rsprot" ] || { echo "no $repo/.m2-rsprot: the tree pins a locally published rsprot" | tee "$state/build-$T.log" | record false build; echo "build failed: $state/build-$T.log"; return 1; }
    cp -a "$repo/.m2-rsprot" "$copy/"
  fi
  ck=$(git -C "$copy" ls-tree -r "$tc" | grep -E 'or-cache/|/pack/|gamevals|raw-cache' | sha1sum | cut -c1-12)   # the cache's inputs
  log=$state/build-$T.log
  central; vols=(-v "gradle-$slug:/gradle" -v or-dl:/dl -v or-cache-store:/store "${cvols[@]}")
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
  docker build -q -f "$self/server/Dockerfile" -t "or:$T" "$st" >> "$log" 2>&1
  record true build < "$log"; echo "built or:$T"
}
keys() {  # the change's key pair in $state/key, made by up alone: whichever change built or:<T>, no key is copied from a build
  k=$state/key; [ -f "$k/game.key" ] && [ -f "$k/client.key" ] && return
  rm -rf "$k" 2>/dev/null || docker run --rm -v "$state:/s" postgres:16-alpine rm -rf /s/key; mkdir "$k"   # the root-owned directories docker made for missing mount sources go too
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:1024 -out "$k/game.key" 2>/dev/null   # PKCS#8, as the server's generateRsa writes
  printf 'Exponent: 10001\nModulus: %s' "$(openssl rsa -in "$k/game.key" -noout -modulus | cut -d= -f2 | tr A-F a-f)" > "$k/client.key"
}
up() {
  tok=; n=${arg:-1}; [ "$n" != token ] || { tok=true; n=1; }; T=${T:-$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["T"])' "$state/build.json")}
  docker image inspect "or:$T" >/dev/null 2>&1 || { echo "no image or:$T: build first"; return 1; }
  devtools_jar   # the jar every desktop of this up loads
  game=$(port 0); http=$(port 1)
  down >/dev/null   # a previous up's desktops and containers
  docker run --rm --entrypoint cat "or:$T" /app/game.yml | quests "$(quest_mode)" > "$state/game.yml"   # upstream's default assume-completed skips every quest check
  tpl=$self/server/jav_config.ws.template
  if [ -n "$tok" ]; then  # the issuer: game.yml trusts its key; param 11 sends the desktop's game-session calls to it
    python3 "$self/server/issuer.py" key "$state/token" >> "$state/game.yml"; token >/dev/null
    docker run -d --name "token-$slug" -p 8080 -v "$self/server/issuer.py:/issuer.py:ro" -v "$state/token:/t:ro" python:3.13-alpine python3 /issuer.py serve /t >/dev/null
    sed -E "s#^param=(11|22)=.*#param=\1=https://127.0.0.1:$(docker port "token-$slug" 8080 | head -1 | cut -d: -f2)/#" "$tpl" > "$state/jav_config.ws.template"; tpl=$state/jav_config.ws.template   # 11 Jagex auth, 22 RuneScape auth: the client casts both to https
  fi
  keys   # never a missing mount source: docker would make it a directory
  [ -n "${RESUME:-}" ] || rm -rf "$state/postgres"; mkdir -p -m 700 "$state/postgres"   # fresh, or RESUME: the last down's (owner uid 1000, the server's)
  docker run -d --name "server-$slug" --cpus=4 --memory=4g -p "$game:43594" -v "$state/postgres:/app/.data/postgres" \
    -v "$state/key/game.key:/app/.data/game.key:ro" -v "$state/key/client.key:/app/.data/client.key:ro" -v "$state/game.yml:/app/game.yml:ro" "or:$T" >/dev/null
  docker run -d --name "http-$slug" -p "$http:8080" -v "$sidecar_data:/data:ro" -v "$tpl:/app/protocol/jav_config.ws.template:ro" \
    -e WORLD_HOST=127.0.0.1 -e HTTP_PUBLIC_PORT="$http" -e WORLD_ID=255 -e SAVE_DIR=/tmp/saves -e JAV_CONFIG_TEMPLATE=/app/protocol/jav_config.ws.template \
    -e LOCAL_AUTH=false -e PROOF_OF_WORK=false -e VALIDATE_CRCS=false osrs-rsps-test:241 >/dev/null
  ready "server-$slug"
  # Central seeds a dev realm (every login an administrator, xp at 150x); the server reads its realm at boot: as live, then restarted
  if [ -n "$(sql "update realms set dev_mode = 0, player_xp_rate_in_hundreds = 100, global_xp_rate_in_hundreds = 100 where dev_mode <> 0 or player_xp_rate_in_hundreds <> 100 or global_xp_rate_in_hundreds <> 100 returning 1")" ]; then since=$(date -u +%FT%TZ); docker restart "server-$slug" >/dev/null; ready "server-$slug" "$since"; fi
  python3 "$self/server/javcheck.py" "http://127.0.0.1:$http/jav_config.ws" || { down; return 1; }   # no desktop is pointed at a foreign host
  modulus=$(openssl rsa -in "$state/key/game.key" -noout -modulus | cut -d= -f2 | tr A-F a-f)
  desktops=(); accounts=()
  for i in $(seq 1 "$n"); do
    desktop_start
    desktops+=("$inst:$dt"); accounts+=("$(account "$i")")
    printf '{"game": %s, "http": %s, "desktops": "%s", "accounts": "%s"}\n' "$game" "$http" "${desktops[*]}" "${accounts[*]}" | atomic "$state/up.json"   # before the login: restart and the devtools tools find each desktop
    login "$dt" "$(account "$i")"      # creates the account on first login; prints LOGGED_IN or FAILED: reason
  done
  # members and rights are read at login and members written by the save at logout: log out, wait until no character of this up is online, update, log in
  for c in "${desktops[@]}"; do python3 "$self/server/desktop_login.py" "$(cut -d: -f2 <<< "$c")" logout; done
  offline
  sql "update account_characters set members = true where id in ($mine)" >/dev/null
  rights="case when lower(account_name) = '$(account "${STAFF:-0}")' then 'ADMINISTRATOR' else '' end"   # players as on live; STAFF=N: the Nth desktop's account an administrator
  sql "update accounts set rights = $rights where lower(account_name) like '$(account)%'" >/dev/null
  for i in "${!desktops[@]}"; do login "$(cut -d: -f2 <<< "${desktops[$i]}")" "${accounts[$i]}"; done
  [ "$(sql "select count(*) from accounts where lower(account_name) like '$(account)%' and rights <> $rights")" = 0 ] || { echo "up: the accounts' rights did not hold after the login"; return 1; }   # read back once the logins ran: up fails rather than leave a cheat silently refused
  echo "up: game $game http $http desktops ${desktops[*]}${STAFF:+ administrator $(account "$STAFF")}"
}
restart() {  # the server container restarted with its game.yml and database; the desktops of up logged in again
  since=$(date -u +%FT%TZ); docker restart "server-$slug" >/dev/null; ready "server-$slug" "$since"
  d=$(python3 -c 'import json,sys;d=json.load(open(sys.argv[1]));print(d["desktops"], "|", d["accounts"])' "$state/up.json")
  tok=$(docker ps -q -f "name=^token-$slug\$"); read -ra ds <<< "${d%%|*}"; read -ra as <<< "${d#*|}"
  for i in "${!ds[@]}"; do login "$(cut -d: -f2 <<< "${ds[$i]}")" "${as[$i]}"; done
  echo "restarted: quest-requirements $(sed -n '/quest-requirements:/,/mode:/p' "$state/game.yml" | grep -o 'mode:.*')"
}
down() {
  (desktops_remove) || true   # a missing program still lets the containers go
  if [ "$(docker inspect -f '{{.State.Running}}' "server-$slug" 2>/dev/null)" = true ] && docker logs "server-$slug" 2>&1 | grep -q "Server ready in"; then offline; fi   # the server drops each disconnected character and saves it: RESUME starts there
  docker rm -f "server-$slug" "http-$slug" "token-$slug" >/dev/null 2>&1 || true; rm -f "$state/up.json"; echo "down"
}
projects() { local f d; for f; do d=$(dirname "$f"); while [ "$d" != . ] && [ ! -f "$copy/$d/build.gradle.kts" ]; do d=$(dirname "$d"); done; [ "$d" = . ] || { [ "${d##*/}" = pack ] && d=${d%/pack}/$(basename "${d%/pack}")-pack; echo ":${d//\//:}"; }; done | sort -u; }   # the Gradle projects holding these files: settings.gradle.kts names a pack directory <parent>-pack
projdir() { local d=${1#:}; d=${d//://}; [ "${d##*/}" = "$(basename "$(dirname "$d")")-pack" ] && d=$(dirname "$d")/pack; echo "$d"; }   # a project's directory
tests() {  # spotless's JGit cannot open a linked worktree: the container sees the copy's .git as a plain repo of T, its objects borrowed
  build; git -C "$copy" reset -q --hard "$tc" && git -C "$copy" clean -fdq
  local mods=("$@") f d g=$state/test.git out=${EVIDENCE:-$root/evidence/$slug}
  if [ ${#mods[@]} = 0 ]; then
    mapfile -t mods < <(projects $(git -C "$repo" diff --name-only "$(git -C "$repo" merge-base origin/main "$tc")" "$tc"))
  fi
  [ ${#mods[@]} -gt 0 ] || { echo "no module touched: tools/track.sh test <slug> MODULE..."; return 2; }
  echo "modules: ${mods[*]}"
  rm -rf "$g"; git init -q --bare "$g"; git --git-dir="$g" config core.bare false; git --git-dir="$g" config core.worktree "$copy"
  echo "$repo/.git/objects" > "$g/objects/info/alternates"
  git --git-dir="$g" update-ref refs/remotes/origin/main "$(git -C "$repo" rev-parse origin/main)"; git --git-dir="$g" update-ref --no-deref HEAD "$tc"
  git -C "$copy" update-index -q --refresh || true; cp "$(git -C "$copy" rev-parse --absolute-git-dir)/index" "$g/index"
  echo "gitdir: $g" > "$state/test.gitfile"; mkdir -p "$out"
  for d in "${mods[@]}"; do rm -rf "$copy/$(projdir "$d")/build/test-results/test"; done   # the report is this run's
  central; docker run --rm --name "test-$slug" --cpus=4 --memory=5g -u 1000:1000 -e HOME=/gradle/home -e GRADLE_USER_HOME=/gradle -e CDIR="$cdir" \
    -v "gradle-$slug:/gradle" -v or-dl:/dl "${cvols[@]}" -v "$repo/.git:$repo/.git:ro" -v "$g:$g:ro" -v "$copy:$copy" -v "$state/test.gitfile:$copy/.git:ro" -w "$copy" \
    eclipse-temurin:21 bash -c 'mkdir -p ~/.openrune/caches && cp -an /dl/. ~/.openrune/caches/ 2>/dev/null || true
      ./gradlew --console=plain --build-cache --no-configuration-cache --max-workers=4 -Dorg.gradle.jvmargs=-Xmx1536m${CDIR:+ --init-script /central.init.gradle} --continue "$@"' _ \
    $(printf '%s:test ' "${mods[@]}") $(printf '%s:spotlessCheck ' "${mods[@]}") > "$out/test.log" 2>&1 && ok=true || ok=false
  for d in "${mods[@]}"; do cat "$copy/$(projdir "$d")"/build/test-results/test/*.xml 2>/dev/null | grep -o '<testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' || true; done >> "$out/test.log"
  record "$ok" test < "$out/test.log"; grep -E -A1 '^<testsuite|went wrong|violations|FAILED|BUILD' "$out/test.log" | grep -v '^--$' || true; [ "$ok" = true ]
}
gate() {
  tc=$arg; build; git -C "$copy" reset -q --hard "$tc" && git -C "$copy" clean -fdq
  if out=$(HD_ROOT=$root python3 "$root/hd.py" rows "$slug" "$tc" "$copy" 2>&1); then record true gate <<< "$out"; else record false gate <<< "$out"; fi
}
gate_sha() {  # a child of TCOMMIT whose workflows move the ephemeral ports off the game port, and with CENTRAL hold that Central tree at .central/ built in
  set -e   # a command substitution does not inherit it
  d=$(mktemp -d); trap 'rm -rf "$d"' EXIT; idx=$d/index   # one index per run: gates of other changes run at once
  GIT_INDEX_FILE=$idx git -C "$repo" read-tree "$TCOMMIT"
  step='      - name: Ephemeral ports above the game port (tools/track.sh gate-ci)
        run: sysctl net.ipv4.ip_local_port_range; sudo sysctl -w net.ipv4.ip_local_port_range="49152 65535"'
  if [ -n "${CENTRAL:-}" ]; then
    IFS=: read -r cpath ccommit <<< "$CENTRAL"; git -C "$repo" fetch -q "$cpath" "$ccommit"
    GIT_INDEX_FILE=$idx git -C "$repo" read-tree --prefix=.central/ "$ccommit"
    GIT_INDEX_FILE=$idx git -C "$repo" update-index --add --cacheinfo "100644,$(git -C "$repo" hash-object -w "$self/server/central.init.gradle"),.central.init.gradle"
    step="$step"'
      - name: Central of the gate tree (tools/track.sh gate-ci)
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
gate_ci() {  # pushes T (gate_sha) to gate/<slug>; a run of each workflow at that sha concludes green, or the row fails
  sha=$(gate_sha); git -C "$repo" push -q --force fork "$sha:refs/heads/gate/$slug"
  for wf in CI Formatting; do
    while :; do
      st=$(gh run list -R "$(git -C "$repo" remote get-url fork | sed -E 's#.*github.com[:/]##; s#\.git$##')" --branch "gate/$slug" --limit 20 \
             --json headSha,conclusion,status,workflowName -q ".[] | select(.headSha==\"$sha\" and .workflowName==\"$wf\") | (.conclusion // \"\") + \"/\" + .status" | head -1)
      case $st in success/*) break ;; "") gh workflow run "$wf" -R "$(git -C "$repo" remote get-url fork | sed -E 's#.*github.com[:/]##; s#\.git$##')" --ref "gate/$slug"; sleep 30 ;;
        */completed) echo "$wf $st on gate/$slug at $sha (T $TCOMMIT)"; return 1 ;; *) sleep 30 ;; esac
    done
  done; echo "CI and Formatting green at $sha (T $TCOMMIT)"
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
    python3 -c 'import json,sys;print(json.dumps({"tree":sys.argv[1],"commit":sys.argv[2],"heads":sys.argv[3].split(),"names":sys.argv[4].split(),"rollback":sys.argv[5]}))' \
      "$T" "$arg" "${HEADS:-}" "${NAMES:-}" "rollback-${prev:0:12}" | atomic "$state/tree.json"; echo "live: $T"
    for c in osrs-openrune-http osrs-openrune-http-desktop; do python3 "$self/server/javcheck.py" "http://127.0.0.1:$(docker port "$c" 8080 | head -1 | cut -d: -f2)/jav_config.ws" || return 6; done
  else
    docker tag "osrs-openrune:rollback-${prev:0:12}" "$live_image"; (cd "$compose_dir" && docker compose --env-file switch.env up -d --no-deps openrune) >/dev/null
    echo "deploy of $T failed: rolled back to ${prev:0:12}"; return 1
  fi
}
measure() {  # the goal's inventory: share of rows whose unit is in PASSES (hd: named by a live change with a passing verdict)
  python3 - "$root/harness/goals/server-content.json" "$state/measure.json" "${TREE:-}" "${PASSES:-}" <<'EOF'
import json, os, sys
rows, passes = json.load(open(sys.argv[1])), set(sys.argv[4].split())
value = 100.0 * sum(1 for r in rows if r["unit"] in passes) / len(rows) if rows else 0.0
with open(sys.argv[2] + ".tmp", "w") as f: json.dump({"tree": sys.argv[3], "value": value, "report": sys.argv[1]}, f)
os.replace(sys.argv[2] + ".tmp", sys.argv[2]); print(f"server-content: {value:g}")
EOF
}
token() {  # [TOKEN]: the token the desktop's next login sends, or a valid one for the first desktop's account
  printf '%s\n' "${1:-$(python3 "$self/server/issuer.py" mint "$state/token" "$(account 1)")}" > "$state/token/token"; echo "token: $state/token/token"
}
log() {  # the container's whole log (up makes it fresh), never since an earlier call: a second search read only lines after the first
  c=server-$slug; [ "$slug" = live ] && c=osrs-openrune
  docker inspect "$c" > /dev/null 2>&1 || { echo "no container $c: up first" >&2; return 1; }   # no server is not a clean log
  docker logs "$c" 2>&1 | grep -E "${arg:-.}" || true; }
rsprox=${RSPROX:-$HOME/osrs-plugins-reverse/re/ref/rsprox/txt}
recordings() { grep -liE "$arg" "$rsprox"/*.txt 2>/dev/null | xargs -rn1 basename || echo "no transcript holds /$arg/"; }
transcribe() {  # a fresh RSProx home: one filter set, active, naming every PropertyFilter true; the index's settings and caches
  local cp=${RSPROX_RUNTIME:-$HOME/osrs-plugins-reverse/re/rsprox/build/osrs-test-runtime} h b id fail=0; h=$(mktemp -d); mkdir -p "$h/.rsprox/binary" "$h/.rsprox/filters"
  { printf 'version=0\ncreationtime=1\nname=All\nactive=true\n'
    javap -cp "$cp/shared-1.0.5.jar" net.rsprox.shared.filters.PropertyFilter | sed -nE 's/.*public static final net\.rsprox\.shared\.filters\.PropertyFilter ([A-Z0-9_]+);/\1=true/p'; } > "$h/.rsprox/filters/filters-all.txt"
  grep -q '=true' "$h/.rsprox/filters/filters-all.txt" || { echo "no PropertyFilter in $cp/shared-1.0.5.jar" >&2; rm -rf "$h"; return 1; }
  for d in settings caches; do [ -e "$rsprox/../home/.rsprox/$d" ] && ln -s "$(realpath "$rsprox/../home/.rsprox/$d")" "$h/.rsprox/$d"; done
  for b in $([ $# -gt 0 ] && printf "$rsprox/../bin/%s.bin " "$@" || echo "$rsprox"/../bin/*.bin); do id=$(basename "$b" .bin)
    gunzip -c "$b" > "$h/.rsprox/binary/$id.bin" 2>/dev/null || cp "$b" "$h/.rsprox/binary/$id.bin"
    java -Duser.home="$h" -cp "$cp/*" net.rsprox.proxy.cli.TranscribeCommandKt -name "$id" > "$h/$id.log" 2>&1 && [ -s "$h/.rsprox/binary/$id.txt" ] \
      && mv "$h/.rsprox/binary/$id.txt" "$rsprox/$id.txt" && echo "$id.txt" || { echo "$id: failed, $h/$id.log" >&2; fail=1; }
    rm -f "$h/.rsprox/binary/$id.bin"; done
  [ $fail = 1 ] || rm -rf "$h"; return $fail
}

case $verb in build|up|restart|down|gate|gate-ci|ship|measure|log|recordings) "${verb//-/_}" ;; token) token "$arg" ;; test) arg= tests "${@:3}" ;;
  transcribe) transcribe "${@:3}" ;;
  *) echo "verbs: build up restart down test gate gate-ci ship measure log recordings transcribe token" >&2; exit 2 ;; esac
