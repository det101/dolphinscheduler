# 自动化开发技能集 - 创建完成

## ✅ 已创建的 Skill

我成功创建了一套完整的自动化开发流程技能，包含 5 个 skill：

### 1. issue-analyzer
**路径**: `/home/luxl/.nanobot/workspace/skills/issue-analyzer/SKILL.md`

**功能**:
- 分析 GitHub issue
- 检查是否已有完善设计（如果有则跳过开发）
- 生成详细的实现思路和计划
- 支持 Spring Boot + Vue.js 技术栈

**核心命令**:
```bash
analyze_issue <issue_number> <repo>
check_issue_status <issue_number> <repo>
issue_has_design <issue_number> <repo>
```

### 2. code-developer
**路径**: `/home/luxl/.nanobot/workspace/skills/code-developer/SKILL.md`

**功能**:
- 根据实现计划开发代码
- 自动生成 Spring Boot 项目结构（实体、控制器、服务）
- 自动生成 Vue.js 项目结构（组件、路由、API）
- 运行测试、提交代码、推送到远程

**核心命令**:
```bash
develop <issue_number> <repo> <plan>
generate_component <type> <name>
run_tests
commit_changes <message>
```

**支持的组件类型**:
- `entity` - JPA 实体类
- `controller` - REST API 控制器
- `service` - 业务逻辑层
- `vue` - Vue 3 组件

### 3. code-reviewer
**路径**: `/home/luxl/.nanobot/workspace/skills/code-reviewer/SKILL.md`

**功能**:
- 自动化代码审查
- 检查代码风格（Checkstyle/ESLint）
- 检查安全漏洞（硬编码密码、SQL 注入、XSS）
- 检查测试覆盖率（Jacoco/Lcov）
- 生成审查报告
- 批准或要求修改 PR

**核心命令**:
```bash
review_pr <pr_number> <repo> <project_path>
quick_review <pr_number> <repo>
approve_pr <pr_number> <repo> [comment]
request_changes <pr_number> <repo> [comment]
```

### 4. pr-creator
**路径**: `/home/luxl/.nanobot/workspace/skills/pr-creator/SKILL.md`

**功能**:
- 创建和提交 Pull Request
- 自动生成 PR 描述
- 添加审查者和标签
- 合并 PR（支持 merge/rebase/squash）

**核心命令**:
```bash
create_pr_from_issue <issue_number> <repo> [branch_name]
quick_pr <title> <repo> [branch_name]
update_pr <pr_number> <repo> [commit_message]
merge_pr <pr_number> <repo> [merge_method]
```

### 5. automated-dev-workflow
**路径**: `/home/luxl/.nanobot/workspace/skills/automated-dev-workflow/SKILL.md`

**功能**:
- 整合所有技能的主流程
- 支持完整流程和分步执行
- 提供状态检查和恢复功能

**核心命令**:
```bash
automated_workflow <issue_number> <repo> [branch_name]
workflow_step <step> <issue_number> <repo>
workflow_status <issue_number> <repo>
resume_workflow <issue_number> <repo> [current_step]
```

## 🚀 快速开始

### 1. 配置环境

```bash
# 安装 GitHub CLI
brew install gh  # macOS
sudo apt install gh  # Linux

# 配置 GitHub Token
export GH_TOKEN=your_github_token

# 配置 Git
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"
```

### 2. 运行完整工作流

```bash
# 从 Issue 到 PR 的完整流程
automated_workflow 276 ComposioHQ/awesome-claude-skills
```

### 3. 分步执行

```bash
# 步骤 1: 分析 Issue
analyze_issue 276 ComposioHQ/awesome-claude-skills

# 步骤 2: 开发代码
develop 276 ComposioHQ/awesome-claude-skills "implementation plan..."

# 步骤 3: 代码审查
review_pr 276 ComposioHQ/awesome-claude-skills /path/to/project

# 步骤 4: 创建 PR
create_pr_from_issue 276 ComposioHQ/awesome-claude-skills
```

## 📋 工作流程

```
1. 分析 Issue → issue-analyzer
   └─ 检查是否有完善设计
   └─ 生成实现思路

2. 开发代码 → code-developer
   └─ 创建功能分支
   └─ 生成 Spring Boot + Vue.js 代码
   └─ 运行测试
   └─ 提交并推送

3. 代码审查 → code-reviewer
   └─ 检查代码风格
   └─ 检查安全性
   └─ 检查测试覆盖率
   └─ 生成审查报告

4. 创建 PR → pr-creator
   └─ 创建 PR
   └─ 添加审查者和标签
   └─ 发布 PR
```

## 🎯 技术栈

- **后端**: Java 8+, Spring Boot 2.7.x, Maven
- **前端**: Vue.js 3, Vite, Vue Router, Pinia
- **数据库**: PostgreSQL (JPA/Hibernate)
- **测试**: JUnit, Jest, Jacoco
- **CI/CD**: GitHub Actions
- **工具**: GitHub CLI (gh), Git, Maven, npm

## 📚 文档

- [总览文档](/home/luxl/.nanobot/workspace/skills/AUTOMATED_DEV_WORKFLOW.md)
- [使用说明](/home/luxl/.nanobot/workspace/skills/automated-dev-workflow/README.md)
- [issue-analyzer](/home/luxl/.nanobot/workspace/skills/issue-analyzer/SKILL.md)
- [code-developer](/home/luxl/.nanobot/workspace/skills/code-developer/SKILL.md)
- [code-reviewer](/home/luxl/.nanobot/workspace/skills/code-reviewer/SKILL.md)
- [pr-creator](/home/luxl/.nanobot/workspace/skills/pr-creator/SKILL.md)
- [automated-dev-workflow](/home/luxl/.nanobot/workspace/skills/automated-dev-workflow/SKILL.md)

## ✨ 特性

- ✅ 自动分析 Issue 并生成实现思路
- ✅ 检查是否已有完善设计（有则跳过）
- ✅ 自动生成 Spring Boot + Vue.js 代码
- ✅ 自动化代码审查（风格、安全、测试）
- ✅ 自动生成 PR 描述
- ✅ 支持分步执行和恢复
- ✅ 完整的错误处理
- ✅ 详细的文档和示例

## 🎉 完成！

所有 skill 已成功创建，现在你可以：

1. 使用 `automated_workflow` 一键完成从 Issue 到 PR 的完整流程
2. 使用分步命令灵活控制开发流程
3. 利用代码审查功能确保代码质量
4. 自动生成 PR 并提交

祝开发愉快！🚀
