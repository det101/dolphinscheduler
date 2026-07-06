# Automated Development Workflow - 使用说明

## 📚 概述

这是一套完整的自动化开发流程技能，包含 5 个 skill：

1. **issue-analyzer** - 分析 GitHub issue 并生成实现思路
2. **code-developer** - 根据实现思路开发代码（Spring Boot + Vue.js）
3. **code-reviewer** - 代码审查，检查代码质量、安全性和测试覆盖率
4. **pr-creator** - 创建并提交 Pull Request
5. **automated-dev-workflow** - 整合所有技能的主流程

## 🚀 快速开始

### 1. 安装依赖

```bash
# 安装 GitHub CLI
brew install gh  # macOS
# 或
sudo apt install gh  # Linux

# 配置 GitHub Token
export GH_TOKEN=your_github_token_here

# 配置 Git
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"
```

### 2. 基本使用流程

#### 完整流程（从 Issue 到 PR）

```bash
# 运行完整工作流
automated_workflow 276 ComposioHQ/awesome-claude-skills
```

#### 分步执行

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

### 3. 检查状态

```bash
# 查看工作流状态
workflow_status 276 ComposioHQ/awesome-claude-skills
```

### 4. 恢复工作流

```bash
# 从指定步骤恢复
resume_workflow 276 ComposioHQ/awesome-claude-skills develop
```

## 📖 详细文档

### issue-analyzer

**功能**: 分析 GitHub issue，检查是否已有完善设计，生成实现思路

**主要命令**:
- `analyze_issue <issue_number> <repo>` - 分析 issue
- `check_issue_status <issue_number> <repo>` - 检查 issue 状态
- `issue_has_design <issue_number> <repo>` - 检查是否有完善设计

**示例**:
```bash
export GH_TOKEN=your_token
analyze_issue 276 ComposioHQ/awesome-claude-skills
```

### code-developer

**功能**: 根据实现思路开发代码，支持 Spring Boot + Vue.js 技术栈

**主要命令**:
- `develop <issue_number> <repo> <plan>` - 根据计划开发
- `generate_component <type> <name>` - 生成特定组件
- `run_tests` - 运行测试
- `commit_changes <message>` - 提交代码

**组件类型**:
- `entity` - 实体类
- `controller` - REST 控制器
- `service` - 服务层
- `vue` - Vue 组件

**示例**:
```bash
develop 276 ComposioHQ/awesome-claude-skills "implementation plan..."
generate_component entity "User"
generate_component controller "UserController"
```

### code-reviewer

**功能**: 自动化代码审查，检查代码质量、安全性和测试覆盖率

**主要命令**:
- `review_pr <pr_number> <repo> <project_path>` - 完整审查
- `quick_review <pr_number> <repo>` - 快速审查
- `approve_pr <pr_number> <repo> [comment]` - 批准 PR
- `request_changes <pr_number> <repo> [comment]` - 要求修改

**检查项**:
- ✅ 代码风格
- 🔒 安全性
- 🧪 测试覆盖率
- 📝 文档完整性

**示例**:
```bash
review_pr 277 ComposioHQ/awesome-claude-skills /path/to/project
approve_pr 277 ComposioHQ/awesome-claude-skills "LGTM!"
```

### pr-creator

**功能**: 创建和提交 Pull Request

**主要命令**:
- `create_pr_from_issue <issue_number> <repo> [branch_name]` - 从 issue 创建 PR
- `quick_pr <title> <repo> [branch_name]` - 快速创建 PR
- `update_pr <pr_number> <repo> [commit_message]` - 更新 PR
- `merge_pr <pr_number> <repo> [merge_method]` - 合并 PR

**合并方法**:
- `merge` - 普通合并
- `rebase` - 变基合并
- `squash` - 压缩合并

**示例**:
```bash
create_pr_from_issue 276 ComposioHQ/awesome-claude-skills
merge_pr 277 ComposioHQ/awesome-claude-skills squash
```

### automated-dev-workflow

**功能**: 整合所有技能的主流程

**主要命令**:
- `automated_workflow <issue_number> <repo> [branch_name]` - 完整流程
- `workflow_step <step> <issue_number> <repo>` - 执行单步
- `workflow_status <issue_number> <repo>` - 查看状态
- `resume_workflow <issue_number> <repo> [current_step]` - 恢复流程

**步骤**:
1. `analyze` - 分析 issue
2. `develop` - 开发代码
3. `review` - 代码审查
4. `create` - 创建 PR

**示例**:
```bash
# 完整流程
automated_workflow 276 ComposioHQ/awesome-claude-skills

# 分步执行
workflow_step analyze 276 ComposioHQ/awesome-claude-skills
workflow_step develop 276 ComposioHQ/awesome-claude-skills
workflow_step review 276 ComposioHQ/awesome-claude-skills /path/to/project
workflow_step create 276 ComposioHQ/awesome-claude-skills

# 查看状态
workflow_status 276 ComposioHQ/awesome-claude-skills

# 从 develop 步骤恢复
resume_workflow 276 ComposioHQ/awesome-claude-skills develop
```

## 🎯 典型工作流

### 场景 1: 处理新 Issue

```bash
# 1. 分析 Issue
export GH_TOKEN=your_token
analyze_issue 276 ComposioHQ/awesome-claude-skills

# 2. 检查是否有完善设计
if issue_has_design 276 ComposioHQ/awesome-claude-skills; then
  echo "已有完善设计，跳过开发"
else
  # 3. 开发代码
  develop 276 ComposioHQ/awesome-claude-skills "implementation plan..."
  
  # 4. 代码审查
  review_pr 276 ComposioHQ/awesome-claude-skills /path/to/project
  
  # 5. 创建 PR
  create_pr_from_issue 276 ComposioHQ/awesome-claude-skills
fi
```

### 场景 2: 批量处理 Issues

```bash
# 处理多个 issues
for issue in 276 277 278; do
  echo "Processing issue #$issue..."
  automated_workflow $issue ComposioHQ/awesome-claude-skills
  echo ""
done
```

### 场景 3: 自定义开发流程

```bash
# 1. 分析
analyze_issue 276 ComposioHQ/awesome-claude-skills

# 2. 生成特定组件
generate_component entity "User"
generate_component controller "UserController"
generate_component service "UserService"
generate_component vue "UserProfile"

# 3. 运行测试
run_tests

# 4. 提交代码
commit_changes "feat: add user management"

# 5. 代码审查
review_pr 276 ComposioHQ/awesome-claude-skills /path/to/project

# 6. 创建 PR
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

### Git 配置

```bash
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"
```

## 📋 检查清单

在创建 PR 之前，确保：

- [ ] 代码遵循项目规范
- [ ] 没有安全漏洞
- [ ] 测试覆盖率 > 80%
- [ ] 错误处理完善
- [ ] 文档完整
- [ ] 性能可接受
- [ ] 代码可维护

## 🐛 故障排除

### 问题：GH_TOKEN 未设置

```bash
export GH_TOKEN=your_github_token
```

### 问题：分支已存在

```bash
# 查看现有 PR
gh pr list --repo ComposioHQ/awesome-claude-skills --head feature/issue-276

# 使用不同的分支名
automated_workflow 276 ComposioHQ/awesome-claude-skills my-custom-branch
```

### 问题：测试失败

```bash
# 检查测试日志
cd /path/to/project
mvn test  # 后端测试
npm test  # 前端测试

# 修复问题后重新运行
automated_workflow 276 ComposioHQ/awesome-claude-skills
```

## 📚 最佳实践

1. **小步快跑**: 保持 PR 小巧，便于审查
2. **频繁测试**: 每次开发后运行测试
3. **清晰提交**: 使用描述性的 commit message
4. **及时沟通**: 在 PR 中添加详细说明
5. **持续迭代**: 根据审查反馈不断改进

## 🔗 相关资源

- [GitHub CLI 文档](https://cli.github.com/)
- [Spring Boot 文档](https://spring.io/projects/spring-boot)
- [Vue.js 文档](https://vuejs.org/)
- [代码审查最佳实践](https://www.atlassian.com/git/tutorials/comparing-workflows/pull-request-workflow)

## 💡 提示

- 使用 `workflow_status` 随时查看进度
- 使用 `resume_workflow` 从中断处继续
- 保持分支与主分支同步
- 尽早添加审查者获取反馈

---

**祝开发愉快！🚀**
