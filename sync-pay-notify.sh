#!/bin/bash
# 同步 docker-compose.apps.yml 到 VM 并重建 pay-service（应用新支付宝回调 URL）
# 用法: bash sync-pay-notify.sh
set -e

SSH_ARGS="-i C:/Users/黄文杰/.ssh/id_rsa -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=C:/Users/黄文杰/.ssh/known_hosts"
VM="root@192.168.150.11"
LOCAL=/d/TEST/tianji-mall/docker/docker-compose.apps.yml
REMOTE=/root/tianji-mall/docker/docker-compose.apps.yml

echo "==> [1/3] 同步 compose 文件到 VM"
scp $SSH_ARGS "$LOCAL" "$VM:$REMOTE"

echo "==> [2/3] 重建 pay-service（应用新 ALIPAY_NOTIFY_URL）"
ssh $SSH_ARGS $VM "cd /root/tianji-mall/docker && docker compose -f docker-compose.apps.yml up -d pay-service"

echo "==> [3/3] 验证新回调地址生效"
ssh $SSH_ARGS $VM "docker inspect pay-service --format '{{range .Config.Env}}{{println .}}{{end}}' | grep ALIPAY_NOTIFY_URL; cd /root/tianji-mall/docker && docker compose ps pay-service --format '{{.Status}}'"

echo "==> 完成"
