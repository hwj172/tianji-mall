#!/bin/bash
# 监控 VM 微服务健康 + 关键接口存活。
# 用法: bash monitor-services.sh          # 仅打印状态
#       bash monitor-services.sh --alert  # 任一异常 exit 1（供 cron 告警）
SSH_ARGS="-i C:/Users/黄文杰/.ssh/id_rsa -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=C:/Users/黄文杰/.ssh/known_hosts"
VM="root@192.168.150.11"
GATEWAY="http://192.168.150.11:8080"
ALERT=0
[ "$1" = "--alert" ] && ALERT=1
FAIL=0

echo "===== 容器健康 ====="
for svc in gateway user-service mall-goods-order pay-service mcp-server ai-chat-service; do
  status=$(ssh $SSH_ARGS $VM "docker inspect --format '{{.State.Health.Status}}' $svc" 2>/dev/null)
  echo "  $svc: ${status:-unknown}"
  [ "$status" = "healthy" ] || FAIL=1
done

echo "===== 关键接口 ====="
for path in "/api/home" "/api/product/list?page=1&size=5" "/api/product/search/hot" "/api/region/tree"; do
  code=$(curl -s -o /dev/null -w "%{http_code}" -m 10 "$GATEWAY$path")
  echo "  GET $path → $code"
  [ "$code" = "200" ] || FAIL=1
done

if [ $FAIL -eq 0 ]; then
  echo "✅ 全部正常"
else
  echo "⚠️ 存在异常服务/接口"
  [ $ALERT -eq 1 ] && exit 1
fi
