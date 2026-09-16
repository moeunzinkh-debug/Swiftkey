#!/usr/bin/env bash
set -euo pipefail

readonly script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly backmerge_script="$script_dir/backmerge-main-to-dev.sh"
readonly fixture_root="$(mktemp -d)"
readonly -a generated_files=(
  "CHANGELOG.md"
  "README.md"
  "gradle.properties"
  "patches-bundle.json"
  "patches-list.json"
)
# Files that dev does not track in the real repository (they are main's release metadata).
readonly -a main_only_files=(
  "CHANGELOG.md"
  "gradle.properties"
  "patches-bundle.json"
  "patches-list.json"
)

cleanup() {
  case "$fixture_root" in
    "${TMPDIR:-/tmp}"/tmp.*) rm -rf -- "$fixture_root" ;;
    *) echo "Refusing to remove unexpected fixture path: $fixture_root" >&2 ;;
  esac
}
trap cleanup EXIT

# Build a remote/seed/worker fixture and print the worker path.
#   $1 fixture name
#   $2 "with-metadata"    -> dev keeps its own copy of every generated file (content conflicts)
#      "without-metadata" -> dev dropped main's release metadata (modify/delete conflicts)
#      "diverged-source"  -> the above, plus dev rewrote a source file main also edits
#                            (the MicSupport.java shape that aborted the v1.5.0 back-merge)
make_fixture() {
  local name="$1" mode="$2"
  local root="$fixture_root/$name"
  local remote="$root/remote.git"
  local seed="$root/seed"
  local path

  mkdir -p "$root"
  git init --bare --quiet "$remote"
  git init --quiet "$seed"
  git -C "$seed" config user.name "Back-merge test"
  git -C "$seed" config user.email "backmerge-test@example.invalid"

  # Merge base: every generated file exists, exactly like the real repo at v1.0.0.
  for path in "${generated_files[@]}"; do
    printf 'base metadata\n' > "$seed/$path"
  done
  printf 'base lockfile\n' > "$seed/package-lock.json"
  printf 'shared\n' > "$seed/source.txt"
  git -C "$seed" add .
  git -C "$seed" commit --quiet -m "initial"
  local base
  base="$(git -C "$seed" rev-parse HEAD)"

  # dev side
  git -C "$seed" branch -M dev
  git -C "$seed" remote add origin "$remote"
  case "$mode" in
    with-metadata)
      for path in "${generated_files[@]}"; do
        printf 'dev metadata\n' > "$seed/$path"
      done
      git -C "$seed" add .
      git -C "$seed" commit --quiet -m "dev keeps its own metadata"
      ;;
    without-metadata)
      # dev intentionally does not carry main's release metadata; this is what makes the
      # merge report "modify/delete" conflicts for those files.
      git -C "$seed" rm --quiet -- "${main_only_files[@]}"
      printf 'dev readme\n' > "$seed/README.md"
      git -C "$seed" add .
      git -C "$seed" commit --quiet -m "dev drops release metadata"
      ;;
    diverged-source)
      # Same metadata shape, and dev additionally rewrote source.txt — main edits the old
      # copy below, so the merge reports a genuine content conflict on a source file.
      git -C "$seed" rm --quiet -- "${main_only_files[@]}"
      printf 'dev readme\n' > "$seed/README.md"
      printf 'dev rewrite\n' > "$seed/source.txt"
      git -C "$seed" add .
      git -C "$seed" commit --quiet -m "dev drops release metadata and rewrites source"
      ;;
    *)
      echo "Unknown fixture mode: $mode" >&2
      return 2
      ;;
  esac
  git -C "$seed" push --quiet --set-upstream origin dev

  # main side: release metadata rewritten by semantic-release, plus a real source change.
  git -C "$seed" switch --quiet -c main "$base"
  printf 'main source change\n' >> "$seed/source.txt"
  for path in "${generated_files[@]}"; do
    printf 'main metadata\n' > "$seed/$path"
  done
  printf 'main lockfile\n' > "$seed/package-lock.json"
  git -C "$seed" add .
  git -C "$seed" commit --quiet -m "main release"
  git -C "$seed" push --quiet --set-upstream origin main

  git clone --quiet --branch main "$remote" "$root/worker"
  printf '%s\n' "$root/worker"
}

# Assertions shared by every successful back-merge: main is merged in, real changes land,
# and lockfile follows main.
assert_merged() {
  local worker="$1"
  git -C "$worker" fetch --quiet origin main dev
  git -C "$worker" merge-base --is-ancestor origin/main origin/dev
  test "$(git -C "$worker" show origin/dev:package-lock.json)" = "main lockfile"
  test "$(git -C "$worker" show origin/dev:source.txt | tail -n 1)" = "main source change"
}

# Every fixture run below clears GITHUB_ACTIONS=: the script's git behaviour is identical
# either way, but its ::notice::/::error:: annotations would otherwise spam the CI run
# (and eat into GitHub's per-step annotation budget). The negative cases in particular
# are *supposed* to fail — their ::error:: must never paint a green run red.
# 1) dev keeps its own generated files -> content conflicts must resolve to dev's version.
worker="$(make_fixture with-metadata with-metadata)"
printf 'post-release regeneration\n' > "$worker/patches-list.json"
printf 'npm install residue\n' > "$worker/package-lock.json"
(
  cd "$worker"
  GITHUB_ACTIONS= BACKMERGE_REMOTE=origin bash "$backmerge_script"
)
assert_merged "$worker"
for path in "${generated_files[@]}"; do
  test "$(git -C "$worker" show "origin/dev:$path")" = "dev metadata"
done

# 2) The real repository shape: dev does not track CHANGELOG.md, gradle.properties,
#    patches-bundle.json or patches-list.json, so main's update conflicts as modify/delete.
worker="$(make_fixture without-metadata without-metadata)"
(
  cd "$worker"
  GITHUB_ACTIONS= BACKMERGE_REMOTE=origin bash "$backmerge_script"
)
assert_merged "$worker"
test "$(git -C "$worker" show origin/dev:README.md)" = "dev readme"
for path in "${main_only_files[@]}"; do
  if git -C "$worker" cat-file -e "origin/dev:$path" 2>/dev/null; then
    echo "Back-merge leaked main-only file $path into dev." >&2
    exit 1
  fi
  if [[ -e "$worker/$path" ]]; then
    echo "Back-merge left main-only file $path in the dev worktree." >&2
    exit 1
  fi
done

# 3) Same shape, but the worker still holds untracked build output (generatePatchesList on dev).
worker="$(make_fixture untracked-residue without-metadata)"
printf 'untracked generated output\n' > "$worker/patches-list.json"
(
  cd "$worker"
  GITHUB_ACTIONS= BACKMERGE_REMOTE=origin bash "$backmerge_script"
)
assert_merged "$worker"
if git -C "$worker" cat-file -e "origin/dev:patches-list.json" 2>/dev/null; then
  echo "Untracked residue was committed into dev." >&2
  exit 1
fi

# 4) A tracked source edit must never be discarded.
#    GITHUB_ACTIONS= is cleared for the negative cases below: they are *supposed* to fail,
#    and the script's ::error:: annotation would otherwise paint a green CI run red.
worker="$(make_fixture dirty without-metadata)"
printf 'unexpected source edit\n' >> "$worker/source.txt"
if (
  cd "$worker"
  GITHUB_ACTIONS= BACKMERGE_REMOTE=origin bash "$backmerge_script"
); then
  echo "Back-merge unexpectedly discarded a source change." >&2
  exit 1
fi
grep -q "unexpected source edit" "$worker/source.txt"

# 5) A source file dev rewrote while main edited the old copy, listed in the target-wins
#    allow-list: the back-merge completes and dev's implementation survives.
worker="$(make_fixture target-wins diverged-source)"
(
  cd "$worker"
  GITHUB_ACTIONS= BACKMERGE_REMOTE=origin BACKMERGE_TARGET_WINS=source.txt bash "$backmerge_script"
)
git -C "$worker" fetch --quiet origin main dev
git -C "$worker" merge-base --is-ancestor origin/main origin/dev
test "$(git -C "$worker" show origin/dev:package-lock.json)" = "main lockfile"
test "$(git -C "$worker" show origin/dev:source.txt)" = "dev rewrite"

# 6) The very same conflict WITHOUT the allow-list entry must still abort: an unreviewed
#    source conflict is a human decision, never something the script resolves by itself.
worker="$(make_fixture unexpected-source-conflict diverged-source)"
if (
  cd "$worker"
  GITHUB_ACTIONS= BACKMERGE_REMOTE=origin bash "$backmerge_script"
) 2>/dev/null; then
  echo "Back-merge silently resolved an unlisted source conflict." >&2
  exit 1
fi
git -C "$worker" fetch --quiet origin main dev
if git -C "$worker" merge-base --is-ancestor origin/main origin/dev; then
  echo "Aborted back-merge still moved dev." >&2
  exit 1
fi

echo "back-merge tests passed."
