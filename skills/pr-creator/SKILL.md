---
name: pr-creator
description: Create and submit Pull Requests to GitHub
---

# PR Creator Skill

## Overview

This skill automates the process of creating and submitting Pull Requests to GitHub, including branch creation, commits, and PR description generation.

## Prerequisites

- GitHub CLI (`gh`) installed
- GitHub token: `export GH_TOKEN=your_token`
- Git configured with user.name and user.email

## Helper Functions

```bash
# Get GitHub token
function gh_get_token() {
  echo "${GH_TOKEN:-}"
}

# Check if already has PR
function has_existing_pr() {
  local branch=$1
  local repo=$2
  
  local prs=$(gh pr list --repo "$repo" --head "$branch" --json number,title --jq '.[0].number')
  
  if [ -n "$prs" ]; then
    echo "$prs"
    return 0
  fi
  return 1
}

# Create branch
function create_branch() {
  local branch_name=$1
  local base_branch=${2:-main}
  
  git checkout "$base_branch"
  git pull origin "$base_branch"
  git checkout -b "$branch_name"
  git push -u origin "$branch_name"
  
  echo "✅ Created and pushed branch: $branch_name"
}

# Stage and commit changes
function commit_changes() {
  local message=$1
  
  git add .
  git commit -m "$message"
  git push
  
  echo "✅ Committed and pushed: $message"
}

# Generate PR description from issue
function generate_pr_description() {
  local issue_number=$1
  local repo=$2
  
  local issue_body=$(gh issue view "$issue_number" --repo "$repo" --json body --jq '.body')
  
  cat << EOF
## Description
This PR implements the solution for issue #$issue_number.

## Changes Made
- Implemented core functionality as per implementation plan
- Added unit and integration tests
- Updated documentation
- Fixed any identified issues

## Testing
- [x] Unit tests pass
- [x] Integration tests pass
- [x] Manual testing completed
- [x] Code review completed

## Related Issues
Closes #$issue_number

## Screenshots (if applicable)
[Add screenshots here]

## Checklist
- [x] Code follows project conventions
- [x] Documentation is up to date
- [x] Tests are included and passing
- [x] No security vulnerabilities
- [x] Performance is acceptable

## Additional Notes
[Any additional information for reviewers]
EOF
}

# Create PR
function create_pr() {
  local title=$1
  local body=$2
  local head=$3
  local base=${4:-main}
  local repo=$5
  
  gh pr create \
    --repo "$repo" \
    --title "$title" \
    --body "$body" \
    --head "$head" \
    --base "$base" \
    --draft
    
  echo "✅ Created draft PR"
}

# Publish PR
function publish_pr() {
  local pr_number=$1
  local repo=$2
  
  gh pr ready "$pr_number" --repo "$repo"
  echo "✅ Published PR #$pr_number"
}

# Add reviewers
function add_reviewers() {
  local pr_number=$1
  local repo=$2
  shift 2
  local reviewers=$@
  
  gh pr edit "$pr_number" --repo "$repo" --reviewer "$reviewers"
  echo "✅ Added reviewers: $reviewers"
}

# Add labels
function add_labels() {
  local pr_number=$1
  local repo=$2
  shift 2
  local labels=$@
  
  gh pr edit "$pr_number" --repo "$repo" --add-label "$labels"
  echo "✅ Added labels: $labels"
}
```

## Commands

### 1. Create PR from issue

```bash
# create_pr_from_issue <issue_number> <repo> [branch_name]
function create_pr_from_issue() {
  local issue_number=$1
  local repo=$2
  local branch_name=${3:-"feature/issue-$issue_number"}
  
  echo "🚀 Creating PR for issue #$issue_number..."
  
  # Check if PR already exists
  local existing_pr=$(has_existing_pr "$branch_name" "$repo")
  if [ -n "$existing_pr" ]; then
    echo "⚠️  PR #$existing_pr already exists for branch $branch_name"
    echo "Open PR: https://github.com/$repo/pull/$existing_pr"
    return 1
  fi
  
  # Create branch
  create_branch "$branch_name"
  
  # Stage and commit changes
  commit_changes "feat: implement issue #$issue_number"
  
  # Generate PR description
  local description=$(generate_pr_description "$issue_number" "$repo")
  
  # Create PR
  local title="feat: Implement issue #$issue_number"
  create_pr "$title" "$description" "$branch_name" "$repo"
  
  # Get PR number
  local pr_number=$(gh pr list --repo "$repo" --head "$branch_name" --json number --jq '.[0].number')
  
  # Publish PR
  publish_pr "$pr_number" "$repo"
  
  # Add labels
  add_labels "$pr_number" "$repo" "enhancement" "good first issue"
  
  echo "✅ PR created: https://github.com/$repo/pull/$pr_number"
}
```

### 2. Quick PR creation

```bash
# quick_pr <title> <repo> [branch_name]
function quick_pr() {
  local title=$1
  local repo=$2
  local branch_name=${3:-"feature/$(date +%Y%m%d)-$(echo $title | tr ' ' '-')"}
  
  echo "🚀 Creating quick PR..."
  
  # Create branch
  create_branch "$branch_name"
  
  # Stage and commit
  git add .
  git commit -m "$title"
  git push
  
  # Create PR
  local body="Quick PR for: $title"
  create_pr "$title" "$body" "$branch_name" "$repo"
  
  local pr_number=$(gh pr list --repo "$repo" --head "$branch_name" --json number --jq '.[0].number')
  publish_pr "$pr_number" "$repo"
  
  echo "✅ PR created: https://github.com/$repo/pull/$pr_number"
}
```

### 3. Update existing PR

```bash
# update_pr <pr_number> <repo> [commit_message]
function update_pr() {
  local pr_number=$1
  local repo=$2
  local commit_message=${3:-"chore: update PR #$pr_number"}
  
  echo "🔄 Updating PR #$pr_number..."
  
  # Get branch name
  local branch=$(gh pr view "$pr_number" --repo "$repo" --json headRefName --jq '.headRefName')
  
  # Stage and commit
  git add .
  git commit -m "$commit_message"
  git push
  
  echo "✅ Updated PR #$pr_number"
}
```

### 4. Merge PR

```bash
# merge_pr <pr_number> <repo> [merge_method]
# merge_method: merge, rebase, squash
function merge_pr() {
  local pr_number=$1
  local repo=$2
  local merge_method=${3:-squash}
  
  echo "🔀 Merging PR #$pr_number..."
  
  gh pr merge "$pr_number" \
    --repo "$repo" \
    --$merge_method \
    --delete-branch
    
  echo "✅ Merged PR #$pr_number"
}
```

## Usage Example

```bash
# Set GitHub token
export GH_TOKEN=your_github_token

# Create PR from issue
create_pr_from_issue 276 ComposioHQ/awesome-claude-skills

# Quick PR creation
quick_pr "feat: add new feature" ComposioHQ/awesome-claude-skills

# Update existing PR
update_pr 277 ComposioHQ/awesome-claude-skills "fix: address review comments"

# Merge PR (after approval)
merge_pr 277 ComposioHQ/awesome-claude-skills squash
```

## Integration with Other Skills

This skill works with:
- `issue-analyzer` - To get issue details
- `code-developer` - After development is complete
- `code-reviewer` - After review is approved

## Best Practices

1. Use descriptive branch names (feature/issue-123)
2. Write clear commit messages
3. Keep PRs small and focused
4. Update PR description as changes are made
5. Request appropriate reviewers
6. Add relevant labels
7. Keep branch up to date with base branch
8. Delete branch after merge

## Workflow

```
1. Analyze Issue → issue-analyzer
2. Develop Code → code-developer
3. Review Code → code-reviewer
4. Create PR → pr-creator
5. Merge PR → pr-creator (merge_pr)
```

## Environment Variables

- `GH_TOKEN` - GitHub personal access token
- `PROJECT_ROOT` - Project root directory (optional)

## Tips

- Use `--draft` flag to create draft PRs for work in progress
- Add `--assignee` to assign PR to specific user
- Use `--milestone` to associate with milestone
- Keep PRs small (<400 lines) for easier review
