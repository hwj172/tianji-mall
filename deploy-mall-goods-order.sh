#!/bin/bash
# 部署最新 mall-goods-order 到 VM（应用任务 1-3：直购/CartItemDTO.skuSpecs/分页 total）
# 用法: bash deploy-mall-goods-order.sh
set -e

SSH_ARGS="-i C:/Users/黄文杰/.ssh/id_rsa -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=C:/Users/黄文杰/.ssh/known_hosts"
VM="root@192.168.150.11"
JAR=/d/TEST/tianji-mall/mall-goods-order/target/mall-goods-order-0.0.1-SNAPSHOT.jar

echo "==> [1/4] 确保 VM target 目录"
ssh $SSH_ARGS $VM "mkdir -p /root/tianji-mall/mall-goods-order/target"

echo "==> [2/4] 传输新 jar（约 129MB）"
scp $SSH_ARGS "$JAR" "$VM:/root/tianji-mall/mall-goods-order/target/"

echo "==> [3/4] 重建 mall-goods-order 镜像"
ssh $SSH_ARGS $VM "cd /root/tianji-mall/docker && docker compose -f docker-compose.apps.yml build mall-goods-order"

echo "==> [4/4] 重建容器并等待就绪"
ssh $SSH_ARGS $VM "cd /root/tianji-mall/docker && docker compose -f docker-compose.apps.yml up -d mall-goods-order"
sleep 45
ssh $SSH_ARGS $VM "docker ps --filter name=mall-goods-order --format '{{.Names}}  {{.Status}}'"

echo "==> 完成"
