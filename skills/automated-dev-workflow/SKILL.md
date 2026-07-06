---
name: automated-dev-workflow
description: Complete automated development workflow from issue to PR
---

# Automated Development Workflow Skill

## Overview

This skill provides a complete automated development workflow that integrates all other skills:
1. **issue-analyzer** - Analyze issue and generate implementation plan
2. **code-developer** - Develop code based on implementation plan
3. **code-reviewer** - Review code and provide feedback
4. **pr-creator** - Create and submit PR

## Prerequisites

- All prerequisite skills installed:
  - `issue-analyzer`
  - `code-developer`
  - `code-reviewer`
  - `pr-creator`
- GitHub token: `export GH_TOKEN=your_token`
- Java 8+ and Node.js installed
- Git configured

## Workflow Commands

### 1. Complete Workflow (Issue → PR)

```bash
# automated_workflow <issue_number> <repo> [branch_name]
# This is the main command that runs the complete workflow
function automated_workflow() {
  local issue_number=$1
  local repo=$2
  local branch_name=${3:-"feature/issue-$issue_number"}
  
  echo "🚀 Starting automated development workflow for issue #$issue_number..."
  echo "Repository: $repo"
  echo "Branch: $branch_name"
  echo ""
  
  # Step 1: Analyze Issue
  echo "📋 Step 1: Analyzing issue..."
  analyze_issue "$issue_number" "$repo"
  
  # Check if issue has complete design
  if issue_has_design "$issue_number" "$repo"; then
    echo "⏭️  Skipping development - issue already has complete design"
    return 0
  fi
  
  # Step 2: Develop Code
  echo ""
  echo "💻 Step 2: Developing code..."
  develop "$issue_number" "$repo" "Implementation plan generated above"
  
  # Step 3: Review Code
  echo ""
  echo "🔍 Step 3: Reviewing code..."
  review_pr "$issue_number" "$repo" "$(pwd)"
  
  # Step 4: Create PR
  echo ""
  echo "🔀 Step 4: Creating PR..."
  create_pr_from_issue "$issue_number" "$repo" "$branch_name"
  
  echo ""
  echo "✅ Automated workflow completed successfully!"
  echo "📄 Check the PR at: https://github.com/$repo/pull/$issue_number"
}
```

### 2. Partial Workflow (Select Steps)

```bash
# workflow_step <step> <issue_number> <repo> [params...]
# step: analyze, develop, review, create
function workflow_step() {
  local step=$1
  local issue_number=$2
  local repo=$3
  shift 3
  
  case $step in
    analyze)
      echo "📋 Analyzing issue..."
      analyze_issue "$issue_number" "$repo"
      ;;
    develop)
      echo "💻 Developing code..."
      develop "$issue_number" "$repo" "$@"
      ;;
    review)
      echo "🔍 Reviewing code..."
      review_pr "$issue_number" "$repo" "$(pwd)"
      ;;
    create)
      echo "🔀 Creating PR..."
      create_pr_from_issue "$issue_number" "$repo" "$@"
      ;;
    *)
      echo "Unknown step: $step"
      echo "Available steps: analyze, develop, review, create"
      return 1
      ;;
  esac
}
```

### 3. Workflow Status

```bash
# workflow_status <issue_number> <repo>
function workflow_status() {
  local issue_number=$1
  local repo=$2
  
  echo "📊 Workflow Status for Issue #$issue_number"
  echo "==========================================="
  
  # Check issue status
  echo ""
  echo "1. Issue Status:"
  check_issue_status "$issue_number" "$repo"
  
  # Check if has design
  echo ""
  echo "2. Design Status:"
  if issue_has_design "$issue_number" "$repo"; then
    echo "✅ Has complete design"
  else
    echo "❌ No design yet"
  fi
  
  # Check for existing PR
  echo ""
  echo "3. PR Status:"
  local branch="feature/issue-$issue_number"
  local prs=$(gh pr list --repo "$repo" --head "$branch" --json number,state,title --jq '.[0]' 2>/dev/null)
  
  if [ -n "$prs" ]; then
    echo "$prs" | jq .
  else
    echo "❌ No PR exists"
  fi
  
  # Check branch
  echo ""
  echo "4. Branch Status:"
  if git rev-parse --verify "origin/$branch" &>/dev/null; then
    echo "✅ Branch exists: $branch"
  else
    echo "❌ Branch does not exist: $branch"
  fi
}
```

### 4. Resume Workflow

```bash
# resume_workflow <issue_number> <repo> [current_step]
# current_step: analyze, develop, review, create
function resume_workflow() {
  local issue_number=$1
  local repo=$2
  local current_step=${3:-analyze}
  
  echo "🔄 Resuming workflow for issue #$issue_number..."
  
  case $current_step in
    analyze)
      workflow_step analyze "$issue_number" "$repo"
      workflow_step develop "$issue_number" "$repo"
      workflow_step review "$issue_number" "$repo" "$(pwd)"
      workflow_step create "$issue_number" "$repo"
      ;;
    develop)
      workflow_step develop "$issue_number" "$repo"
      workflow_step review "$issue_number" "$repo" "$(pwd)"
      workflow_step create "$issue_number" "$repo"
      ;;
    review)
      workflow_step review "$issue_number" "$repo" "$(pwd)"
      workflow_step create "$issue_number" "$repo"
      ;;
    create)
      workflow_step create "$issue_number" "$repo"
      ;;
    *)
      echo "Unknown step: $current_step"
      return 1
      ;;
  esac
}
```

## Usage Examples

### Complete Workflow

```bash
# Set up environment
export GH_TOKEN=your_github_token

# Run complete workflow from issue to PR
automated_workflow 276 ComposioHQ/awesome-claude-skills

# With custom branch name
automated_workflow 276 ComposioHQ/awesome-claude-skills my-feature-branch
```

### Partial Workflow

```bash
# Just analyze the issue
workflow_step analyze 276 ComposioHQ/awesome-claude-skills

# Just develop the code
workflow_step develop 276 ComposioHQ/awesome-claude-skills

# Just review the code
workflow_step review 276 ComposioHQ/awesome-claude-skills /path/to/project

# Just create PR
workflow_step create 276 ComposioHQ/awesome-claude-skills
```

### Check Status

```bash
# Check workflow status
workflow_status 276 ComposioHQ/awesome-claude-skills
```

### Resume Workflow

```bash
# Resume from a specific step
resume_workflow 276 ComposioHQ/awesome-claude-skills develop
```

## Integration with Other Skills

This workflow skill integrates:
- `issue-analyzer` - For issue analysis and planning
- `code-developer` - For code development
- `code-reviewer` - For code review
- `pr-creator` - For PR creation and submission

## Workflow Diagram

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   Issue     │────▶│   Analyze    │────▶│   Plan      │
│  #276       │     │   Issue      │     │   Created   │
└─────────────┘     └──────────────┘     └─────────────┘
                                           │
                                           ▼
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│     PR      │◀────│    Review    │◀────│   Develop   │
│   Created   │     │    Code      │     │   Code      │
└─────────────┘     └──────────────┘     └─────────────┘
       ▲                                      │
       │                                      ▼
       │                              ┌─────────────┐
       └──────────────────────────────│   Commit    │
                                      │   & Push    │
                                      └─────────────┘
```

## Best Practices

1. **Review before committing**: Always review the generated implementation plan
2. **Test frequently**: Run tests after each development step
3. **Keep PRs small**: Split large issues into multiple PRs if needed
4. **Document changes**: Update documentation as you develop
5. **Communicate**: Add comments to PRs explaining your approach
6. **Iterate**: Be prepared to iterate based on review feedback

## Error Handling

The workflow includes error handling at each step:
- If issue has complete design, skip development
- If PR already exists, notify and exit
- If tests fail, stop and report errors
- If review fails, request changes instead of creating PR

## Environment Variables

- `GH_TOKEN` - GitHub personal access token
- `PROJECT_ROOT` - Project root directory (optional)
- `DS_API_URL` - DolphinScheduler API URL (if using DS integration)
- `DS_TOKEN` - DolphinScheduler API token (if using DS integration)

## Tips

- Use `workflow_status` to check progress before running workflow
- Use `resume_workflow` to continue from where you left off
- Keep branches up to date with base branch before creating PR
- Add reviewers early to get feedback during development
