#!/bin/bash

# Cursor IDE 监控脚本

echo "=== Cursor IDE 监控 ==="
echo "时间: $(date '+%Y-%m-%d %H:%M:%S')"

# 1. 进程状态
echo -e "\n[进程状态]"
PROCESS=$(ps aux | grep "/usr/share/cursor/cursor" | grep -v grep | head -1)
if [ -n "$PROCESS" ]; then
  PID=$(echo "$PROCESS" | awk '{print $2}')
  CPU=$(echo "$PROCESS" | awk '{print $3}')
  MEM=$(echo "$PROCESS" | awk '{print $4}')
  UPTIME=$(ps -p $PID -o etime --no-headers | tr -d ' ')
  
  echo "✅ Cursor 正在运行"
  echo "PID: $PID"
  echo "CPU: ${CPU}%"
  echo "内存: ${MEM}%"
  echo "运行时间: $UPTIME"
  
  # 总进程数
  TOTAL_PROCESS=$(ps aux | grep cursor | grep -v grep | wc -l)
  echo "总进程数: $TOTAL_PROCESS"
else
  echo "❌ Cursor 未运行"
fi

# 2. 工作空间
echo -e "\n[工作空间]"
WORKSPACE=$(find ~/.config/Cursor/User/workspaceStorage -maxdepth 1 -type d -mtime -1 2>/dev/null | head -1)
if [ -n "$WORKSPACE" ]; then
  echo "最近工作区: $WORKSPACE"
  
  # 工作区大小
  SIZE=$(du -sh "$WORKSPACE" 2>/dev/null | awk '{print $1}')
  echo "工作区大小: $SIZE"
fi

# 3. 扩展
echo -e "\n[扩展]"
EXT_COUNT=$(ls ~/.cursor/extensions/ 2>/dev/null | wc -l)
echo "已安装扩展: $EXT_COUNT 个"

# 最近更新的扩展
RECENT_EXT=$(ls -lt ~/.cursor/extensions/ 2>/dev/null | head -3 | tail -2 | awk '{print $NF}')
if [ -n "$RECENT_EXT" ]; then
  echo "最近更新:"
  echo "$RECENT_EXT" | while read ext; do
    echo "  - $ext"
  done
fi

# 4. 配置文件
echo -e "\n[配置文件]"
SETTINGS_SIZE=$(du -sh ~/.config/Cursor/User/settings.json 2>/dev/null | awk '{print $1}')
echo "设置文件大小: $SETTINGS_SIZE"

# 5. 日志（如果存在）
echo -e "\n[日志]"
LOG_FILE=$(find ~/.config/Cursor -name "*.log" -type f 2>/dev/null | head -1)
if [ -n "$LOG_FILE" ]; then
  echo "日志文件: $LOG_FILE"
  ERROR_COUNT=$(grep -c "ERROR" "$LOG_FILE" 2>/dev/null || echo "0")
  echo "错误数量: $ERROR_COUNT"
fi

# 6. 语言服务器
echo -e "\n[语言服务器]"
JAVA_LS=$(ps aux | grep "jdt.ls" | grep -v grep | wc -l)
SPRING_LS=$(ps aux | grep "spring-boot-language-server" | grep -v grep | wc -l)
echo "Java 语言服务器: $JAVA_LS 个"
echo "Spring Boot 服务器: $SPRING_LS 个"

echo -e "\n=== 监控完成 ==="