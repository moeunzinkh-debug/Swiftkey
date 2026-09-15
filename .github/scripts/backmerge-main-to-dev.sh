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
# ឯកសារកូដដែល dev រក្សាសាខាផ្ទាល់ខ្លួនដាច់ដោយឡែកពី main (dev-owned)។
#
# ហេតុអ្វីត្រូវមានបញ្ជីនេះ៖ Release run 34983639229 (v1.5.0) បានបរាជ័យត្រង់ step
# "Back-merge main into dev" ព្រោះ dev និង main បានកែ `MicSupport.java` ទាំងសងខាង
# តាមទិសដៅផ្សេងគ្នា —
#   • dev  (cc4675d "feat: add text editing and on-demand offline voice")៖ សរសេរឡើងវិញ
#     ទាំងស្រុង (~95 បន្ទាត់) ដើម្បី route សំឡេងទៅ OfflineRecognitionService របស់ខ្លួន
#   • main (93e5203 "fix(swiftkey): stop the dead Vosk model download…")៖ រក្សាឯកសារពេញ
#     (~311 បន្ទាត់) ហើយគ្រាន់តែដកការទាញយក Vosk model ចេញ
# ភាគីទាំងពីរដោះស្រាយបញ្ហាដូចគ្នាតាមរបៀបខុសគ្នា ដូច្នេះ git រាយការណ៍ content conflict
# ហើយស្គ្រីបចាស់ឈប់ដោយ "non-generated conflicts" → ទាំង job Release ក្រហម ទោះបី
# release/attestation/artifact ចេញរួចរាល់ហើយក៏ដោយ។
#
# គោលការណ៍៖ ឯកសារទាំងនេះត្រូវបានដោះស្រាយដូច generated files ដែរ — រក្សាកំណែរបស់
# <target_branch> (dev) — ព្រោះ dev គឺជាសាខាពិសោធន៍ដែលមានស្ថាបត្យកម្មផ្ទាល់ខ្លួន។
# ការរក្សាកំណែ dev មិនបាត់បង់អ្វីទេ៖ គោលបំណងនៃ fix របស់ main (កុំទាញយក model ស្ងាត់ៗ)
# មានរួចហើយនៅក្នុងការសរសេរឡើងវិញរបស់ dev ដែលមិនមានកូដ download ទាល់តែសោះ។
# ⚠️ កុំបន្ថែមឯកសារណាដែល dev មិនបានបំបែកខ្លួនពិតប្រាកដ — វានឹងលុបកំណែថ្មីរបស់ main ចោល។
readonly -a dev_owned_files=(
  "extensions/swiftkey/src/main/java/app/morphe/extension/swiftkey/MicSupport.java"
)

git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git fetch "$remote" "$source_branch" "$target_branch"

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
  echo "Back-merge worktree has unexpected tracked changes:" >&2
  git status --short --untracked-files=no >&2
  exit 1
fi

git switch --force-create "$target_branch" "$remote/$target_branch"

merge_status=0
git merge --no-commit --no-ff "$remote/$source_branch" || merge_status=$?

if ! git rev-parse --verify --quiet MERGE_HEAD >/dev/null; then
  if (( merge_status != 0 )); then
    echo "Back-merge failed before a merge could start." >&2
    exit "$merge_status"
  fi

  echo "$target_branch already contains $source_branch."
  exit 0
fi

mapfile -t conflicts < <(git diff --name-only --diff-filter=U)
# ឯកសារដែលត្រូវដោះស្រាយដោយរក្សាភាគី <target_branch> (dev)៖
#   • generated_files — ឯកសារ release metadata របស់សាខានីមួយៗ
#   • dev_owned_files — ឯកសារកូដដែល dev បំបែកខ្លួនដោយចេតនា (មើលពន្យល់ខាងលើ)
declare -A keep_target_set=()
for path in "${generated_files[@]}" "${dev_owned_files[@]}"; do
  keep_target_set["$path"]=1
done

unexpected_conflicts=()
for path in "${conflicts[@]}"; do
  if [[ -z "${keep_target_set[$path]+set}" ]]; then
    unexpected_conflicts+=("$path")
  fi
done

if (( ${#unexpected_conflicts[@]} > 0 )); then
  printf 'Back-merge has non-generated conflicts:\n' >&2
  printf '  %s\n' "${unexpected_conflicts[@]}" >&2
  # ណែនាំឲ្យអ្នកថែសាខាដឹងថាត្រូវធ្វើអ្វីបន្ទាប់ — release បានចេញរួចហើយ គ្រាន់តែ
  # dev នៅតែ behind។ ដោះស្រាយដោយដៃម្ដង រួច back-merge ស្វ័យប្រវត្តិនឹងដំណើរការឡើងវិញ។
  cat >&2 <<EOF

These files were changed on BOTH $source_branch and $target_branch, so the bot cannot pick a
side safely. Resolve them once by hand (release artifacts are already published):

  git switch $target_branch && git merge $source_branch   # ឬបើក PR $source_branch → $target_branch
  # កែ conflict ដោយដៃ រួច commit + push

If $target_branch deliberately owns one of these files (its own implementation that must not be
overwritten by $source_branch), add the path to dev_owned_files in
.github/scripts/backmerge-main-to-dev.sh — វានឹងរក្សាកំណែរបស់ $target_branch ដោយស្វ័យប្រវត្តិ។
EOF
  git merge --abort
  exit 1
fi

# These files describe each branch's own release and must stay on the target branch, i.e. keep
# whatever <target_branch> says. The merge can conflict on them in two different shapes:
#   1. content conflict  — both branches have the file with different generated content
#   2. modify/delete     — <target_branch> removed the file while <source_branch> updated it
#      (this is the normal case here: dev does not track CHANGELOG.md, gradle.properties,
#       patches-bundle.json or patches-list.json, so every release conflicts this way)
# `git restore --source=HEAD` only handles shape 1 and dies on shape 2 with
# "error: path '<file>' is unmerged", so resolve each path against HEAD explicitly.
for path in "${conflicts[@]}"; do
  [[ -n "${keep_target_set[$path]+set}" ]] || continue
  if git cat-file -e "HEAD:${path}" 2>/dev/null; then
    echo "Keeping ${target_branch} version of $path"
    git checkout --quiet HEAD -- "$path"
  else
    echo "Keeping ${target_branch} deletion of $path"
    git rm -f --quiet -- "$path"
  fi
done

mapfile -t unresolved < <(git diff --name-only --diff-filter=U)
if (( ${#unresolved[@]} > 0 )); then
  printf 'Back-merge still has unresolved conflicts:\n' >&2
  printf '  %s\n' "${unresolved[@]}" >&2
  git merge --abort
  exit 1
fi

git commit -m "$commit_message"
git push "$remote" "$target_branch"
