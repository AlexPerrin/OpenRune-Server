#!/usr/bin/env bash
# tools/track.sh <verb> <slug> [arg]: the server track (docs/ARCHITECTURE.md of osrs-harness). Env from hd: SLUG HEAD T
# TCOMMIT BASE CLONE EVIDENCE PINS HD_ROOT (HEADS NAMES for ship). Verbs:
#   build [TCOMMIT]   the image or:<T> of the tree T (a dev: the working copy snapshotted, T from `hd tree`); cached by T
#   up [N]            server-<slug> and http-<slug> from or:<T> on the change's ports, N desktop clients logged in
#   down              stops the clients, removes the containers
#   gate TCOMMIT      every check: and gate: row of the contract from the build copy (`hd rows`)
#   gate-ci           fork CI and Formatting green on gate/<slug> at TCOMMIT (blocks until both conclude)
#   ship TCOMMIT      the integration tree: build, the deploy image with the live key, boot check, deploy, or roll back
#   measure GOAL      the content inventory's share of passing rows
#   log PATTERN       the change's server log since the run's marker (slug `live`: the live server)
#   recordings REGEX  the RSProx transcripts holding a packet type or text
set -euo pipefail
verb=$1; slug=${2:-${SLUG:-}}; arg=${3:-}   # a contract row passes no slug: hd exports SLUG
self=$(cd "$(dirname "$0")" && pwd)
repo=${CLONE:-$HOME/src/OpenRune-Server}
root=${HD_ROOT:-$HOME/osrs-harness}
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

tree() {  # TCOMMIT and T: the pass gives them; a dev's build snapshots the working copy and asks hd
  if [ -n "$arg" ]; then tc=$arg; T=${T:-$(git -C "$repo" rev-parse "$tc^{tree}")}; return; fi
  [ -d "$dev" ] || git -C "$repo" worktree add -q "$dev" -B "dev/$slug" "$(git -C "$repo" rev-parse -q --verify "fork/dev/$slug" || echo origin/main)"
  idx=$(git -C "$dev" rev-parse --git-path snapshot-index); rm -f "$idx"   # from HEAD: add -A alone drops tracked files under ignored paths
  GIT_INDEX_FILE=$idx git -C "$dev" read-tree HEAD && GIT_INDEX_FILE=$idx git -C "$dev" add -A . && snap=$(git -C "$dev" commit-tree "$(GIT_INDEX_FILE=$idx git -C "$dev" write-tree)" -p HEAD -m snapshot)
  read -r tc T < <(HD_ROOT=$root python3 "$root/hd.py" tree "$slug" "$snap")
}
build() {
  tree
  if docker image inspect "or:$T" >/dev/null 2>&1; then echo "cached or:$T"; record true build <<< "cached"; return; fi
  if [ -d "$copy" ]; then git -C "$copy" reset -q --hard "$tc" && git -C "$copy" clean -fdq; else git -C "$repo" worktree add -q --detach "$copy" "$tc"; fi
  [ -f "$copy/game.yml" ] || cp "$copy/game.example.yml" "$copy/game.yml"
  ck=$(git -C "$copy" ls-tree -r "$tc" | grep -E 'or-cache/|/pack/|gamevals|raw-cache' | sha1sum | cut -c1-12)   # the cache's inputs
  log=$state/build-$T.log
  vols=(-v "gradle-$slug:/gradle" -v or-dl:/dl -v or-cache-store:/store)
  docker run --rm "${vols[@]}" eclipse-temurin:21 chown 1000:1000 /gradle /dl /store   # docker makes a new volume root's; the build runs as 1000
  docker run --rm --name "build-$slug" --cpus=4 --memory=5g -u 1000:1000 -e HOME=/gradle/home -e GRADLE_USER_HOME=/gradle -e CK="$ck" \
    "${vols[@]}" -v "$repo/.git:$repo/.git:ro" -v "$copy:$copy" -w "$copy" \
    eclipse-temurin:21 bash -c '
      set -e; mkdir -p "$HOME"; g="./gradlew --console=plain --build-cache --no-configuration-cache --max-workers=4 -Dorg.gradle.jvmargs=-Xmx1536m"
      mkdir -p ~/.openrune/caches && cp -an /dl/. ~/.openrune/caches/ 2>/dev/null || true
      if [ -d /store/$CK ]; then cp -a /store/$CK/. .data/cache/; else
        $g :or-cache:freshCache :or-cache:mergePluginGamevals :or-cache:buildCache
        mkdir -p /store/$CK && cp -a .data/cache/. /store/$CK/
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
  game=$(port 0); http=$(port 1)
  docker rm -f "server-$slug" "http-$slug" >/dev/null 2>&1 || true
  docker run -d --name "server-$slug" --cpus=4 --memory=4g -p "$game:43594" --tmpfs /app/.data/postgres:uid=1000,gid=1000,mode=0700 \
    -v "$state/key/game.key:/app/.data/game.key:ro" -v "$state/key/client.key:/app/.data/client.key:ro" "or:$T" >/dev/null
  docker run --rm --entrypoint cat osrs-rsps-test:241 /app/protocol/jav_config.ws.template \
    | sed -E "s#https?://[A-Za-z0-9.-]*(jagex|runescape)\.com[^[:space:]]*#http://127.0.0.1:$http/#g" > "$state/jav_config.ws.template"
  docker run -d --name "http-$slug" -p "$http:8080" -v "$sidecar_data:/data:ro" -v "$state/jav_config.ws.template:/app/protocol/jav_config.ws.template:ro" \
    -e WORLD_HOST=127.0.0.1 -e HTTP_PUBLIC_PORT="$http" -e WORLD_ID=255 -e SAVE_DIR=/tmp/saves -e JAV_CONFIG_TEMPLATE=/app/protocol/jav_config.ws.template \
    -e LOCAL_AUTH=false -e PROOF_OF_WORK=false -e VALIDATE_CRCS=false osrs-rsps-test:241 >/dev/null
  ready "server-$slug"
  modulus=$(openssl rsa -in "$state/key/game.key" -noout -modulus | cut -d= -f2 | tr A-F a-f)
  clients=()
  for i in $(seq 1 "$n"); do
    inst=$(powershell.exe -NoProfile -File "$(wslpath -w "$self/server/desktop.ps1")" start -Title "$slug" -JavConfig "http://127.0.0.1:$http/jav_config.ws" -Modulus "$modulus" -GamePort "$game" | tr -d '\r')
    dt=${inst##* }; inst=${inst%% *}
    python3 "$self/server/desktop_login.py" "$dt" "$slug$i" "$slug$i"      # creates the account on first login; prints LOGGED_IN or FAILED: reason
    clients+=("$inst:$dt")
  done
  pg=$(docker logs "server-$slug" 2>&1 | grep -oE 'jdbc:postgresql://localhost:[0-9]+' | tail -1 | grep -oE '[0-9]+$')
  docker run --rm --network "container:server-$slug" postgres:16-alpine psql -h 127.0.0.1 -p "$pg" -U postgres -d postgres -q -c \
    "update account_characters set members = true where account_id in (select id from accounts where lower(account_name) like '$slug%')" >/dev/null
  for c in "${clients[@]}"; do python3 "$self/server/desktop_login.py" "${c##*:}" relog; done   # members applies at login
  printf '{"game": %s, "http": %s, "clients": "%s"}\n' "$game" "$http" "${clients[*]}" > "$state/up.json"
  echo "up: game $game http $http clients ${clients[*]}"
}
down() {
  for c in $(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["clients"])' "$state/up.json" 2>/dev/null); do
    powershell.exe -NoProfile -File "$(wslpath -w "$self/server/desktop.ps1")" stop -Instance "${c%%:*}" >/dev/null || true
  done
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
  else
    docker tag "osrs-openrune:rollback-${prev:0:12}" "$live_image"; (cd "$compose_dir" && docker compose --env-file switch.env up -d --no-deps openrune) >/dev/null
    echo "deploy of $T failed: rolled back to ${prev:0:12}"; return 1
  fi
}
measure() {  # the goal's inventory: share of rows passing
  python3 - "$root/harness/goals/server-content.json" "$state/measure.json" "${TREE:-}" <<'EOF'
import json, sys
rows = json.load(open(sys.argv[1])); value = 100.0 * sum(1 for r in rows if r.get("passes")) / len(rows) if rows else 0.0
json.dump({"tree": sys.argv[3], "value": value, "report": sys.argv[1]}, open(sys.argv[2], "w")); print(f"server-content: {value:g}")
EOF
}
log() { c=server-$slug; [ "$slug" = live ] && c=osrs-openrune; m=$state/log-mark; docker logs ${m:+--since "$(cat "$m" 2>/dev/null || echo 1h)"} "$c" 2>&1 | grep -E "${arg:-.}" || true; date -u +%FT%TZ > "$m"; }
recordings() { grep -lE "$arg" "${RSPROX:-$HOME/osrs-plugins-reverse/re/ref/rsprox/txt}"/*.txt 2>/dev/null | xargs -rn1 basename || echo "no transcript holds /$arg/"; }

case $verb in build|up|down|gate|gate-ci|ship|measure|log|recordings) "${verb//-/_}" ;; *) echo "verbs: build up down gate gate-ci ship measure log recordings" >&2; exit 2 ;; esac
