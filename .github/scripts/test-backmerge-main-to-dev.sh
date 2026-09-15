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
# Must stay identical to dev_owned_files in backmerge-main-to-dev.sh: the file where dev keeps
# its own offline-voice implementation while main ships the released one.
readonly dev_owned_file="extensions/swiftkey/src/main/java/app/morphe/extension/swiftkey/MicSupport.java"

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
#   $3 optional extra divergence, space separated:
#      "dev-owned"       -> dev and main both rewrite $dev_owned_file (dev keeps its own copy)
#      "source-conflict" -> dev and main both edit source.txt (must NOT be auto-resolved)
make_fixture() {
  local name="$1" mode="$2" extra="${3:-}"
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
  mkdir -p "$seed/$(dirname "$dev_owned_file")"
  printf 'base mic implementation\n' > "$seed/$dev_owned_file"
  git -C "$seed" add .
  git -C "$seed" commit --quiet -m "initial"
  local base
  base="$(git -C "$seed" rev-parse HEAD)"

  # dev side
  git -C "$seed" branch -M dev
  git -C "$seed" remote add origin "$remote"
  # Extra dev-side divergence is written before the case below so the branch's `git add .`
  # picks it up together with the metadata shape.
  if [[ "$extra" == *dev-owned* ]]; then
    printf 'dev mic implementation (offline voice)\n' > "$seed/$dev_owned_file"
  fi
  if [[ "$extra" == *source-conflict* ]]; then
    printf 'dev source change\n' >> "$seed/source.txt"
  fi
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
    *)
      echo "Unknown fixture mode: $mode" >&2
      return 2
      ;;
  esac
  git -C "$seed" push --quiet --set-upstream origin dev

  # main side: release metadata rewritten by semantic-release, plus a real source change.
  git -C "$seed" switch --quiet -c main "$base"
  printf 'main source change\n' >> "$seed/source.txt"
  if [[ "$extra" == *dev-owned* ]]; then
    printf 'main mic implementation (released)\n' > "$seed/$dev_owned_file"
  fi
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

# 1) dev keeps its own generated files -> content conflicts must resolve to dev's version.
worker="$(make_fixture with-metadata with-metadata)"
printf 'post-release regeneration\n' > "$worker/patches-list.json"
printf 'npm install residue\n' > "$worker/package-lock.json"
(
  cd "$worker"
  BACKMERGE_REMOTE=origin bash "$backmerge_script"
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
  BACKMERGE_REMOTE=origin bash "$backmerge_script"
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
  BACKMERGE_REMOTE=origin bash "$backmerge_script"
)
assert_merged "$worker"
if git -C "$worker" cat-file -e "origin/dev:patches-list.json" 2>/dev/null; then
  echo "Untracked residue was committed into dev." >&2
  exit 1
fi

# 4) A tracked source edit must never be discarded.
worker="$(make_fixture dirty without-metadata)"
printf 'unexpected source edit\n' >> "$worker/source.txt"
if (
  cd "$worker"
  BACKMERGE_REMOTE=origin bash "$backmerge_script"
); then
  echo "Back-merge unexpectedly discarded a source change." >&2
  exit 1
fi
grep -q "unexpected source edit" "$worker/source.txt"

# 5) The real v1.5.0 failure shape (Release run 34983639229): dev and main both rewrote
#    MicSupport.java in incompatible ways. It is listed in dev_owned_files, so the merge must
#    succeed and keep dev's own implementation instead of aborting the whole Release job.
worker="$(make_fixture dev-owned without-metadata dev-owned)"
(
  cd "$worker"
  BACKMERGE_REMOTE=origin bash "$backmerge_script"
)
assert_merged "$worker"
test "$(git -C "$worker" show "origin/dev:$dev_owned_file")" = "dev mic implementation (offline voice)"

# 6) A genuine source conflict that nobody declared must still stop the back-merge: silently
#    picking a side here would drop real work from one of the two branches.
worker="$(make_fixture source-conflict without-metadata source-conflict)"
if (
  cd "$worker"
  BACKMERGE_REMOTE=origin bash "$backmerge_script"
) > "$worker/../source-conflict.log" 2>&1; then
  echo "Back-merge silently resolved an undeclared source conflict." >&2
  exit 1
fi
grep -q "non-generated conflicts" "$worker/../source-conflict.log"
grep -q "source.txt" "$worker/../source-conflict.log"
# The failed merge must leave dev untouched on the remote.
git -C "$worker" fetch --quiet origin dev
if git -C "$worker" merge-base --is-ancestor origin/main origin/dev; then
  echo "Aborted back-merge still pushed main into dev." >&2
  exit 1
fi

echo "back-merge tests passed."
