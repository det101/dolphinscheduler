# 自动化开发技能集

## 📦 技能列表

这套技能提供完整的自动化开发流程，从 Issue 分析到 PR 提交：

| Skill | 功能 | 状态 |
|-------|------|------|
| **issue-analyzer** | 分析 GitHub issue，生成实现思路 | ✅ 已创建 |
| **code-developer** | 根据实现思路开发代码（Spring Boot + Vue.js） | ✅ 已创建 |
| **code-reviewer** | 代码审查，检查质量、安全性和测试覆盖率 | ✅ 已创建 |
| **pr-creator** | 创建和提交 Pull Request | ✅ 已创建 |
| **automated-dev-workflow** | 整合所有技能的主流程 | ✅ 已创建 |

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

## 📖 技能详解

### 1. issue-analyzer

**位置**: `/home/luxl/.nanobot/workspace/skills/issue-analyzer/SKILL.md`

**功能**:
- 分析 GitHub issue
- 检查是否已有完善设计
- 生成实现思路和计划

**主要命令**:
```bash
analyze_issue <issue_number> <repo>
check_issue_status <issue_number> <repo>
issue_has_design <issue_number> <repo>
```

### 2. code-developer

**位置**: `/home/luxl/.nanobot/workspace/skills/code-developer/SKILL.md`

**功能**:
- 根据实现计划开发代码
- 支持 Spring Boot + Vue.js 技术栈
- 生成实体、控制器、服务和 Vue 组件
- 运行测试和提交代码

**主要命令**:
```bash
develop <issue_number> <repo> <plan>
generate_component <type> <name>
run_tests
commit_changes <message>
```

**组件类型**:
- `entity` - 实体类
- `controller` - REST 控制器
- `service` - 服务层
- `vue` - Vue 组件

### 3. code-reviewer

**位置**: `/home/luxl/.nanobot/workspace/skills/code-reviewer/SKILL.md`

**功能**:
- 自动化代码审查
- 检查代码风格、安全性和测试覆盖率
- 生成审查报告
- 批准或要求修改 PR

**主要命令**:
```bash
review_pr <pr_number> <repo> <project_path>
quick_review <pr_number> <repo>
approve_pr <pr_number> <repo> [comment]
request_changes <pr_number> <repo> [comment]
```

**检查项**:
- ✅ 代码风格
- 🔒 安全性
- 🧪 测试覆盖率
- 📝 文档完整性

### 4. pr-creator

**位置**: `/home/luxl/.nanobot/workspace/skills/pr-creator/SKILL.md`

**功能**:
- 创建和提交 Pull Request
- 自动生成 PR 描述
- 添加审查者和标签
- 合并 PR

**主要命令**:
```bash
create_pr_from_issue <issue_number> <repo> [branch_name]
quick_pr <title> <repo> [branch_name]
update_pr <pr_number> <repo> [commit_message]
merge_pr <pr_number> <repo> [merge_method]
```

**合并方法**:
- `merge` - 普通合并
- `rebase` - 变基合并
- `squash` - 压缩合并

### 5. automated-dev-workflow

**位置**: `/home/luxl/.nanobot/workspace/skills/automated-dev-workflow/SKILL.md`

**功能**:
- 整合所有技能的主流程
- 支持完整流程和分步执行
- 提供状态检查和恢复功能

**主要命令**:
```bash
automated_workflow <issue_number> <repo> [branch_name]
workflow_step <step> <issue_number> <repo>
workflow_status <issue_number> <repo>
resume_workflow <issue_number> <repo> [current_step]
```

**步骤**:
1. `analyze` - 分析 issue
2. `develop` - 开发代码
3. `review` - 代码审查
4. `create` - 创建 PR

## 🎯 使用场景

### 场景 1: 处理新 Issue

```bash
# 完整流程
automated_workflow 276 ComposioHQ/awesome-claude-skills
```

### 场景 2: 批量处理 Issues

```bash
for issue in 276 277 278; do
  automated_workflow $issue ComposioHQ/awesome-claude-skills
done
```

### 场景 3: 自定义开发流程

```bash
# 分析
analyze_issue 276 ComposioHQ/awesome-claude-skills

# 生成组件
generate_component entity "User"
generate_component controller "UserController"
generate_component service "UserService"
generate_component vue "UserProfile"

# 测试和提交
run_tests
commit_changes "feat: add user management"

# 审查和创建 PR
review_pr 276 ComposioHQ/awesome-claude-skills /path/to/project
create_pr_from_issue 276 ComposioHQ/awesome-claude-skills
```

## ⚙️ 配置

### 环境变量

```bash
# GitHub Token
export GH_TOKEN=your_github_token

# 项目根目录（可选）
export PROJECT_ROOT=/path/to/project

# DolphinScheduler API（如果使用）
export DS_API_URL=http://localhost:12345/dolphinscheduler
export DS_TOKEN=4bb970fe470254c3612993196c616646
```

## 📋 检查清单

在创建 PR 之前：

- [ ] 代码遵循项目规范
- [ ] 没有安全漏洞
- [ ] 测试覆盖率 > 80%
- [ ] 错误处理完善
- [ ] 文档完整
- [ ] 性能可接受

## 🐛 故障排除

### GH_TOKEN 未设置

```bash
export GH_TOKEN=your_github_token
```

### 分支已存在

```bash
# 使用不同的分支名
automated_workflow 276 ComposioHQ/awesome-claude-skills my-custom-branch
```

### 测试失败

```bash
# 检查测试日志
cd /path/to/project
mvn test  # 后端测试
npm test  # 前端测试
```

## 📚 文档

- [automated-dev-workflow 使用说明](/home/luxl/.nanobot/workspace/skills/automated-dev-workflow/README.md)
- [issue-analyzer 文档](/home/luxl/.nanobot/workspace/skills/issue-analyzer/SKILL.md)
- [code-developer 文档](/home/luxl/.nanobot/workspace/skills/code-developer/SKILL.md)
- [code-reviewer 文档](/home/luxl/.nanobot/workspace/skills/code-reviewer/SKILL.md)
- [pr-creator 文档](/home/luxl/.nanobot/workspace/skills/pr-creator/SKILL.md)

## 💡 提示

- 使用 `workflow_status` 随时查看进度
- 使用 `resume_workflow` 从中断处继续
- 保持分支与主分支同步
- 尽早添加审查者获取反馈

---

**祝开发愉快！🚀**
