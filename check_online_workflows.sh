#!/bin/bash

echo "=== 检查 DolphinScheduler 上线工作流 ==="

# 1. 尝试获取 token（新版本 API）
echo "尝试自动获取认证信息..."
AUTH_RESPONSE=$(curl -s -X POST http://localhost:12345/dolphinscheduler/login \
  -d "userName=admin&userPassword=dolphinscheduler123")

TOKEN=$(echo "$AUTH_RESPONSE" | jq -r '.data.token')
SESSION_ID=$(echo "$AUTH_RESPONSE" | jq -r '.data.sessionId')

# 2. 检查认证结果
if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
  AUTH_HEADER="token: $TOKEN"
  echo "✅ 使用 token 认证"
elif [ -n "$SESSION_ID" ] && [ "$SESSION_ID" != "null" ]; then
  AUTH_HEADER="sessionId: $SESSION_ID"
  echo "✅ 使用 sessionId 认证"
else
  echo "❌ 无法自动获取认证信息"
  echo ""
  echo "请手动提供认证信息："
  echo "1. 运行以下命令获取 token 或 sessionId："
  echo "   curl -X POST http://localhost:12345/dolphinscheduler/login \\"
  echo "     -d \"userName=admin&userPassword=dolphinscheduler123\" | jq"
  echo ""
  echo "2. 然后告诉我："
  echo "   - 如果有 token，请提供 token 值"
  echo "   - 如果有 sessionId，请提供 sessionId 值"
  echo "   - 或者直接提供完整的认证头"
  exit 1
fi

# 2. 查询所有项目
echo -e "\n=== 项目列表 ==="
curl -s "http://localhost:12345/dolphinscheduler/projects/list" \
  -H "$AUTH_HEADER" | jq -r '.data[] | "\(.name) (code: \(.code))"'

# 3. 查询每个项目的上线工作流
echo -e "\n=== 上线工作流统计 ==="
TOTAL_ONLINE=0
TOTAL_OFFLINE=0

for project_code in 168408167099232 168408167099233 168408167099234; do
  echo -e "\n项目 $project_code:"
  
  # 获取项目名称
  PROJECT_NAME=$(curl -s "http://localhost:12345/dolphinscheduler/projects/list" \
    -H "$AUTH_HEADER" | jq -r ".data[] | select(.code == $project_code) | .name")
  
  echo "项目名称: $PROJECT_NAME"
  
  # 查询工作流
  WORKFLOWS=$(curl -s "http://localhost:12345/dolphinscheduler/projects/$project_code/process-definition" \
    -H "$AUTH_HEADER" | jq '.data.totalList[]')
  
  if [ -n "$WORKFLOWS" ]; then
    ONLINE_COUNT=$(echo "$WORKFLOWS" | jq 'select(.releaseState == "ONLINE") | .name' | wc -l)
    OFFLINE_COUNT=$(echo "$WORKFLOWS" | jq 'select(.releaseState == "OFFLINE") | .name' | wc -l)
    
    echo "  上线: $ONLINE_COUNT 个"
    echo "  离线: $OFFLINE_COUNT 个"
    
    # 显示上线工作流详情
    if [ $ONLINE_COUNT -gt 0 ]; then
      echo "  上线工作流列表:"
      echo "$WORKFLOWS" | jq -r 'select(.releaseState == "ONLINE") | "    - \(.name) (code: \(.code))"'
    fi
    
    TOTAL_ONLINE=$((TOTAL_ONLINE + ONLINE_COUNT))
    TOTAL_OFFLINE=$((TOTAL_OFFLINE + OFFLINE_COUNT))
  else
    echo "  无工作流"
  fi
done

echo -e "\n=== 汇总 ==="
echo "总上线工作流: $TOTAL_ONLINE 个"
echo "总离线工作流: $TOTAL_OFFLINE 个"
echo "总计工作流: $((TOTAL_ONLINE + TOTAL_OFFLINE)) 个"

if [ $TOTAL_ONLINE -eq 0 ]; then
  echo -e "\n⚠️ 警告: 没有上线的工作流！"
  echo "请先发布工作流才能执行。"
fi