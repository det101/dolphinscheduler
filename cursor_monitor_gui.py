#!/usr/bin/env python3
"""
Cursor IDE GUI 监控面板
启动方式: python3 cursor_monitor_gui.py
访问地址: http://localhost:8080
"""

import json
import os
import subprocess
import time
from datetime import datetime
from http.server import HTTPServer, SimpleHTTPRequestHandler
import threading
import re

class CursorMonitor:
    """Cursor IDE 监控类"""
    
    @staticmethod
    def get_process_info():
        """获取进程信息"""
        try:
            result = subprocess.run(
                ['ps', 'aux'],
                capture_output=True,
                text=True,
                timeout=5
            )
            
            processes = []
            for line in result.stdout.split('\n'):
                if '/usr/share/cursor/cursor' in line and 'grep' not in line:
                    parts = line.split()
                    if len(parts) >= 11:
                        processes.append({
                            'user': parts[0],
                            'pid': int(parts[1]),
                            'cpu': float(parts[2]),
                            'mem': float(parts[3]),
                            'vsz': parts[4],
                            'rss': parts[5],
                            'stat': parts[7],
                            'start': parts[8],
                            'time': parts[9],
                            'command': ' '.join(parts[10:])
                        })
            
            return processes
        except Exception as e:
            return []
    
    @staticmethod
    def get_workspace_info():
        """获取工作空间信息"""
        workspace_dir = os.path.expanduser('~/.config/Cursor/User/workspaceStorage')
        try:
            if os.path.exists(workspace_dir):
                # 获取最近修改的工作区
                workspaces = []
                for item in os.listdir(workspace_dir):
                    item_path = os.path.join(workspace_dir, item)
                    if os.path.isdir(item_path):
                        stat = os.stat(item_path)
                        workspaces.append({
                            'name': item,
                            'path': item_path,
                            'modified': datetime.fromtimestamp(stat.st_mtime).strftime('%Y-%m-%d %H:%M:%S'),
                            'size': subprocess.run(
                                ['du', '-sh', item_path],
                                capture_output=True,
                                text=True
                            ).stdout.split()[0]
                        })
                
                # 按修改时间排序
                workspaces.sort(key=lambda x: x['modified'], reverse=True)
                return workspaces[:5]  # 返回最近5个
        except:
            pass
        return []
    
    @staticmethod
    def get_extensions():
        """获取扩展列表"""
        ext_dir = os.path.expanduser('~/.cursor/extensions')
        try:
            if os.path.exists(ext_dir):
                extensions = []
                for item in os.listdir(ext_dir):
                    if item not in ['extensions.json', '.obsolete']:
                        extensions.append(item)
                return sorted(extensions)
        except:
            pass
        return []
    
    @staticmethod
    def get_settings():
        """获取配置信息"""
        settings_file = os.path.expanduser('~/.config/Cursor/User/settings.json')
        try:
            if os.path.exists(settings_file):
                with open(settings_file, 'r') as f:
                    return json.load(f)
        except:
            pass
        return {}
    
    @staticmethod
    def get_language_servers():
        """获取语言服务器状态"""
        try:
            result = subprocess.run(
                ['ps', 'aux'],
                capture_output=True,
                text=True,
                timeout=5
            )
            
            servers = {
                'java': 0,
                'spring_boot': 0,
                'python': 0,
                'typescript': 0,
                'other': 0
            }
            
            for line in result.stdout.split('\n'):
                if 'jdt.ls' in line:
                    servers['java'] += 1
                if 'spring-boot-language-server' in line:
                    servers['spring_boot'] += 1
                if 'pyls' in line or 'python-language-server' in line:
                    servers['python'] += 1
                if 'typescript-language-server' in line or 'tsserver' in line:
                    servers['typescript'] += 1
            
            return servers
        except:
            return {}
    
    @staticmethod
    def get_system_resources():
        """获取系统资源"""
        try:
            # CPU 使用率
            cpu_result = subprocess.run(
                ['top', '-bn1'],
                capture_output=True,
                text=True,
                timeout=5
            )
            cpu_line = [l for l in cpu_result.stdout.split('\n') if '%Cpu(s)' in l]
            cpu_usage = 0
            if cpu_line:
                match = re.search(r'(\d+\.\d+)\s+id', cpu_line[0])
                if match:
                    cpu_usage = 100 - float(match.group(1))
            
            # 内存使用率
            mem_result = subprocess.run(
                ['free', '-m'],
                capture_output=True,
                text=True,
                timeout=5
            )
            mem_lines = mem_result.stdout.split('\n')
            if len(mem_lines) >= 2:
                mem_parts = mem_lines[1].split()
                total_mem = int(mem_parts[1])
                used_mem = int(mem_parts[2])
                mem_usage = (used_mem / total_mem) * 100
            else:
                mem_usage = 0
            
            # 磁盘使用率
            disk_result = subprocess.run(
                ['df', '-h', '/'],
                capture_output=True,
                text=True,
                timeout=5
            )
            disk_line = disk_result.stdout.split('\n')[1]
            disk_parts = disk_line.split()
            disk_usage = disk_parts[4].replace('%', '')
            
            return {
                'cpu': round(cpu_usage, 1),
                'memory': round(mem_usage, 1),
                'disk': int(disk_usage)
            }
        except:
            return {'cpu': 0, 'memory': 0, 'disk': 0}


class MonitorHandler(SimpleHTTPRequestHandler):
    """HTTP 请求处理器"""
    
    def do_GET(self):
        """处理 GET 请求"""
        if self.path == '/':
            self.send_html()
        elif self.path == '/api/status':
            self.send_json_status()
        elif self.path == '/api/processes':
            self.send_json_processes()
        elif self.path == '/api/workspaces':
            self.send_json_workspaces()
        elif self.path == '/api/extensions':
            self.send_json_extensions()
        elif self.path == '/api/settings':
            self.send_json_settings()
        else:
            self.send_error(404)
    
    def send_html(self):
        """发送 HTML 页面"""
        html = self.generate_html()
        self.send_response(200)
        self.send_header('Content-type', 'text/html; charset=utf-8')
        self.end_headers()
        self.wfile.write(html.encode('utf-8'))
    
    def send_json_status(self):
        """发送 JSON 状态"""
        monitor = CursorMonitor()
        status = {
            'timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'running': len(monitor.get_process_info()) > 0,
            'process_count': len(monitor.get_process_info()),
            'system': monitor.get_system_resources(),
            'language_servers': monitor.get_language_servers()
        }
        
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(status, indent=2).encode('utf-8'))
    
    def send_json_processes(self):
        """发送进程信息"""
        monitor = CursorMonitor()
        processes = monitor.get_process_info()
        
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(processes, indent=2).encode('utf-8'))
    
    def send_json_workspaces(self):
        """发送工作空间信息"""
        monitor = CursorMonitor()
        workspaces = monitor.get_workspace_info()
        
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(workspaces, indent=2).encode('utf-8'))
    
    def send_json_extensions(self):
        """发送扩展信息"""
        monitor = CursorMonitor()
        extensions = monitor.get_extensions()
        
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(extensions, indent=2).encode('utf-8'))
    
    def send_json_settings(self):
        """发送配置信息"""
        monitor = CursorMonitor()
        settings = monitor.get_settings()
        
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(settings, indent=2).encode('utf-8'))
    
    def generate_html(self):
        """生成 HTML 页面"""
        return '''<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Cursor IDE 监控面板</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 20px;
        }
        .container {
            max-width: 1400px;
            margin: 0 auto;
        }
        h1 {
            color: white;
            text-align: center;
            margin-bottom: 30px;
            font-size: 2.5em;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
        }
        .dashboard {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin-bottom: 20px;
        }
        .card {
            background: white;
            border-radius: 15px;
            padding: 20px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.2);
            transition: transform 0.3s;
        }
        .card:hover {
            transform: translateY(-5px);
        }
        .card h2 {
            color: #333;
            margin-bottom: 15px;
            padding-bottom: 10px;
            border-bottom: 2px solid #667eea;
        }
        .status-running {
            color: #4caf50;
            font-weight: bold;
            font-size: 1.2em;
        }
        .status-stopped {
            color: #f44336;
            font-weight: bold;
            font-size: 1.2em;
        }
        .metric {
            margin: 10px 0;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .metric-label {
            color: #666;
            font-weight: 500;
        }
        .metric-value {
            font-weight: bold;
            color: #333;
        }
        .progress-bar {
            background: #e0e0e0;
            border-radius: 10px;
            height: 20px;
            overflow: hidden;
            margin-top: 5px;
        }
        .progress-fill {
            height: 100%;
            transition: width 0.5s;
            border-radius: 10px;
        }
        .cpu-normal { background: linear-gradient(90deg, #4caf50, #8bc34a); }
        .cpu-high { background: linear-gradient(90deg, #ff9800, #ffc107); }
        .mem-normal { background: linear-gradient(90deg, #2196f3, #03a9f4); }
        .mem-high { background: linear-gradient(90deg, #f44336, #e91e63); }
        .process-list {
            max-height: 300px;
            overflow-y: auto;
        }
        .process-item {
            padding: 10px;
            margin: 5px 0;
            background: #f5f5f5;
            border-radius: 8px;
            font-size: 0.9em;
        }
        .extension-list {
            max-height: 400px;
            overflow-y: auto;
        }
        .extension-item {
            padding: 8px;
            margin: 3px 0;
            background: #f5f5f5;
            border-radius: 5px;
            font-size: 0.85em;
        }
        .refresh-btn {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            padding: 10px 20px;
            border-radius: 25px;
            cursor: pointer;
            font-size: 1em;
            margin: 10px 0;
            transition: transform 0.2s;
        }
        .refresh-btn:hover {
            transform: scale(1.05);
        }
        .timestamp {
            color: #999;
            font-size: 0.9em;
            text-align: center;
            margin-top: 10px;
        }
        .language-server {
            display: inline-block;
            padding: 5px 10px;
            margin: 3px;
            background: #e3f2fd;
            border-radius: 15px;
            font-size: 0.85em;
        }
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }
        .loading {
            animation: pulse 1.5s infinite;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🖥️ Cursor IDE 监控面板</h1>
        
        <div class="dashboard">
            <!-- 状态卡片 -->
            <div class="card">
                <h2>📊 运行状态</h2>
                <div id="status-content" class="loading">加载中...</div>
                <button class="refresh-btn" onclick="refreshStatus()">🔄 刷新</button>
                <div class="timestamp" id="timestamp"></div>
            </div>
            
            <!-- 系统资源 -->
            <div class="card">
                <h2>💻 系统资源</h2>
                <div id="system-content" class="loading">加载中...</div>
            </div>
            
            <!-- 语言服务器 -->
            <div class="card">
                <h2>🔧 语言服务器</h2>
                <div id="servers-content" class="loading">加载中...</div>
            </div>
            
            <!-- 进程列表 -->
            <div class="card">
                <h2>⚙️ 进程列表</h2>
                <div id="processes-content" class="loading process-list">加载中...</div>
            </div>
            
            <!-- 工作空间 -->
            <div class="card">
                <h2>📁 工作空间</h2>
                <div id="workspaces-content" class="loading">加载中...</div>
            </div>
            
            <!-- 扩展列表 -->
            <div class="card">
                <h2>🔌 已安装扩展</h2>
                <div id="extensions-content" class="loading extension-list">加载中...</div>
            </div>
        </div>
    </div>
    
    <script>
        // 自动刷新间隔（毫秒）
        const REFRESH_INTERVAL = 5000;
        
        // 页面加载时获取数据
        window.onload = function() {
            refreshAll();
            setInterval(refreshAll, REFRESH_INTERVAL);
        };
        
        // 刷新所有数据
        function refreshAll() {
            refreshStatus();
            refreshProcesses();
            refreshWorkspaces();
            refreshExtensions();
        }
        
        // 刷新状态
        async function refreshStatus() {
            try {
                const response = await fetch('/api/status');
                const data = await response.json();
                
                const statusHtml = `
                    <div class="metric">
                        <span class="metric-label">状态:</span>
                        <span class="${data.running ? 'status-running' : 'status-stopped'}">
                            ${data.running ? '✅ 运行中' : '❌ 已停止'}
                        </span>
                    </div>
                    <div class="metric">
                        <span class="metric-label">进程数:</span>
                        <span class="metric-value">${data.process_count}</span>
                    </div>
                `;
                
                document.getElementById('status-content').innerHTML = statusHtml;
                document.getElementById('timestamp').textContent = '更新时间: ' + data.timestamp;
                
                // 更新系统资源
                const systemHtml = `
                    <div class="metric">
                        <span class="metric-label">CPU 使用率:</span>
                        <span class="metric-value">${data.system.cpu}%</span>
                    </div>
                    <div class="progress-bar">
                        <div class="progress-fill ${data.system.cpu > 80 ? 'cpu-high' : 'cpu-normal'}" 
                             style="width: ${data.system.cpu}%"></div>
                    </div>
                    
                    <div class="metric">
                        <span class="metric-label">内存使用率:</span>
                        <span class="metric-value">${data.system.memory}%</span>
                    </div>
                    <div class="progress-bar">
                        <div class="progress-fill ${data.system.memory > 80 ? 'mem-high' : 'mem-normal'}" 
                             style="width: ${data.system.memory}%"></div>
                    </div>
                    
                    <div class="metric">
                        <span class="metric-label">磁盘使用率:</span>
                        <span class="metric-value">${data.system.disk}%</span>
                    </div>
                    <div class="progress-bar">
                        <div class="progress-fill ${data.system.disk > 80 ? 'mem-high' : 'cpu-normal'}" 
                             style="width: ${data.system.disk}%"></div>
                    </div>
                `;
                document.getElementById('system-content').innerHTML = systemHtml;
                
                // 更新语言服务器
                const serversHtml = Object.entries(data.language_servers)
                    .map(([name, count]) => 
                        `<span class="language-server">${name}: ${count}</span>`
                    ).join('');
                document.getElementById('servers-content').innerHTML = serversHtml || '无运行中的语言服务器';
                
            } catch (error) {
                document.getElementById('status-content').innerHTML = '❌ 加载失败';
            }
        }
        
        // 刷新进程列表
        async function refreshProcesses() {
            try {
                const response = await fetch('/api/processes');
                const processes = await response.json();
                
                const html = processes.map(p => `
                    <div class="process-item">
                        <strong>PID: ${p.pid}</strong> | CPU: ${p.cpu}% | MEM: ${p.mem}%<br>
                        <small>${p.command.substring(0, 80)}...</small>
                    </div>
                `).join('');
                
                document.getElementById('processes-content').innerHTML = html || '无进程';
            } catch (error) {
                document.getElementById('processes-content').innerHTML = '❌ 加载失败';
            }
        }
        
        // 刷新工作空间
        async function refreshWorkspaces() {
            try {
                const response = await fetch('/api/workspaces');
                const workspaces = await response.json();
                
                const html = workspaces.map(w => `
                    <div class="process-item">
                        <strong>${w.name}</strong><br>
                        <small>大小: ${w.size} | 修改: ${w.modified}</small>
                    </div>
                `).join('');
                
                document.getElementById('workspaces-content').innerHTML = html || '无工作空间';
            } catch (error) {
                document.getElementById('workspaces-content').innerHTML = '❌ 加载失败';
            }
        }
        
        // 刷新扩展列表
        async function refreshExtensions() {
            try {
                const response = await fetch('/api/extensions');
                const extensions = await response.json();
                
                const html = extensions.map(ext => 
                    `<div class="extension-item">${ext}</div>`
                ).join('');
                
                document.getElementById('extensions-content').innerHTML = html || '无扩展';
            } catch (error) {
                document.getElementById('extensions-content').innerHTML = '❌ 加载失败';
            }
        }
    </script>
</body>
</html>'''
    
    def log_message(self, format, *args):
        """禁用日志输出"""
        pass


def run_server(port=8080):
    """运行服务器"""
    server_address = ('', port)
    httpd = HTTPServer(server_address, MonitorHandler)
    
    print(f"""
╔══════════════════════════════════════════════════════════╗
║        🖥️  Cursor IDE 监控面板已启动                    ║
╠══════════════════════════════════════════════════════════╣
║  访问地址: http://localhost:{port}                        ║
║  自动刷新: 每 5 秒                                        ║
║  按 Ctrl+C 停止服务器                                    ║
╚══════════════════════════════════════════════════════════╝
    """)
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n\n👋 服务器已停止")
        httpd.server_close()


if __name__ == '__main__':
    run_server()
