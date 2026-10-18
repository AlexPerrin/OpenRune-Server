#!/bin/sh
# Recreates the `integration` branch: `main` plus every branch in integration.txt, merged in that
# order. integration.txt and the recorded conflict resolutions (scripts/integration-rr-cache/, git
# rerere) are read from the local/integration-tools branch, not from the working tree.
#
#   scripts/build-integration.sh [remote]   remote whose branches are merged (default: origin;
#                                           "." merges local branches)
#
# A conflict without a recorded resolution stops the build with a non-zero exit and lists the
# files. To record one: resolve it in the stopped merge, `git add` the files, `git commit`, then
# copy the new directories from .git/rr-cache/ into scripts/integration-rr-cache/ on
# local/integration-tools and commit them there.
set -eu
remote=${1:-origin}
if [ "$remote" = "." ]; then prefix=""; else prefix="$remote/"; git fetch -q "$remote"; fi
cd "$(git rev-parse --show-toplevel)"
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    echo "build-integration: the working tree has changes; commit or stash them first" >&2
    exit 1
fi
tools="${prefix}local/integration-tools"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
git show "$tools:integration.txt" > "$tmp/list"
gitdir=$(git rev-parse --git-dir)
mkdir -p "$gitdir/rr-cache"
if git cat-file -e "$tools:scripts/integration-rr-cache" 2>/dev/null; then
    git archive "$tools" scripts/integration-rr-cache | tar -x -C "$tmp"
    cp -R "$tmp/scripts/integration-rr-cache/." "$gitdir/rr-cache/"
fi
git checkout -q -B integration "${prefix}main"
grep -v '^[[:space:]]*\(#\|$\)' "$tmp/list" > "$tmp/branches"
while read -r branch; do
    if git -c rerere.enabled=true -c rerere.autoupdate=true \
        merge -q --no-ff --no-edit -m "Merge $branch into integration" "$prefix$branch" >/dev/null 2>&1; then
        echo "merged $branch"
        continue
    fi
    if ! git rev-parse -q --verify MERGE_HEAD >/dev/null; then
        echo "build-integration: merging $branch failed before any conflict (untracked files in" >&2
        echo "the way, or a missing branch?); run the merge by hand to see why." >&2
        exit 1
    fi
    unresolved=$(git diff --name-only --diff-filter=U)
    if [ -n "$unresolved" ]; then
        echo "build-integration: UNRECORDED CONFLICT merging $branch in:" >&2
        echo "$unresolved" | sed 's/^/  /' >&2
        echo "Resolve, git add, git commit, then store .git/rr-cache as described in this script." >&2
        exit 1
    fi
    git commit -q --no-edit
    echo "merged $branch (recorded resolution)"
done < "$tmp/branches"
echo "integration = $(git rev-parse --short HEAD)"
