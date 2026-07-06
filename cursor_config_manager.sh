#!/bin/bash

# Cursor 配置管理脚本

SETTINGS_FILE="$HOME/.config/Cursor/User/settings.json"

# 备份配置
backup_settings() {
  if [ -f "$SETTINGS_FILE" ]; then
    BACKUP_FILE="$HOME/.config/Cursor/User/settings.json.backup.$(date +%Y%m%d_%H%M%S)"
    cp "$SETTINGS_FILE" "$BACKUP_FILE"
    echo "✅ 配置已备份到: $BACKUP_FILE"
  else
    echo "❌ 配置文件不存在"
  fi
}

# 恢复配置
restore_settings() {
  LATEST_BACKUP=$(ls -t ~/.config/Cursor/User/settings.json.backup.* 2>/dev/null | head -1)
  if [ -n "$LATEST_BACKUP" ]; then
    cp "$LATEST_BACKUP" "$SETTINGS_FILE"
    echo "✅ 配置已恢复: $LATEST_BACKUP"
  else
    echo "❌ 没有找到备份文件"
  fi
}

# 查看配置
view_settings() {
  if [ -f "$SETTINGS_FILE" ]; then
    echo "=== Cursor 设置 ==="
    cat "$SETTINGS_FILE" | jq '.' 2>/dev/null || cat "$SETTINGS_FILE"
  else
    echo "❌ 配置文件不存在"
  fi
}

# 修改配置
update_setting() {
  local key=$1
  local value=$2
  
  if [ -f "$SETTINGS_FILE" ]; then
    # 备份
    backup_settings
    
    # 更新配置
    tmp=$(mktemp)
    jq "$key = $value" "$SETTINGS_FILE" > "$tmp" && mv "$tmp" "$SETTINGS_FILE"
    echo "✅ 配置已更新: $key = $value"
    echo "⚠️ 需要重启 Cursor 才能生效"
  else
    echo "❌ 配置文件不存在"
  fi
}

# 安装扩展
install_extension() {
  local ext_id=$1
  echo "安装扩展: $ext_id"
  cursor --install-extension "$ext_id"
}

# 列出扩展
list_extensions() {
  echo "=== 已安装扩展 ==="
  ls -1 ~/.cursor/extensions/ 2>/dev/null | while read ext; do
    echo "  - $ext"
  done
}

# 主菜单
case "$1" in
  backup)
    backup_settings
    ;;
  restore)
    restore_settings
    ;;
  view)
    view_settings
    ;;
  update)
    update_setting "$2" "$3"
    ;;
  install-ext)
    install_extension "$2"
    ;;
  list-ext)
    list_extensions
    ;;
  *)
    echo "用法: $0 {backup|restore|view|update|install-ext|list-ext}"
    echo ""
    echo "示例:"
    echo "  $0 backup              # 备份配置"
    echo "  $0 restore             # 恢复配置"
    echo "  $0 view                # 查看配置"
    echo "  $0 update '.editor.fontSize' 14  # 修改字体大小"
    echo "  $0 install-ext <ext-id>        # 安装扩展"
    echo "  $0 list-ext                    # 列出扩展"
    ;;
esac