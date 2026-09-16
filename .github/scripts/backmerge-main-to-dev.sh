#!/usr/bin/env bash
set -euo pipefail

readonly remote="${BACKMERGE_REMOTE:-origin}"
readonly source_branch="${BACKMERGE_SOURCE_BRANCH:-main}"
readonly target_branch="${BACKMERGE_TARGET_BRANCH:-dev}"
readonly commit_message="${BACKMERGE_COMMIT_MESSAGE:-chore: back-merge main into dev [skip ci]}"
readonly -a generated_files=(
  "CHANGELOG.md"
  "README.md"
  "gradle.properties"
  "patches-bundle.json"
  "patches-list.json"
)
readonly -a pre_switch_files=(
  "${generated_files[@]}"
  "package-lock.json"
)
# Source files that the target branch (dev) has deliberately rewritten, so the source
# branch's (main's) edits to them are already superseded and must NOT be merged back.
#
# Real case (release v1.5.0, run 34983639229): dev replaced MicSupport.java with its own
# offline-voice router (extensions/.../voice/OfflineRecognitionService, whisper JNI, ...),
# while main edited the old implementation to drop the dead Vosk model download. Git reports
# a content conflict, the whole back-merge aborted with exit 1, and dev never received
# v1.4.4/v1.5.0. Keeping dev's file is the correct resolution here: dev's rewrite has no
# Vosk download at all, so main's fix is a no-op on dev, and main's copy would not even
# compile against dev's tree.
#
# This is an explicit, reviewed allow-list — anything NOT listed still aborts the merge so a
# human decides. Tests may point it at fixture paths via BACKMERGE_TARGET_WINS (space separated).
readonly default_target_wins="extensions/swiftkey/src/main/java/app/morphe/extension/swiftkey/MicSupport.java"
read -r -a target_wins_files <<< "${BACKMERGE_TARGET_WINS:-$default_target_wins}"

# A failing back-merge used to show up in the workflow UI as nothing but
# "Process completed with exit code 1"; the actual reason only lived in the raw log tail
# (that is how release v1.5.0 / run 34983639229 had to be diagnosed). Emit a GitHub
# annotation as well so the cause is visible directly on the run page.
annotate_error() {
  local message="$1"
  echo "$message" >&2
  if [[ -n "${GITHUB_ACTIONS:-}" ]]; then
    echo "::error title=back-merge ${source_branch} to ${target_branch}::${message}"
  fi
}

git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
# A CI checkout can be configured with a narrow fetch refspec (single-branch clone). Then
# `git fetch <remote> main dev` only fills FETCH_HEAD and never creates
# refs/remotes/<remote>/<target_branch>, so the switch below dies with
# "fatal: invalid reference: origin/dev". Force the wildcard refspec and fetch both
# branches with explicit destination refs so the remote-tracking refs always exist.
git config "remote.${remote}.fetch" "+refs/heads/*:refs/remotes/${remote}/*"
git fetch --prune "$remote" \
  "+refs/heads/${source_branch}:refs/remotes/${remote}/${source_branch}" \
  "+refs/heads/${target_branch}:refs/remotes/${remote}/${target_branch}"

# Print the subset of "$@" that exists in <tree> (default HEAD). `git restore`/`git checkout`
# fail hard on a pathspec that the tree does not know, so filter first.
paths_in_tree() {
  local tree="${1:?tree required}"
  shift
  local path
  for path in "$@"; do
    if git cat-file -e "${tree}:${path}" 2>/dev/null; then
      printf '%s\n' "$path"
    fi
  done
}

# The workflow can rewrite release metadata and package-lock.json after semantic-release commits.
# Clear only that known residue before switching branches, and refuse to discard source changes.
# package-lock.json is not in generated_files, so real lockfile changes still merge into dev below.
mapfile -t tracked_residue < <(paths_in_tree HEAD "${pre_switch_files[@]}")
if (( ${#tracked_residue[@]} > 0 )); then
  git restore --source=HEAD --staged --worktree -- "${tracked_residue[@]}"
fi
# Untracked leftovers of the same generated files (e.g. `patches-list.json` written by
# `./gradlew generatePatchesList` on a branch that does not track it) would otherwise make
# `git merge` abort with "untracked working tree files would be overwritten by merge".
for path in "${pre_switch_files[@]}"; do
  if [[ -e "$path" ]] && ! git ls-files --error-unmatch -- "$path" >/dev/null 2>&1; then
    echo "Removing untracked generated residue: $path"
    rm -f -- "$path"
  fi
done

if ! git diff --quiet || ! git diff --cached --quiet; then
  git status --short --untracked-files=no >&2
  annotate_error "Back-merge worktree has unexpected tracked changes: $(
    git status --short --untracked-files=no | tr '\n' ' '
  )— refusing to discard them."
  exit 1
fi

git switch --force-create "$target_branch" "$remote/$target_branch"

merge_status=0
git merge --no-commit --no-ff "$remote/$source_branch" || merge_status=$?

if ! git rev-parse --verify --quiet MERGE_HEAD >/dev/null; then
  if (( merge_status != 0 )); then
    annotate_error "Back-merge failed before a merge could start (git merge exit ${merge_status})."
    exit "$merge_status"
  fi

  echo "$target_branch already contains $source_branch."
  exit 0
fi

mapfile -t conflicts < <(git diff --name-only --diff-filter=U)
declare -A generated_file_set=()
for path in "${generated_files[@]}"; do
  generated_file_set["$path"]=1
done
declare -A target_wins_set=()
for path in ${target_wins_files[@]+"${target_wins_files[@]}"}; do
  target_wins_set["$path"]=1
done

unexpected_conflicts=()
for path in "${conflicts[@]}"; do
  if [[ -z "${generated_file_set[$path]+set}" && -z "${target_wins_set[$path]+set}" ]]; then
    unexpected_conflicts+=("$path")
  fi
done

if (( ${#unexpected_conflicts[@]} > 0 )); then
  printf '  %s\n' "${unexpected_conflicts[@]}" >&2
  annotate_error "Back-merge aborted: ${source_branch} and ${target_branch} both changed ${unexpected_conflicts[*]}. Resolve by hand, or add the path to the target-wins allow-list at the top of this script when ${target_branch} deliberately rewrote it."
  git merge --abort
  exit 1
fi

# These files must stay as the target branch has them, i.e. keep whatever <target_branch> says.
# Two groups share that outcome but for different reasons:
#   * generated_files  — each branch describes its own release metadata
#   * target_wins_files — dev intentionally rewrote them (see the allow-list at the top)
# The merge can conflict on them in two different shapes:
#   1. content conflict  — both branches have the file with different content
#   2. modify/delete     — <target_branch> removed the file while <source_branch> updated it
#      (this is the normal case for the release metadata: dev does not track CHANGELOG.md,
#       gradle.properties, patches-bundle.json or patches-list.json, so every release
#       conflicts this way)
# `git restore --source=HEAD` only handles shape 1 and dies on shape 2 with
# "error: path '<file>' is unmerged", so resolve each path against HEAD explicitly.
for path in "${conflicts[@]}"; do
  if [[ -n "${generated_file_set[$path]+set}" ]]; then
    reason="release metadata"
  elif [[ -n "${target_wins_set[$path]+set}" ]]; then
    reason="rewritten on ${target_branch}"
  else
    continue
  fi
  if git cat-file -e "HEAD:${path}" 2>/dev/null; then
    echo "Keeping ${target_branch} version of $path (${reason})"
    git checkout --quiet HEAD -- "$path"
  else
    echo "Keeping ${target_branch} deletion of $path (${reason})"
    git rm -f --quiet -- "$path"
  fi
done

mapfile -t unresolved < <(git diff --name-only --diff-filter=U)
if (( ${#unresolved[@]} > 0 )); then
  printf '  %s\n' "${unresolved[@]}" >&2
  annotate_error "Back-merge aborted: conflicts still unresolved after keeping the ${target_branch} side: ${unresolved[*]}."
  git merge --abort
  exit 1
fi

git commit -m "$commit_message"
git push "$remote" "$target_branch"
