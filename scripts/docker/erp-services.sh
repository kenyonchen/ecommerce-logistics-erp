#!/usr/bin/env bash
#
# 跨境大卖ERP —— 依赖服务统一管理脚本
#
# 用法：
#   ./erp-services.sh start       # 一键拉起 MySQL + RocketMQ（后台）
#   ./erp-services.sh stop        # 停止但不删除容器（数据保留）
#   ./erp-services.sh restart     # 重启全部
#   ./erp-services.sh status      # 查看各服务健康状态
#   ./erp-services.sh logs [svc]  # 查看日志（svc=mysql|rmqnamesrv|rmqbroker，默认全部）
#   ./erp-services.sh ps          # 等价于 docker compose ps
#   ./erp-services.sh down        # 停止并删除容器（数据卷保留）
#   ./erp-services.sh reset       # 停止删容器并清空所有持久化数据（慎用！）
#
set -euo pipefail

# 脚本所在目录即为 compose 文件目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
# 自身绝对路径：restart/start 分支会自调用，必须用绝对路径，
# 否则在 cd 到 SCRIPT_DIR 后，$0 的相对路径会解析失败（No such file or directory）
SELF="$SCRIPT_DIR/$(basename "${BASH_SOURCE[0]}")"

# 颜色输出
if [ -t 1 ]; then
  C_RESET='\033[0m'; C_GREEN='\033[32m'; C_YELLOW='\033[33m'; C_RED='\033[31m'; C_CYAN='\033[36m'
else
  C_RESET=''; C_GREEN=''; C_YELLOW=''; C_RED=''; C_CYAN=''
fi

info()  { printf "${C_CYAN}[erp]${C_RESET} %s\n" "$*"; }
ok()    { printf "${C_GREEN}[ok ]${C_RESET} %s\n" "$*"; }
warn()  { printf "${C_YELLOW}[warn]${C_RESET} %s\n" "$*"; }
err()   { printf "${C_RED}[err ]${C_RESET} %s\n" "$*" >&2; }

# 前置检查
need_cmd() { command -v "$1" >/dev/null 2>&1 || { err "未找到命令: $1，请先安装 Docker / Docker Compose。"; exit 1; }; }
need_cmd docker
if ! docker compose version >/dev/null 2>&1; then
  err "未检测到 'docker compose'（V2）。请升级 Docker Desktop 或使用 docker-compose V1（并改写下方调用）。"
  exit 1
fi

# 启动前检测宿主机端口是否被占用。
# 若 3306 等端口被本机其它 MySQL / RocketMQ 占用，docker 会静默发布端口失败，
# 后端连 localhost:3306 时便会抛 Communications link failure，极难定位，故提前拦截。
# 仅在当前没有本栈容器运行时才检测，避免把 Docker 已发布的本栈端口误判为冲突。
PORTS=(3306 9876 10909 10911 10912 8080 8081)
check_ports() {
  if [ -n "$(docker compose -f "$COMPOSE_FILE" ps -q 2>/dev/null)" ]; then
    return 0
  fi
  local occupied=()
  for p in "${PORTS[@]}"; do
    # /dev/tcp 探针：macOS/Linux 通用，不依赖 lsof
    # 用子 shell 内的 exec 探测：成功则子 shell 退出码 0（fd 随子 shell 结束自动关闭，
    # 切勿在父 shell 再 exec 3>&-，否则会因 fd 未打开而报 Bad file descriptor 并在 set -e 下静默退出）
    if (exec 3<>"/dev/tcp/127.0.0.1/$p") 2>/dev/null; then
      occupied+=("$p")
    fi
  done
  if [ "${#occupied[@]}" -gt 0 ]; then
    err "宿主机端口已被占用，docker 将无法发布端口，后端会因此报 Communications link failure："
    printf '%s\n' "${occupied[@]}" | sed 's/^/    - /' >&2
    err "请先停止占用这些端口的其它服务（如本机已有的 MySQL / RocketMQ 容器），或修改 docker-compose.yml 的端口映射后重试。"
    exit 1
  fi
}

cd "$SCRIPT_DIR"

case "${1:-help}" in
  start)
    check_ports
    info "正在启动依赖服务（MySQL 8.0 + RocketMQ 5.3.2）..."
    docker compose -f "$COMPOSE_FILE" up -d
    info "等待 MySQL 健康检查通过..."
    for i in $(seq 1 30); do
      if [ "$(docker inspect -f '{{.State.Health.Status}}' erp-mysql 2>/dev/null)" = "healthy" ]; then
        ok "MySQL 已就绪。"
        break
      fi
      sleep 2
    done
    # 已初始化的数据卷不会重跑初始化脚本，这里幂等补建 root@'%'，
    # 保证宿主机（端口映射来源为非 localhost）的 Spring Boot 能连入，避免 Access denied。
    docker compose -f "$COMPOSE_FILE" exec -T mysql mysql -uroot -p123456 -e \
      "CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY '123456'; GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION; FLUSH PRIVILEGES;" 2>/dev/null \
      && ok "已确保 root@'%' 可远程登录（宿主机可连）。" \
      || warn "root@'%' 授权未完成（若后端报访问拒绝，请手动执行授权）。"
    ok "全部服务已启动。"
    "$SELF" status
    ;;
  stop)
    info "停止服务（保留容器与数据）..."
    docker compose -f "$COMPOSE_FILE" stop
    ok "已停止。"
    ;;
  restart)
    "$SELF" stop
    "$SELF" start
    ;;
  status)
    info "服务状态："
    docker compose -f "$COMPOSE_FILE" ps
    echo
    if docker inspect -f '{{.State.Health.Status}}' erp-mysql >/dev/null 2>&1; then
      printf "  MySQL 健康: ${C_GREEN}%s${C_RESET}\n" "$(docker inspect -f '{{.State.Health.Status}}' erp-mysql)"
    fi
    ;;
  logs)
    svc="${2:-}"
    if [ -n "$svc" ]; then
      docker compose -f "$COMPOSE_FILE" logs -f --tail=200 "$svc"
    else
      docker compose -f "$COMPOSE_FILE" logs -f --tail=200
    fi
    ;;
  ps)
    docker compose -f "$COMPOSE_FILE" ps
    ;;
  down)
    info "停止并删除容器（数据卷保留）..."
    docker compose -f "$COMPOSE_FILE" down
    ok "已清理容器。"
    ;;
  reset)
    warn "将删除容器并清空所有持久化数据（MySQL 数据 / RocketMQ 数据），不可恢复！"
    read -r -p "确认执行 reset? 输入 'yes' 继续: " confirm
    if [ "$confirm" = "yes" ]; then
      docker compose -f "$COMPOSE_FILE" down -v
      ok "已重置所有数据。"
    else
      info "已取消。"
    fi
    ;;
  help|-h|--help|*)
    cat <<EOF
跨境大卖ERP —— 依赖服务管理（MySQL 8.0 + RocketMQ 5.3.2）

  start    一键拉起全部依赖（后台运行）
  stop     停止容器（保留数据与容器）
  restart  重启全部
  status   查看健康状态
  logs     跟踪日志，可加服务名: logs mysql / logs rmqbroker
  ps       列出容器
  down     停止并删除容器（数据卷保留）
  reset    停止删容器并清空所有数据（需确认）
EOF
    ;;
esac
