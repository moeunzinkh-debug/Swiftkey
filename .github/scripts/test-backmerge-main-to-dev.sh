#!/usr/bin/env bash
set -euo pipefail

readonly script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly backmerge_script="$script_dir/backmerge-main-to-dev.sh"
readonly fixture_root="$(mktemp -d)"
readonly remote="$fixture_root/remote.git"
readonly seed="$fixture_root/seed"
readonly worker="$fixture_root/worker"
readonly dirty_worker="$fixture_root/dirty-worker"
readonly -a generated_files=(
  "CHANGELOG.md"
  "README.md"
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

git init --bare --quiet "$remote"
git init --quiet "$seed"
git -C "$seed" config user.name "Back-merge test"
git -C "$seed" config user.email "backmerge-test@example.invalid"

for path in "${generated_files[@]}"; do
  printf 'dev metadata\n' > "$seed/$path"
done
printf 'dev lockfile\n' > "$seed/package-lock.json"
printf 'shared\n' > "$seed/source.txt"
git -C "$seed" add .
git -C "$seed" commit --quiet -m "initial dev"
git -C "$seed" branch -M dev
git -C "$seed" remote add origin "$remote"
git -C "$seed" push --quiet --set-upstream origin dev

git -C "$seed" switch --quiet -c main
printf 'main source change\n' >> "$seed/source.txt"
for path in "${generated_files[@]}"; do
  printf 'main metadata\n' > "$seed/$path"
done
printf 'main lockfile\n' > "$seed/package-lock.json"
git -C "$seed" add .
git -C "$seed" commit --quiet -m "main release"
git -C "$seed" push --quiet --set-upstream origin main

git clone --quiet --branch main "$remote" "$worker"
printf 'post-release regeneration\n' > "$worker/patches-list.json"
printf 'npm install residue\n' > "$worker/package-lock.json"
(
  cd "$worker"
  BACKMERGE_REMOTE=origin bash "$backmerge_script"
)

git -C "$worker" fetch --quiet origin main dev
git -C "$worker" merge-base --is-ancestor origin/main origin/dev
test "$(git -C "$worker" show origin/dev:patches-list.json)" = "dev metadata"
test "$(git -C "$worker" show origin/dev:package-lock.json)" = "main lockfile"
test "$(git -C "$worker" show origin/dev:source.txt | tail -n 1)" = "main source change"

git clone --quiet --branch main "$remote" "$dirty_worker"
printf 'unexpected source edit\n' >> "$dirty_worker/source.txt"
if (
  cd "$dirty_worker"
  BACKMERGE_REMOTE=origin bash "$backmerge_script"
); then
  echo "Back-merge unexpectedly discarded a source change." >&2
  exit 1
fi
grep -q "unexpected source edit" "$dirty_worker/source.txt"

# A target branch that has never published a release does not track the generated files at
# all, so the back-merge meets modify/delete conflicts on them. They must stay deleted while
# the source change and the dev-owned README still land.
readonly untracked_remote="$fixture_root/untracked-remote.git"
readonly untracked_seed="$fixture_root/untracked-seed"
readonly untracked_worker="$fixture_root/untracked-worker"

git init --bare --quiet "$untracked_remote"
git init --quiet "$untracked_seed"
git -C "$untracked_seed" config user.name "Back-merge test"
git -C "$untracked_seed" config user.email "backmerge-test@example.invalid"

printf 'shared\n' > "$untracked_seed/source.txt"
printf 'dev readme\n' > "$untracked_seed/README.md"
printf 'dev lockfile\n' > "$untracked_seed/package-lock.json"
git -C "$untracked_seed" add .
git -C "$untracked_seed" commit --quiet -m "initial dev without release metadata"
git -C "$untracked_seed" branch -M dev
git -C "$untracked_seed" remote add origin "$untracked_remote"
git -C "$untracked_seed" push --quiet --set-upstream origin dev

git -C "$untracked_seed" switch --quiet -c main
printf 'main source change\n' >> "$untracked_seed/source.txt"
for path in "${generated_files[@]}"; do
  printf 'main metadata\n' > "$untracked_seed/$path"
done
git -C "$untracked_seed" add .
git -C "$untracked_seed" commit --quiet -m "main release"
git -C "$untracked_seed" push --quiet --set-upstream origin main

git clone --quiet --branch main "$untracked_remote" "$untracked_worker"
(
  cd "$untracked_worker"
  BACKMERGE_REMOTE=origin bash "$backmerge_script"
)

git -C "$untracked_worker" fetch --quiet origin main dev
git -C "$untracked_worker" merge-base --is-ancestor origin/main origin/dev
test "$(git -C "$untracked_worker" show origin/dev:source.txt | tail -n 1)" = "main source change"
test "$(git -C "$untracked_worker" show origin/dev:README.md)" = "dev readme"
for path in "CHANGELOG.md" "gradle.properties" "patches-bundle.json" "patches-list.json"; do
  if git -C "$untracked_worker" cat-file -e "origin/dev:$path" 2>/dev/null; then
    echo "Back-merge re-added $path, which the target branch does not track." >&2
    exit 1
  fi
done

echo "back-merge tests passed"
