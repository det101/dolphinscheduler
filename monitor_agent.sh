#!/bin/bash

# Agent 监控脚本
# 用法: ./monitor_agent.sh <agent-name>

AGENT_NAME=${1:-"openclaw"}
LOG_FILE="/tmp/agent_monitor.log"

echo "=== 监控 Agent: $AGENT_NAME ===" | tee -a "$LOG_FILE"
echo "时间: $(date '+%Y-%m-%d %H:%M:%S')" | tee -a "$LOG_FILE"

# 1. 进程状态
echo -e "\n[进程状态]" | tee -a "$LOG_FILE"
PROCESS=$(ps aux | grep -i "$AGENT_NAME" | grep -v grep | grep -v monitor)
if [ -n "$PROCESS" ]; then
  echo "✅ Agent 正在运行" | tee -a "$LOG_FILE"
  echo "$PROCESS" | tee -a "$LOG_FILE"
  
  # 提取 PID
  PID=$(echo "$PROCESS" | awk '{print $2}')
  echo "PID: $PID" | tee -a "$LOG_FILE"
  
  # CPU 和内存使用
  CPU_MEM=$(ps -p $PID -o %cpu,%mem --no-headers)
  echo "CPU/内存: $CPU_MEM" | tee -a "$LOG_FILE"
else
  echo "❌ Agent 未运行" | tee -a "$LOG_FILE"
fi

# 2. 日志检查（如果存在）
echo -e "\n[日志检查]" | tee -a "$LOG_FILE"
LOG_PATH=$(find ~ -name "*$AGENT_NAME*.log" -type f 2>/dev/null | head -1)
if [ -n "$LOG_PATH" ]; then
  echo "日志文件: $LOG_PATH" | tee -a "$LOG_FILE"
  
  # 最近 10 行日志
  echo "最近日志:" | tee -a "$LOG_FILE"
  tail -10 "$LOG_PATH" | tee -a "$LOG_FILE"
  
  # 错误统计
  ERROR_COUNT=$(grep -c "ERROR" "$LOG_PATH" 2>/dev/null || echo "0")
  echo "错误数量: $ERROR_COUNT" | tee -a "$LOG_FILE"
else
  echo "未找到日志文件" | tee -a "$LOG_FILE"
fi

# 3. API 健康检查（如果是 OpenClaw）
if [ "$AGENT_NAME" = "openclaw" ]; then
  echo -e "\n[API 健康检查]" | tee -a "$LOG_FILE"
  
  # 检查 Gateway 端口
  if curl -s --max-time 2 http://127.0.0.1:18789/health > /dev/null 2>&1; then
    echo "✅ Gateway API 正常" | tee -a "$LOG_FILE"
    
    # 获取详细状态
    STATUS=$(openclaw status 2>/dev/null | head -20)
    echo "$STATUS" | tee -a "$LOG_FILE"
  else
    echo "❌ Gateway API 无响应" | tee -a "$LOG_FILE"
  fi
fi

# 4. 工作文件检查
echo -e "\n[工作文件检查]" | tee -a "$LOG_FILE"
WORKSPACE=$(find ~ -name ".$AGENT_NAME" -type d 2>/dev/null | head -1)
if [ -n "$WORKSPACE" ]; then
  echo "工作空间: $WORKSPACE" | tee -a "$LOG_FILE"
  
  # 最近修改的文件
  echo "最近修改的文件:" | tee -a "$LOG_FILE"
  find "$WORKSPACE" -type f -mtime -1 -ls 2>/dev/null | head -5 | tee -a "$LOG_FILE"
else
  echo "未找到工作空间" | tee -a "$LOG_FILE"
fi

echo -e "\n=== 监控完成 ===" | tee -a "$LOG_FILE"
echo "日志已保存到: $LOG_FILE"