---
name: issue-analyzer
description: Analyze GitHub issues and generate implementation plans
---

# Issue Analyzer Skill

## Overview

This skill analyzes GitHub issues and generates detailed implementation plans. It checks if the issue already has a complete design and skips development if so.

## Prerequisites

- GitHub CLI (`gh`) must be installed
- GitHub token configured: `export GH_TOKEN=your_token`

## Helper Functions

```bash
# Get GitHub token
function gh_get_token() {
  echo "${GH_TOKEN:-}"
}

# Check if issue has complete design
function issue_has_design() {
  local issue_number=$1
  local repo=$2
  local body=$(gh issue view "$issue_number" --repo "$repo" --json body --jq '.body')
  
  # Check for keywords indicating complete design
  if echo "$body" | grep -qiE "(design|architecture|specification|detailed plan|implementation plan)"; then
    return 0  # Has design
  fi
  return 1  # No design
}

# Get issue details
function get_issue_details() {
  local issue_number=$1
  local repo=$2
  gh issue view "$issue_number" --repo "$repo" --json number,title,body,labels,state,createdAt --jq '{number, title, body, labels: [.labels[].name], state, createdAt}'
}

# Generate implementation plan
function generate_plan() {
  local issue_number=$1
  local repo=$2
  local body=$3
  
  cat << EOF
# Implementation Plan for Issue #$issue_number

## Issue Title
$(echo "$body" | head -1)

## Problem Statement
$(echo "$body" | sed -n '2,10p')

## Implementation Steps
1. **Analysis Phase**
   - Review existing codebase
   - Identify affected components
   - Document current behavior

2. **Design Phase**
   - Create architecture diagram
   - Define API contracts
   - Plan database changes

3. **Development Phase**
   - Implement core functionality
   - Write unit tests
   - Add integration tests

4. **Testing Phase**
   - Run full test suite
   - Perform manual testing
   - Document edge cases

5. **Deployment Phase**
   - Update documentation
   - Create migration scripts
   - Deploy to staging

## Technical Requirements
- Language: Java/Spring Boot (backend), Vue.js (frontend)
- Database: PostgreSQL
- Testing: JUnit, Jest
- CI/CD: GitHub Actions

## Estimated Effort
- Analysis: 2 hours
- Design: 4 hours
- Development: 16 hours
- Testing: 8 hours
- Documentation: 4 hours
- **Total: ~34 hours**

## Risks and Mitigations
- **Risk**: Breaking existing functionality
  **Mitigation**: Comprehensive test coverage, gradual rollout

- **Risk**: Performance degradation
  **Mitigation**: Load testing, performance monitoring

## Next Steps
1. Review this plan with team
2. Create feature branch
3. Start implementation
EOF
}
```

## Commands

### 1. Analyze an issue

```bash
# analyze_issue <issue_number> <repo>
# Example: analyze_issue 276 ComposioHQ/awesome-claude-skills
function analyze_issue() {
  local issue_number=$1
  local repo=$2
  local token=$(gh_get_token)
  
  if [ -z "$token" ]; then
    echo "Error: GH_TOKEN not set. Please export your GitHub token."
    return 1
  fi
  
  # Get issue details
  local details=$(get_issue_details "$issue_number" "$repo")
  local body=$(echo "$details" | jq -r '.body')
  
  # Check if has complete design
  if issue_has_design "$issue_number" "$repo"; then
    echo "✅ Issue #$issue_number already has a complete design. Skipping implementation."
    echo "$details"
    return 0
  fi
  
  # Generate implementation plan
  echo "📋 Generating implementation plan for issue #$issue_number..."
  generate_plan "$issue_number" "$repo" "$body"
}
```

### 2. Check issue status

```bash
# check_issue_status <issue_number> <repo>
function check_issue_status() {
  local issue_number=$1
  local repo=$2
  local token=$(gh_get_token)
  
  gh issue view "$issue_number" --repo "$repo" --json state,title,labels,body --jq '{state, title, labels: [.labels[].name]}'
}
```

## Usage Example

```bash
# Set GitHub token
export GH_TOKEN=your_github_token

# Analyze issue #276
analyze_issue 276 ComposioHQ/awesome-claude-skills

# Check issue status
check_issue_status 276 ComposioHQ/awesome-claude-skills
```

## Integration with Other Skills

This skill works with:
- `code-developer` - For implementing the plan
- `code-reviewer` - For code review
- `pr-creator` - For creating PRs

## Best Practices

1. Always review generated plans before implementation
2. Update plans if requirements change during development
3. Document any deviations from the original plan
4. Keep implementation steps atomic and testable
