#!/usr/bin/env bash
# tools/track.sh <verb> <slug> [arg]: the server track (docs/ARCHITECTURE.md of osrs-harness). Env from hd: SLUG HEAD T
# TCOMMIT BASE CLONE EVIDENCE PINS HD_ROOT CENTRAL G (HEADS NAMES for ship). build and tree are tools/build/build.sh's (the code a
# build runs, keyed alone), test, spotless, gate, gate-ci and ci-step tools/gate/gate.sh's (the code a gate runs). Verbs:
#   up [N]            server-<slug> and http-<slug> from or:<T> on the change's ports (quest mode: QUEST_MODE, else the change's front
#                     matter quest-mode, else respect-progress; or assume-completed), N desktops logged in,
#                     each loading the live client's devtools jar (CLIENT_TRACK: the client track's script, its build of slug live);
#                     a fresh database, or with RESUME=1 the accounts as the change's last down saved them; the realm as live
#                     (dev_mode off, xp at 1x), every account a member at rank 0, or with STAFF=N the Nth desktop's an administrator;
#                     idle logout off (live keeps it); each desktop logged in again after any logout until down (keep); a failed
#                     up takes down what it started
#   up token          up 1, the desktop's login a token login (auth type 7) for its account: token-<slug> (server/issuer.py) is the
#                     issuer of compact signed tokens whose secret game.yml's token-login block holds and jav_config param 11
#                     names (https: the client takes no other)
#   token [TOKEN]     the token the desktop's next login sends (none: a fresh valid one); server/issuer.py mint makes expired
#                     ones (--exp -5), another account's, and forged ones (another key DIR)
#   restart           logs up's desktops out, restarts server-<slug>, its game.yml and database kept, and logs them in again
#   down              stops the desktops, waits until the server has saved and dropped their characters, removes the containers (token-<slug> too)
#   ship TCOMMIT      the integration tree: build, the deploy image with the live key and the issuer's secret, boot check, deploy, or roll back
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
# a run reads its own copy of tools/, one per content in state/tools/<hash>/tools (exported: TOOLS_ROOT, its parent, where hd
# runs a gate's rows; TOOLS_SRC, the checkout): a ship of the tools branch moves the checkout under a running script, and hd's
# repairs remove a copy once no process or container uses it, and a .<hash>.<pid> being copied once its pid is gone
case $self in "$root"/state/tools/*) ;; *)
  k=$(cd "$self" && find . -type f ! -path '*/__pycache__/*' -print0 | sort -z | xargs -0 sha1sum | sha1sum | cut -c1-12); c=$root/state/tools/$k
  [ -d "$c" ] || { d=$root/state/tools/.$k.$$; rm -rf "$d"; mkdir -p "$d/tools"; cp -a "$self/." "$d/tools/"; mv -T "$d" "$c" 2>/dev/null || rm -rf "$d"; }
  TOOLS_SRC=$self TOOLS_ROOT=$c exec bash "$c/tools/track.sh" "$@" ;;
esac
case $verb in build|tree) exec "$self/build/build.sh" "$@" ;; test|spotless|gate|gate-ci|ci-step) exec "$self/gate/gate.sh" "$@" ;; esac
state=$root/state/$slug; mkdir -p "$state"
live_image=osrs-openrune:241; compose_dir=$self/server
export SIDECAR_DATA=${SIDECAR_DATA:-$HOME/src/openrune-work/server-tools/sidecar/data}; sidecar_data=$SIDECAR_DATA  # compose mounts ${SIDECAR_DATA}
atomic() { cat > "$1.$$.tmp"; if [ -s "$1.$$.tmp" ]; then mv -f "$1.$$.tmp" "$1"; else rm -f "$1.$$.tmp"; return 1; fi; }   # a state record whole or not at all: hd reads it at any moment
ready() {  # ready <container> [since]: blocks until "Server ready in" or the container stops
  while ! docker logs ${2:+--since "$2"} "$1" 2>&1 | grep -q "Server ready in"; do
    [ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" = true ] || { echo "$1 is not running"; return 1; }; sleep 5
  done
}
port() { echo $(( 20000 + 16#$(printf %s "$slug" | sha1sum | cut -c1-3) * 2 + $1 )); }   # game +0, http +1; shortcut: two slugs may hash alike, pick from up.json when it happens
quest_mode() { m=${QUEST_MODE:-$(awk '/^---$/{n++; next} n==1 && /^quest-mode:/{print $2}' "$root/harness/changes/$slug.md" 2>/dev/null)}; echo "${m:-respect-progress}"; }   # QUEST_MODE, else the change's front matter quest-mode, else respect-progress
idle_off() { sed -E 's/^([[:space:]]*idle-logout:[[:space:]]*).*/\1false/'; }   # stdin game.yml with idle logout off: a run's desktops idle while its agent reads (live keeps the image's); a tree without the key has no idle logout
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
keep() {  # keep <devtools port> <account>: from now until down, that desktop logged out (a disconnect, a restart, a logout) logs in again
  if [ -n "${tok:-}" ]; then set -- "$1" token; else set -- "$1" "$2" "$2"; fi
  setsid python3 "$self/server/desktop_login.py" "$1" keep "server-$slug" "${@:2}" < /dev/null >> "$state/keep-$1.log" 2>&1 & }
unkeep() { pkill -f "^python3 [^ ]*/desktop_login\.py [0-9]+ keep server-$slug " || true; }   # this slug's keepers stopped, by their command line (no pid file a reused pid could match): a logout that is meant stays out
offline() { until [ "$(sql "select count(*) from main.character_progress where online_central_world_id is not null and character_id in ($mine)")" = 0 ]; do sleep 1; done; }   # each character saved at its logout

sys32=${WINDOWS_SYSTEM32:-/mnt/c/Windows/System32}; desk=${DESKTOP_DIR:-/mnt/c/osrs-test}
need() { [ -x "$1" ] || { echo "$1 not found: set $2" >&2; exit 2; }; }   # need <program> <the variable that locates it>
# a loaded host's WSL interop can leave a Windows program hanging (accept4 failed 110): each listing ends, failed, and is asked again
tcp() { timeout 20 "$sys32/netstat.exe" -ano -p TCP | tr -d '\r' | awk '$4=="LISTENING"{n=split($2,a,":");print a[n], $5}'; }   # "<port> <Windows PID>" of each Windows listening socket
# RSProx binds jav_config on 43600+skip and proxies on 43701+skip; a bind in a range Windows reserves fails (WSL's mirrored
# networking reserves the Linux ephemeral range, which moves each boot; Windows' own excluded ranges): stdin "<lo> <hi>" per range
skip_base() { awk '{lo[NR] = $1; hi[NR] = $2} END {for (b = 0; 43751 + b <= 65535; b++) {ok = 1; for (r in lo) if (lo[r] <= 43751 + b && hi[r] >= 43600 + b) {ok = 0; b = hi[r] - 43600; break}; if (ok) {print b; exit}}; exit 1}'; }   # the least skip whose 51 instances' ports avoid every range
# a Windows process a run started through WSL interop outlives the run's Linux processes: each is recorded, its Windows pid
# and image, under the run's systemd slice (osrs-harness R5: hd ends them with the run); its down drops the record
winpid() { local sl; sl=$(grep -o 'hd-[^/]*\.slice' /proc/self/cgroup) || return 0; mkdir -p "$root/state/winpids/$sl" && echo "$2" > "$root/state/winpids/$sl/$1"; }
marked() { "$desk/jdk/bin/jps.exe" -v | tr -d '\r' | awk -v m="-Dosrstest.marker=osrs-test-run$1" '{for (f = 3; f <= NF; f++) if ($f == m) {print $1, $2; next}}'; }   # "<Windows PID> <main class>" of instance N's JVMs: only desktop_start sets the marker
# The desktops: each up creates its own instances. DESKTOP_DIR is the template (JDK, RSProx and its launcher, a staged
# home); instance N is the dir DESKTOP_DIR/run/N, claimed by its mkdir and naming its up's slug in run/N/owner, with its
# own home copied from the template's (devtools plugin: the live client's build), devtools port 7779+N and RSProx ports
# moved up by skip_base + N-1. Every JVM of instance N carries -Dosrstest.marker=osrs-test-runN;
# nothing without it is ever stopped (the user's own RuneLite). down stops and removes the instances its slug owns.
desktop_start() {  # sets inst dt (desktopN, its devtools port)
  local i d dp w t l busy ex base ep p
  need "$sys32/netstat.exe" WINDOWS_SYSTEM32; need "$sys32/netsh.exe" WINDOWS_SYSTEM32; need "$sys32/robocopy.exe" WINDOWS_SYSTEM32; need "$desk/jdk/bin/java.exe" DESKTOP_DIR; need "$desk/jdk/bin/jps.exe" DESKTOP_DIR; [ -f "$desk/home/.rlcustom/settings.properties" ] || { echo "no staged home in $desk/home" >&2; return 1; }
  mkdir -p "$desk/run"; until busy=$(tcp); do sleep 1; done; busy=$(cut -d' ' -f1 <<< "$busy")
  until ex=$(timeout 20 "$sys32/netsh.exe" interface ipv4 show excludedportrange protocol=tcp); do sleep 1; done
  base=$({ cat /proc/sys/net/ipv4/ip_local_port_range; tr -d '\r' <<< "$ex" | awk '$1 ~ /^[0-9]+$/ && $2 ~ /^[0-9]+$/ {print $1, $2}'; } | skip_base) || { echo "no RSProx ports outside the reserved ranges" >&2; return 1; }
  for i in $(seq 1 51); do d=$desk/run/$i; dp=$((7779 + i))
    grep -qx "$dp" <<< "$busy" || ! mkdir "$d" 2>/dev/null || break; d=   # mkdir is the claim: no two ups get one N
  done
  [ -n "$d" ] || { echo "no free desktop instance" >&2; return 1; }
  echo "$slug" > "$d/owner"; w=$(wslpath -w "$d")
  until [ -f "$d/home/.rlcustom/settings.properties" ]; do   # a WSL interop error can end robocopy before it copied (below 8): copied again until the home is there
    "$sys32/robocopy.exe" "$(wslpath -w "$desk/home")" "$w\\home" /E /MT:16 /NFL /NDL /NJH /NJS /NP /XD screenshots logs > /dev/null || [ $? -lt 8 ] || { echo "robocopy of the desktop home into $d failed" >&2; return 1; }   # robocopy: below 8 is success
  done
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
    kill -0 "$w" 2>/dev/null || { echo "desktop $i exited: $(grep -m1 -E 'REFUSING|Exception' "$state/desktop$i.log" || tail -3 "$state/desktop$i.log")" >&2; return 1; }
    for p in $(marked "$i" | cut -d' ' -f1); do winpid "$p" "java.exe"; done; sleep 2
  done
  for p in $(marked "$i" | cut -d' ' -f1); do winpid "$p" "java.exe"; done
  inst=desktop$i; dt=$dp
}
desktops_remove() {  # stops every JVM carrying the marker of an instance this slug owns, then removes the instance, its owner last (a failed down re-runs)
  local o d i p; need "$sys32/taskkill.exe" WINDOWS_SYSTEM32; need "$desk/jdk/bin/jps.exe" DESKTOP_DIR
  for o in "$desk"/run/*/owner; do
    [ -f "$o" ] && [ "$(cat "$o")" = "$slug" ] || continue; d=${o%/owner}; i=${d##*/}
    # jps can list a live JVM as "process information unavailable": a file it still holds keeps the loop stopping marked JVMs
    until [ -z "$(marked "$i")" ] && find "$d" -mindepth 1 -maxdepth 1 ! -name owner -exec rm -rf {} + 2>/dev/null; do
      for p in $(marked "$i" | cut -d' ' -f1); do "$sys32/taskkill.exe" /F /T /PID "$p" > /dev/null 2>&1 || true; rm -f "$root"/state/winpids/*/"$p"; done; sleep 1
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
  trap 'down >/dev/null 2>&1' EXIT   # a failed up leaves no half-up instance: its desktops and containers go
  docker run --rm --entrypoint cat "or:$T" /app/game.yml | quests "$(quest_mode)" | idle_off > "$state/game.yml"   # upstream's default assume-completed skips every quest check
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
  python3 "$self/server/javcheck.py" "http://127.0.0.1:$http/jav_config.ws"   # no desktop is pointed at a foreign host
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
  for i in "${!desktops[@]}"; do keep "$(cut -d: -f2 <<< "${desktops[$i]}")" "${accounts[$i]}"; done
  trap - EXIT; echo "up: game $game http $http desktops ${desktops[*]}${STAFF:+ administrator $(account "$STAFF")}"
}
restart() {  # the server container restarted with its game.yml and database; the desktops of up logged out first (a server dropping a client leaves the disconnected dialog, which no key closes) and logged in again
  d=$(python3 -c 'import json,sys;d=json.load(open(sys.argv[1]));print(d["desktops"], "|", d["accounts"])' "$state/up.json")
  tok=$(docker ps -q -f "name=^token-$slug\$"); read -ra ds <<< "${d%%|*}"; read -ra as <<< "${d#*|}"
  unkeep; for c in "${ds[@]}"; do python3 "$self/server/desktop_login.py" "$(cut -d: -f2 <<< "$c")" logout; done; offline
  since=$(date -u +%FT%TZ); docker restart "server-$slug" >/dev/null; ready "server-$slug" "$since"
  for i in "${!ds[@]}"; do login "$(cut -d: -f2 <<< "${ds[$i]}")" "${as[$i]}"; keep "$(cut -d: -f2 <<< "${ds[$i]}")" "${as[$i]}"; done
  echo "restarted: quest-requirements $(sed -n '/quest-requirements:/,/mode:/p' "$state/game.yml" | grep -o 'mode:.*')"
}
down() {
  unkeep   # no keeper logs in a desktop down stops
  (desktops_remove) || true   # a missing program still lets the containers go
  if [ "$(docker inspect -f '{{.State.Running}}' "server-$slug" 2>/dev/null)" = true ] && docker logs "server-$slug" 2>&1 | grep -q "Server ready in"; then offline; fi   # the server drops each disconnected character and saves it: RESUME starts there
  docker rm -f "server-$slug" "http-$slug" "token-$slug" >/dev/null 2>&1 || true; rm -f "$state/up.json"; echo "down"
}
ship() {  # the integration tree → the live test server; a failing step names itself in ship.log
  set -E; trap 'echo "ship of ${T:-$arg} failed: $BASH_COMMAND (exit $?)"' ERR
  arg=${arg:-$TCOMMIT}; "$self/build/build.sh" build integration "$arg"; T=${T:-$(git -C "$repo" rev-parse "$arg^{tree}")}
  slug=integration; state=$root/state/live; mkdir -p "$state"
  # the issuer (compose's ISSUER_DIR): its secret in the image's game.yml; the APK's sign-in and tokens for the tester account it has always played
  export ISSUER_DIR=$state/token; { docker run --rm --entrypoint cat "or:$T" /app/game.yml; echo; python3 "$self/server/issuer.py" key "$ISSUER_DIR"; } > "$state/game.yml"
  [ -s "$ISSUER_DIR/account" ] || echo tester > "$ISSUER_DIR/account"
  sed -E "s#^param=(11|20|22|28)=.*#param=\1=http://{{WORLD_HOST}}:${OPENRUNE_ISSUER_PORT:-8083}/#" "$self/server/jav_config.ws.template" > "$ISSUER_DIR/jav_config.ws.template"
  cat > "$state/Dockerfile" <<EOF
FROM or:$T
COPY --from=$live_image --chown=1000:1000 /app/.data/game.key /app/.data/client.key /app/.data/
COPY --chown=1000:1000 game.yml /app/game.yml
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
  (cd "$compose_dir" && docker compose --env-file switch.env up -d --no-deps openrune http http-desktop issuer) >/dev/null
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

case $verb in up|restart|down|ship|measure|log|recordings) "$verb" ;; token) token "$arg" ;;
  transcribe) transcribe "${@:3}" ;;
  *) echo "verbs: build up restart down test spotless gate gate-ci ci-step tree ship measure log recordings transcribe token" >&2; exit 2 ;; esac
