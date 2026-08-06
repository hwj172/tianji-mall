#!/bin/bash
# 天机商城 VM 完整恢复脚本（192.168.150.11）
# 用法: bash recover-vm.sh
# 流程: 清 root 残留 -> root 中间件(排除 redis) -> redis -> 等待就绪 -> 重启应用 -> 验证
set -e

SSH_ARGS="-i C:/Users/黄文杰/.ssh/id_rsa -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=C:/Users/黄文杰/.ssh/known_hosts"
VM="root@192.168.150.11"

echo "==> [1/5] 清理 root 残留容器（Created/Exited 混合 + hash 前缀导致 compose 报 Conflict）"
ssh $SSH_ARGS $VM "docker ps -a -q --filter label=com.docker.compose.project=root | xargs -r docker rm -f"

echo "==> [2/5] 启动 root 中间件（排除 redis：root 与 docker 两处 compose 都声明 redis，统一用 docker 项目那个）"
ssh $SSH_ARGS $VM 'cd /root && docker compose up -d $(docker compose config --services | grep -v "^redis$")'

echo "==> [3/5] 启动 redis（docker 项目）"
ssh $SSH_ARGS $VM "cd /root/tianji-mall/docker && docker compose up -d redis"

echo "==> [4/5] 等待 Nacos/MySQL/RocketMQ 就绪（最长 3 分钟）"
ssh $SSH_ARGS $VM '
for i in $(seq 1 60); do
  ok=1
  curl -sf http://localhost:8848/nacos >/dev/null 2>&1 || ok=0
  (echo >/dev/tcp/localhost/3306) 2>/dev/null || ok=0
  (echo >/dev/tcp/localhost/9876) 2>/dev/null || ok=0
  [ "$ok" = 1 ] && echo "中间件就绪（第 ${i} 次探测）" && break
  sleep 3
done
[ "$ok" = 1 ] || echo "警告：等待超时，仍有中间件未就绪，继续尝试重启应用"
'

echo "==> [5/5] 重启 6 个应用服务（连不上中间件而 unhealthy 的会在此恢复）"
ssh $SSH_ARGS $VM "cd /root/tianji-mall/docker && docker compose -f docker-compose.apps.yml restart"

echo "==> 等待应用健康检查（40 秒）..."
sleep 40

echo "==> 验证"
ssh $SSH_ARGS $VM "cd /root/tianji-mall/docker && docker compose ps --format 'table {{.Name}}\t{{.Status}}' && curl -s -o /dev/null -w '首页 HTTP %{http_code}\n' http://localhost:8080/api/home"

echo "==> 完成"
