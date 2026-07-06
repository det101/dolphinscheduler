---
name: code-reviewer
description: Perform code review and provide feedback
---

# Code Reviewer Skill

## Overview

This skill performs automated code review and provides detailed feedback on code quality, security, performance, and best practices.

## Prerequisites

- GitHub CLI (`gh`) installed
- GitHub token: `export GH_TOKEN=your_token`
- Optional: Install code analysis tools (checkstyle, eslint, etc.)

## Helper Functions

```bash
# Get GitHub token
function gh_get_token() {
  echo "${GH_TOKEN:-}"
}

# Fetch PR details
function get_pr_details() {
  local pr_number=$1
  local repo=$2
  
  gh pr view "$pr_number" --repo "$repo" --json number,title,body,headRefName,baseRefName,state,createdAt --jq '{
    number,
    title,
    body,
    headBranch: .headRefName,
    baseBranch: .baseRefName,
    state,
    createdAt
  }'
}

# Fetch PR files
function get_pr_files() {
  local pr_number=$1
  local repo=$2
  
  gh pr diff "$pr_number" --repo "$repo" | head -1000
}

# Check code style
function check_code_style() {
  local project_path=$1
  
  echo "🔍 Checking code style..."
  
  # Backend style check (Java)
  if [ -d "$project_path/backend" ]; then
    cd "$project_path/backend" || return 1
    if command -v checkstyle &> /dev/null; then
      checkstyle -c google_checks.xml src/main/java 2>&1 | head -50
    else
      echo "⚠️  Checkstyle not installed, skipping Java style check"
    fi
  fi
  
  # Frontend style check (JavaScript/TypeScript)
  if [ -d "$project_path/frontend" ]; then
    cd "$project_path/frontend" || return 1
    if command -v eslint &> /dev/null; then
      npm run lint 2>&1 | head -50
    else
      echo "⚠️  ESLint not installed, skipping JS style check"
    fi
  fi
  
  echo "✅ Code style check completed"
}

# Check for security issues
function check_security() {
  local project_path=$1
  
  echo "🔒 Checking for security issues..."
  
  # Check for hardcoded secrets
  if grep -r "password\s*=\s*['\"][^'\"]+['\"]" "$project_path" 2>/dev/null | grep -v node_modules | grep -v ".git"; then
    echo "❌ Found hardcoded passwords!"
    grep -r "password\s*=\s*['\"][^'\"]+['\"]" "$project_path" 2>/dev/null | grep -v node_modules | grep -v ".git"
  fi
  
  # Check for SQL injection vulnerabilities
  if grep -r "executeQuery.*\+" "$project_path" 2>/dev/null | grep -v node_modules | grep -v ".git"; then
    echo "⚠️  Potential SQL injection detected!"
  fi
  
  # Check for XSS vulnerabilities
  if grep -r "innerHTML\s*=" "$project_path" 2>/dev/null | grep -v node_modules | grep -v ".git"; then
    echo "⚠️  Potential XSS vulnerability detected!"
  fi
  
  echo "✅ Security check completed"
}

# Check test coverage
function check_test_coverage() {
  local project_path=$1
  
  echo "🧪 Checking test coverage..."
  
  # Backend test coverage
  if [ -d "$project_path/backend" ]; then
    cd "$project_path/backend" || return 1
    if [ -f "target/site/jacoco/jacoco.xml" ]; then
      local coverage=$(grep -oP 'percentage="\K[0-9.]+' target/site/jacoco/jacoco.xml | head -1)
      echo "Backend test coverage: ${coverage}%"
      
      if (( $(echo "$coverage < 80" | bc -l) )); then
        echo "⚠️  Backend test coverage is below 80%"
      fi
    else
      echo "⚠️  Jacoco report not found, run 'mvn jacoco:report' first"
    fi
  fi
  
  # Frontend test coverage
  if [ -d "$project_path/frontend" ]; then
    cd "$project_path/frontend" || return 1
    if [ -f "coverage/lcov.info" ]; then
      local coverage=$(grep -oP 'coverage:\K[0-9.]+' coverage/lcov.info | head -1)
      echo "Frontend test coverage: ${coverage}%"
      
      if (( $(echo "$coverage < 80" | bc -l) )); then
        echo "⚠️  Frontend test coverage is below 80%"
      fi
    else
      echo "⚠️  Coverage report not found, run 'npm run test -- --coverage' first"
    fi
  fi
  
  echo "✅ Test coverage check completed"
}

# Generate review report
function generate_review_report() {
  local pr_number=$1
  local repo=$2
  local project_path=$3
  
  cat << EOF
# Code Review Report for PR #$pr_number

## Overview
- **PR Title**: $(get_pr_details "$pr_number" "$repo" | jq -r '.title')
- **Branch**: $(get_pr_details "$pr_number" "$repo" | jq -r '.headBranch') → $(get_pr_details "$pr_number" "$repo" | jq -r '.baseBranch')
- **Created**: $(get_pr_details "$pr_number" "$repo" | jq -r '.createdAt')
- **Status**: $(get_pr_details "$pr_number" "$repo" | jq -r '.state')

## Code Quality Checks

### ✅ Code Style
$(check_code_style "$project_path" 2>&1 | tail -5)

### 🔒 Security
$(check_security "$project_path" 2>&1 | tail -5)

### 🧪 Test Coverage
$(check_test_coverage "$project_path" 2>&1 | tail -5)

## Review Summary

### Strengths
- [ ] Code follows established patterns
- [ ] Proper error handling
- [ ] Good documentation
- [ ] Comprehensive tests

### Areas for Improvement
- [ ] Code style consistency
- [ ] Security best practices
- [ ] Test coverage
- [ ] Performance optimization

### Recommendations
1. **High Priority**: Fix critical issues
2. **Medium Priority**: Address style and documentation
3. **Low Priority**: Minor improvements

## Approval Status
- [ ] Approved
- [ ] Changes Requested
- [ ] Needs More Information

## Reviewer Notes
$(date)
EOF
}

# Comment on PR
function comment_on_pr() {
  local pr_number=$1
  local repo=$2
  local comment=$3
  
  gh pr comment "$pr_number" --repo "$repo" --body "$comment"
  echo "✅ Commented on PR #$pr_number"
}
```

## Commands

### 1. Review a PR

```bash
# review_pr <pr_number> <repo> <project_path>
function review_pr() {
  local pr_number=$1
  local repo=$2
  local project_path=$3
  
  echo "🔍 Starting code review for PR #$pr_number..."
  
  # Get PR details
  local pr_details=$(get_pr_details "$pr_number" "$repo")
  echo "PR Details:"
  echo "$pr_details" | jq .
  
  # Perform checks
  check_code_style "$project_path"
  check_security "$project_path"
  check_test_coverage "$project_path"
  
  # Generate report
  local report=$(generate_review_report "$pr_number" "$repo" "$project_path")
  echo "$report"
  
  # Save report to file
  echo "$report" > "review-pr-$pr_number-$(date +%Y%m%d).md"
  echo "📄 Report saved to: review-pr-$pr_number-$(date +%Y%m%d).md"
}
```

### 2. Quick review

```bash
# quick_review <pr_number> <repo>
function quick_review() {
  local pr_number=$1
  local repo=$2
  
  echo "⚡ Quick review for PR #$pr_number..."
  
  local pr_details=$(get_pr_details "$pr_number" "$repo")
  local state=$(echo "$pr_details" | jq -r '.state')
  
  if [ "$state" != "OPEN" ]; then
    echo "❌ PR #$pr_number is not open (current state: $state)"
    return 1
  fi
  
  echo "✅ PR #$pr_number is open and ready for review"
  echo "Title: $(echo "$pr_details" | jq -r '.title')"
  echo "Branch: $(echo "$pr_details" | jq -r '.headBranch')"
}
```

### 3. Approve PR

```bash
# approve_pr <pr_number> <repo> [comment]
function approve_pr() {
  local pr_number=$1
  local repo=$2
  local comment=${3:-"LGTM! Approved."}
  
  gh pr review "$pr_number" --repo "$repo" --approve --body "$comment"
  echo "✅ Approved PR #$pr_number"
}
```

### 4. Request changes

```bash
# request_changes <pr_number> <repo> [comment]
function request_changes() {
  local pr_number=$1
  local repo=$2
  local comment=${3:-"Changes requested. Please review the feedback."}
  
  gh pr review "$pr_number" --repo "$repo" --request-changes --body "$comment"
  echo "✅ Requested changes for PR #$pr_number"
}
```

## Usage Example

```bash
# Set GitHub token
export GH_TOKEN=your_github_token

# Full review
review_pr 277 ComposioHQ/awesome-claude-skills /path/to/project

# Quick review
quick_review 277 ComposioHQ/awesome-claude-skills

# Approve after review
approve_pr 277 ComposioHQ/awesome-claude-skills "LGTM! Great work."

# Request changes
request_changes 277 ComposioHQ/awesome-claude-skills "Please fix the security issues mentioned in the review."
```

## Integration with Other Skills

This skill works with:
- `code-developer` - To review code after development
- `pr-creator` - To provide feedback before PR creation

## Best Practices

1. Review code in small, manageable chunks
2. Focus on logic and architecture first, style second
3. Provide constructive feedback with examples
4. Check for security vulnerabilities
5. Ensure test coverage is adequate
6. Verify documentation is up to date
7. Consider performance implications
8. Follow project coding standards

## Review Checklist

- [ ] Code follows project conventions
- [ ] No security vulnerabilities
- [ ] Adequate test coverage (>80%)
- [ ] Proper error handling
- [ ] Documentation is complete
- [ ] No unnecessary dependencies
- [ ] Performance is acceptable
- [ ] Code is maintainable and readable
