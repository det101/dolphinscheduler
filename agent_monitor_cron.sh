#!/bin/bash

# 定时监控 Agent 并发送报告
# 用法: 添加到 crontab: */5 * * * * /path/to/agent_monitor_cron.sh

AGENT_NAME="openclaw"
REPORT_FILE="/tmp/agent_report.txt"
ALERT_THRESHOLD_CPU=80
ALERT_THRESHOLD_MEM=80

# 运行监控脚本
/home/luxl/.nanobot/workspace/monitor_agent.sh "$AGENT_NAME" > "$REPORT_FILE" 2>&1

# 检查告警条件
ALERT_MSG=""

# 检查进程是否存在
if ! ps aux | grep -i "$AGENT_NAME" | grep -v grep > /dev/null; then
  ALERT_MSG="⚠️ 告警: $AGENT_NAME 进程已停止"
fi

# 检查 CPU 使用率
CPU_USAGE=$(ps aux | grep -i "$AGENT_NAME" | grep -v grep | awk '{print int($3)}')
if [ -n "$CPU_USAGE" ] && [ "$CPU_USAGE" -gt "$ALERT_THRESHOLD_CPU" ]; then
  ALERT_MSG="$ALERT_MSG\n⚠️ 告警: CPU 使用率过高 ($CPU_USAGE%)"
fi

# 检查内存使用率
MEM_USAGE=$(ps aux | grep -i "$AGENT_NAME" | grep -v grep | awk '{print int($4)}')
if [ -n "$MEM_USAGE" ] && [ "$MEM_USAGE" -gt "$ALERT_THRESHOLD_MEM" ]; then
  ALERT_MSG="$ALERT_MSG\n⚠️ 告警: 内存使用率过高 ($MEM_USAGE%)"
fi

# 发送告警（如果有）
if [ -n "$ALERT_MSG" ]; then
  echo -e "$ALERT_MSG" | tee -a "$REPORT_FILE"
  
  # 可以集成到消息渠道（Telegram/Feishu/Email）
  # 示例：发送到 Telegram
  # curl -X POST "https://api.telegram.org/bot<token>/sendMessage" \
  #   -d "chat_id=<chat_id>&text=$ALERT_MSG"
  
  # 示例：发送到 Feishu
  # curl -X POST "<webhook_url>" \
  #   -H "Content-Type: application/json" \
  #   -d "{\"msg_type\":\"text\",\"content\":{\"text\":\"$ALERT_MSG\"}}"
fi

# 记录到日志
echo "$(date '+%Y-%m-%d %H:%M:%S') - 监控完成" >> /tmp/agent_monitor.log