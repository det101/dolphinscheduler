---
name: dolphinscheduler
description: DolphinScheduler 分布式工作流调度专家技能，涵盖部署、DAG 设计、告警、运维、API 调用等。
metadata: {"nanobot":{"always":true,"emoji":"🐬"}}
---

# DolphinScheduler 专家技能

你是一名 DolphinScheduler 资深专家，掌握其架构设计、部署运维、工作流编排、API 集成等全链路知识。

## 核心概念

- **Project（项目）**：工作流的顶层组织单位
- **Workflow（工作流/DAG）**：由任务节点和依赖关系构成的有向无环图
- **Task（任务节点）**：Shell、Python、SQL、Flink、Spark、HTTP、SubProcess 等类型
- **Worker/Master**：Master 负责调度，Worker 负责执行
- **Zookeeper**：用于 Master/Worker 高可用注册与协调

## 常用命令

### 本地 Standalone 部署
```bash
# 启动 standalone 服务
./bin/dolphinscheduler-daemon.sh start standalone-server

# 停止服务
./bin/dolphinscheduler-daemon.sh stop standalone-server

# 查看日志
tail -f standalone-server/logs/*.log
```

### 高可用部署（HA）

```bash
# 启动 Master（多台机器）
bin/dolphinscheduler-daemon.sh start master-server

# 启动 Worker（按 worker-group 分组）
bin/dolphinscheduler-daemon.sh start worker-server

# 启动 API Server
bin/dolphinscheduler-daemon.sh start api-server

# 启动 Alert Server
bin/dolphinscheduler-daemon.sh start alert-server
```

### REST API 常用操作

```bash
# 1. 获取 Token
export TOKEN=$(curl -s -X POST http://localhost:12345/dolphinscheduler/login \
  -d "userName=admin&userPassword=dolphinscheduler123" | jq -r '.data.token')

# 2. 查询项目列表
curl -s http://localhost:12345/dolphinscheduler/project/list \
  -H "token: $TOKEN" | jq -r '.data[] | "\(.code): \(.name)"'

# 3. 创建项目
curl -s -X POST http://localhost:12345/dolphinscheduler/project \
  -H "token: $TOKEN" -H "Content-Type: application/x-www-form-urlencoded" \
  -d "projectName=test-project&description=Test Project"

# 4. 查询工作流定义（替换 projectCode）
curl -s "http://localhost:12345/dolphinscheduler/projects/{projectCode}/process-definition" \
  -H "token: $TOKEN" | jq '.data.totalList[] | "\(.code): \(.name)"'

# 5. 启动工作流实例
curl -s -X POST "http://localhost:12345/dolphinscheduler/projects/{projectCode}/executors/start-process-instance" \
  -H "token: $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "processDefinitionCode": "{workflowCode}",
    "scheduleTime": "2026-03-20 10:00:00",
    "failureStrategy": "CONTINUE",
    "warningType": "NONE"
  }'

# 6. 查询工作流实例状态
curl -s "http://localhost:12345/dolphinscheduler/projects/{projectCode}/process-instances?pageSize=10&pageNo=1" \
  -H "token: $TOKEN" | jq '.data.totalList[] | "\(.id): \(.processDefinitionCode) - \(.state)"'

# 7. 查询任务实例日志（替换 taskInstanceId）
curl -s "http://localhost:12345/dolphinscheduler/projects/{projectCode}/task-instances/{taskInstanceId}/view-log" \
  -H "token: $TOKEN"
```

## 配置文件

```
# 关键配置文件路径
conf/common.properties      # 通用配置
conf/application.yaml       # Spring Boot 配置
conf/worker.properties      # Worker 配置
conf/master.properties      # Master 配置
conf/alert.properties       # 告警配置
```

### 关键配置项
```properties
# Master 配置
master.listen.port=5678
master.heartbeat.interval=10s
master.task.commit.retry.times=5

# Worker 配置
worker.groups=default,high-memory,gpu
worker.heartbeat.interval=10s
worker.max.cpu.load.avg=100

# Zookeeper 配置
registry.type=zookeeper
registry.servers=localhost:2181

# 数据库配置
spring.datasource.url=jdbc:mysql://localhost:3306/dolphinscheduler
spring.datasource.username=ds_user
spring.datasource.password=ds_password
```

## 任务类型详解

### Shell 任务
```json
{
  "type": "SHELL",
  "taskDefinition": {
    "name": "shell-task",
    "taskParams": {
      "rawScript": "#!/bin/bash\necho 'Hello World'\necho ${custom_param}",
      "localParams": [
        {"prop": "custom_param", "value": "test"}
      ],
      "dependence": {}
    }
  }
}
```

### SQL 任务
```json
{
  "type": "SQL",
  "taskDefinition": {
    "name": "sql-task",
    "taskParams": {
      "type": "MYSQL",
      "datasource": 1,
      "sql": "SELECT * FROM users WHERE id = ${user_id}",
      "preStatements": [],
      "postStatements": [],
      "title": "query_result",
      "udfs": ""
    }
  }
}
```

### HTTP 任务
```json
{
  "type": "HTTP",
  "taskDefinition": {
    "name": "http-task",
    "taskParams": {
      "httpParams": [
        {"prop": "key", "httpParamsValue": "value"}
      ],
      "url": "https://api.example.com/data",
      "httpMethod": "POST",
      "httpCheckCondition": "STATUS_CODE_DEFAULT",
      "condition": "200",
      "requestBody": "{\"id\": 123}",
      "timeout": 60
    }
  }
}
```

## 常见故障排查

| 问题 | 排查方向 | 解决方案 |
|------|---------|---------|
| 任务长时间 RUNNING | 检查 Worker 日志 `logs/worker-server.log`，查看 yarn/k8s 资源 | 调整 Worker 资源、检查依赖任务状态 |
| Master 脑裂 | 检查 Zookeeper 连通性和 session timeout | 重启 Master、检查 ZK 集群健康 |
| 工作流提交失败 | 检查 `api-server.log`，确认 DB 连接和权限 | 检查数据库连接、用户权限 |
| 告警未发送 | 检查 alert-server 日志和告警组配置 | 配置告警实例和告警组 |
| 依赖任务不触发 | 确认上游任务状态为 SUCCESS，检查依赖类型（AND/OR） | 检查依赖配置、手动触发上游 |
| Worker 注册不上 | 检查 Worker 到 Master 的网络连通性 | 检查防火墙、ZK 配置 |
| 任务 OOM | 查看 Worker 内存使用情况 | 调整 JVM 参数 `-Xmx4g` 或 Worker 分组 |

## DAG 设计最佳实践

### 1. 分层设计
```
L1: 数据采集层（Source Data）
  │
L2: 数据清洗层（Clean & Transform）
  │
L3: 数据汇总层（Aggregate）
  │
L4: 数据服务层（Data Service）
```

### 2. 依赖管理
- 使用 **ALL_SUCCESS** 确保上游全部成功
- 使用 **ONE_SUCCESS** 适用于并行任选一
- 使用 **CONTINUE** 失败策略保证容错

### 3. 参数传递
```bash
# 上游任务输出
output_dir=/tmp/data/$(date +%Y%m%d)
echo "output_dir=${output_dir}" > /tmp/param.txt

# 下游任务读取
output_dir=$(cat /tmp/param.txt | grep output_dir | cut -d= -f2)
```

## 版本升级注意事项

1. 升级前备份 `dolphinscheduler` 数据库
2. 执行 SQL 升级脚本（`sql/upgrade/` 目录）
3. 3.x → 3.2.x：注意 `worker.groups` 配置迁移和 API 路径变更
4. 升级后验证任务类型兼容性

## 内置工具函数

```bash
function ds_get_token() {
  echo "${DS_TOKEN:-4bb970fe470254c3612993196c616646}"
}

function ds_submit() {
  local pd_id=$1
  local schedule_time=$2
  local token=$(ds_get_token)
  curl -s -X POST "$DS_API_URL/projects/1/definition/$pd_id/execute" \
    -H "token: $token" \
    -H "Content-Type: application/json" \
    -d "{\"scheduleTime\": \"$schedule_time\"}" | jq .
}

function ds_status() {
  local ti_id=$1
  local token=$(ds_get_token)
  curl -s -X GET "$DS_API_URL/tasks/$ti_id" \
    -H "token: $token" | jq .
}

function ds_list_defs() {
  local token=$(ds_get_token)
  curl -s -X GET "$DS_API_URL/projects/1/definitions" \
    -H "token: $token" | jq .
}

function ds_create_project() {
  local project_name=$1
  local description=${2:-""}
  local token=$(ds_get_token)
  curl -s -X POST "$DS_API_URL/project/list" \
    -H "token: $token" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"$project_name\", \"description\": \"$description\", \"connParam\": \"\"}" | jq .
}

function ds_delete_project() {
  local project_name=$1
  local token=$(ds_get_token)
  curl -s -X DELETE "$DS_API_URL/project/$project_name" \
    -H "token: $token" | jq .
}
```
