#!/bin/bash
# 天机商城 关机后一键启动（本机 Windows Git Bash）
# 用法: bash start-all.sh
# 流程: SSH 隧道 -> VM 恢复(中间件+微服务, recover-vm.sh) -> 前端 dev server -> 验证
set -e

SSH_ARGS="-i C:/Users/黄文杰/.ssh/id_rsa -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=C:/Users/黄文杰/.ssh/known_hosts"
VM="root@192.168.150.11"

echo "╔══════════════════════════════════════╗"
echo "║  天机商城 一键启动                    ║"
echo "╚══════════════════════════════════════╝"

# [1/4] SSH 隧道（本地 8080 → VM 网关，供浏览器/前端代理访问）
if (echo >/dev/tcp/127.0.0.1/8080) 2>/dev/null; then
  echo "==> [1/4] 8080 已在监听，跳过隧道"
else
  echo "==> [1/4] 建立 SSH 隧道 8080 → VM 网关（后台）"
  ssh -f -N $SSH_ARGS -L 8080:192.168.150.11:8080 $VM
fi

# [2/4] VM 恢复（清残留 -> 中间件 -> redis -> 应用重启，约 2-3 分钟）
echo "==> [2/4] 恢复 VM 中间件 + 微服务..."
bash recover-vm.sh

# [3/4] 前端 dev server
if (echo >/dev/tcp/127.0.0.1/5173) 2>/dev/null; then
  echo "==> [3/4] 5173 已在监听，跳过前端"
else
  echo "==> [3/4] 启动前端 dev server（后台，日志 /tmp/vite-dev.log）"
  (cd tianji-mall-frontend && npm run dev > /tmp/vite-dev.log 2>&1 &)
fi

# [4/4] 验证
echo "==> [4/4] 验证"
sleep 5
curl -s -o /dev/null -w "gateway /api/home: HTTP %{http_code}\n" http://localhost:8080/api/home || echo "gateway 未就绪"
echo ""
echo "前端:   http://localhost:5173"
echo "Nacos:  http://192.168.150.11:8848/nacos"
echo "Sentinel: http://192.168.150.11:8858 (sentinel/sentinel)"
echo "注: 支付宝支付回调需手动启动 natapp（隧道指向 127.0.0.1:8080），见 memory 的 [[natapp-ssh-tunnel]]"
echo "==> 一键启动完成"
