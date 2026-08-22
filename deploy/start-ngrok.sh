#!/bin/bash
# 一键启动 ngrok 让老师公网访问天机商城（先确保 VM nginx + 本地隧道，再起 ngrok）
NGROK="/c/Users/黄文杰/AppData/Local/Microsoft/WinGet/Packages/Ngrok.Ngrok_Microsoft.Winget.Source_8wekyb3d8bbwe/ngrok.exe"
SSH_ARGS="-i C:/Users/黄文杰/.ssh/id_rsa -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=C:/Users/黄文杰/.ssh/known_hosts"
VM="root@192.168.150.11"

[ -f "$NGROK" ] || NGROK="ngrok"

echo "==> [1/3] 确保 VM nginx 容器在跑"
ssh $SSH_ARGS $VM "cd /root/tianji-mall/docker && docker compose -f docker-compose.apps.yml up -d nginx 2>&1 | tail -1"

echo "==> [2/3] 确保本地 8088 -> VM:8088 SSH 隧道"
if (echo >/dev/tcp/127.0.0.1/8088) 2>/dev/null; then
  echo "   8088 已在监听(隧道沿用)"
else
  ssh -f -N $SSH_ARGS -L 8088:192.168.150.11:8088 $VM
  sleep 3
fi

echo "==> [3/3] 启动 ngrok http 隧道 -> 本地8088"
pkill -f "ngrok http" 2>/dev/null; sleep 1
"$NGROK" http 8088 --log stdout > /tmp/ngrok.log 2>&1 &
sleep 8
URL=$(curl -s http://127.0.0.1:4040/api/tunnels 2>/dev/null | grep -oE '"public_url":"https://[^"]+"' | head -1 | cut -d'"' -f4)

if [ -n "$URL" ]; then
  echo ""
  echo "=============================================="
  echo " 老师访问地址: $URL"
  echo "=============================================="
else
  echo "ERROR: 未拿到隧道地址，查看日志 /tmp/ngrok.log"
  tail -5 /tmp/ngrok.log
fi
