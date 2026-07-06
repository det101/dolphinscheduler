#!/bin/bash

# 多 Agent 监控面板
# 用法: ./monitor_all_agents.sh

AGENTS=("openclaw" "nanobot" "dolphinscheduler")
DASHBOARD_FILE="/tmp/agent_dashboard.html"

echo "生成监控面板..."

# HTML 头部
cat > "$DASHBOARD_FILE" << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>Agent 监控面板</title>
    <meta charset="UTF-8">
    <meta http-equiv="refresh" content="30">
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }
        .agent-card { background: white; padding: 20px; margin: 10px 0; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        .status-running { color: #4caf50; font-weight: bold; }
        .status-stopped { color: #f44336; font-weight: bold; }
        .metric { margin: 10px 0; }
        .progress-bar { background: #e0e0e0; border-radius: 4px; overflow: hidden; height: 20px; }
        .progress-fill { height: 100%; transition: width 0.3s; }
        .cpu-high { background: #ff9800; }
        .cpu-normal { background: #4caf50; }
        .mem-high { background: #f44336; }
        .mem-normal { background: #2196f3; }
    </style>
</head>
<body>
    <h1>🤖 Agent 监控面板</h1>
    <p>更新时间: <span id="timestamp"></span></p>
EOF

# 添加时间戳
echo "<script>document.getElementById('timestamp').innerText = new Date().toLocaleString('zh-CN');</script>" >> "$DASHBOARD_FILE"

# 监控每个 agent
for AGENT in "${AGENTS[@]}"; do
    echo "<div class='agent-card'>" >> "$DASHBOARD_FILE"
    echo "<h2>$AGENT</h2>" >> "$DASHBOARD_FILE"
    
    # 进程状态
    PROCESS=$(ps aux | grep -i "$AGENT" | grep -v grep | grep -v monitor_all)
    
    if [ -n "$PROCESS" ]; then
        PID=$(echo "$PROCESS" | awk '{print $2}')
        CPU=$(echo "$PROCESS" | awk '{print int($3)}')
        MEM=$(echo "$PROCESS" | awk '{print int($4)}')
        UPTIME=$(ps -p $PID -o etime --no-headers | tr -d ' ')
        
        echo "<p class='status-running'>✅ 运行中</p>" >> "$DASHBOARD_FILE"
        echo "<div class='metric'>PID: $PID | 运行时间: $UPTIME</div>" >> "$DASHBOARD_FILE"
        
        # CPU 进度条
        CPU_CLASS=$([ "$CPU" -gt 80 ] && echo "cpu-high" || echo "cpu-normal")
        echo "<div class='metric'>CPU: ${CPU}%" >> "$DASHBOARD_FILE"
        echo "<div class='progress-bar'><div class='progress-fill $CPU_CLASS' style='width: ${CPU}%'></div></div></div>" >> "$DASHBOARD_FILE"
        
        # 内存进度条
        MEM_CLASS=$([ "$MEM" -gt 80 ] && echo "mem-high" || echo "mem-normal")
        echo "<div class='metric'>内存: ${MEM}%" >> "$DASHBOARD_FILE"
        echo "<div class='progress-bar'><div class='progress-fill $MEM_CLASS' style='width: ${MEM}%'></div></div></div>" >> "$DASHBOARD_FILE"
    else
        echo "<p class='status-stopped'>❌ 已停止</p>" >> "$DASHBOARD_FILE"
    fi
    
    echo "</div>" >> "$DASHBOARD_FILE"
done

# HTML 尾部
cat >> "$DASHBOARD_FILE" << 'EOF'
</body>
</html>
EOF

echo "✅ 监控面板已生成: $DASHBOARD_FILE"
echo "在浏览器中打开: file://$DASHBOARD_FILE"

# 可选：自动打开浏览器
# xdg-open "$DASHBOARD_FILE" 2>/dev/null || open "$DASHBOARD_FILE" 2>/dev/null