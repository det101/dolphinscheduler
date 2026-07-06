#!/bin/bash

echo "=== 检查 DolphinScheduler 上线工作流 (Token 认证版) ==="

# 检查是否提供了 token
if [ -z "$DS_TOKEN" ]; then
  echo "❌ 未提供 DolphinScheduler token"
  echo ""
  echo "使用方法："
  echo "1. 首先获取 token："
  echo "   - 登录 DolphinScheduler UI (http://localhost:12345)"
  echo "   - 点击右上角用户头像 → '用户信息'"
  echo "   - 复制 'Token' 字段的值"
  echo ""
  echo "2. 设置环境变量并运行脚本："
  echo "   export DS_TOKEN='你的token值'"
  echo "   ./check_online_workflows_token.sh"
  echo ""
  echo "或者直接运行："
  echo "   DS_TOKEN='你的token值' ./check_online_workflows_token.sh"
  echo ""
  exit 1
fi

echo "✅ 使用提供的 token 进行认证"
AUTH_HEADER="token: $DS_TOKEN"

# 测试 token 是否有效
echo "测试 token 有效性..."
TEST_RESPONSE=$(curl -s -w "%{http_code}" "http://localhost:12345/dolphinscheduler/projects/list" \
  -H "$AUTH_HEADER" -o /tmp/ds_test_response)

HTTP_CODE=$(tail -n1 /tmp/ds_test_response)
RESPONSE_CONTENT=$(head -n -1 /tmp/ds_test_response)

if [ "$HTTP_CODE" != "200" ]; then
  echo "❌ Token 无效或已过期 (HTTP $HTTP_CODE)"
  echo "响应内容: $RESPONSE_CONTENT"
  echo ""
  echo "请重新获取 token："
  echo "1. 登录 DolphinScheduler UI"
  echo "2. 用户信息 → 复制新的 token"
  echo "3. 更新 DS_TOKEN 环境变量"
  exit 1
fi

echo "✅ Token 验证成功"

# 2. 查询所有项目
echo -e "\n=== 项目列表 ==="
curl -s "http://localhost:12345/dolphinscheduler/projects/list" \
  -H "$AUTH_HEADER" | jq -r '.data[] | "\(.name) (code: \(.code))"'

# 3. 查询每个项目的上线工作流
echo -e "\n=== 上线工作流统计 ==="
TOTAL_ONLINE=0
TOTAL_OFFLINE=0

# 获取所有项目代码
PROJECT_CODES=$(curl -s "http://localhost:12345/dolphinscheduler/projects/list" \
  -H "$AUTH_HEADER" | jq -r '.data[].code')

if [ -z "$PROJECT_CODES" ]; then
  echo "未找到任何项目"
  exit 0
fi

for project_code in $PROJECT_CODES; do
  echo -e "\n项目 $project_code:"
  
  # 获取项目名称
  PROJECT_NAME=$(curl -s "http://localhost:12345/dolphinscheduler/projects/list" \
    -H "$AUTH_HEADER" | jq -r ".data[] | select(.code == $project_code) | .name")
  
  echo "项目名称: $PROJECT_NAME"
  
  # 查询工作流
  WORKFLOWS=$(curl -s "http://localhost:12345/dolphinscheduler/projects/$project_code/process-definition" \
    -H "$AUTH_HEADER" | jq '.data.totalList[]')
  
  if [ -n "$WORKFLOWS" ] && [ "$WORKFLOWS" != "null" ]; then
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

# 清理临时文件
rm -f /tmp/ds_test_response