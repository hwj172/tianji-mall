#!/bin/bash
# deploy.sh — 一键部署到 Linux VM
# 用法:
#   ./deploy.sh              # 部署全部 6 个服务
#   ./deploy.sh gateway      # 只部署指定服务
#   ./deploy.sh --test       # 部署前先跑测试
#   ./deploy.sh -t gateway   # 测试 + 部署指定服务

set -e

VM="root@192.168.150.11"
VM_PROJECT="/root/tianji-mall"
SERVICES=(gateway user-service mall-goods-order pay-service mcp-server ai-chat-service)
COMPOSE_FILE="docker-compose.apps.yml"

RUN_TESTS=false
TARGET_SERVICE=""

# --- 解析参数 ---
for arg in "$@"; do
  case $arg in
    --test|-t) RUN_TESTS=true ;;
    *) TARGET_SERVICE="$arg" ;;
  esac
done

# --- 步骤 1: 编译 ---
echo "╔══════════════════════════════════════╗"
echo "║  [1/4]  Maven 打包                   ║"
echo "╚══════════════════════════════════════╝"

if $RUN_TESTS; then
  echo "→ 运行测试 + 打包..."
  mvn package -q
else
  echo "→ 跳过测试，仅打包..."
  mvn package -DskipTests -q
fi
echo "✓ 打包完成"

# --- 步骤 2: 上传到 VM ---
echo ""
echo "╔══════════════════════════════════════╗"
echo "║  [2/4]  上传文件到 VM                ║"
echo "╚══════════════════════════════════════╝"

deploy_service() {
  local svc=$1
  local jar_file=$(ls $svc/target/$svc-*.jar 2>/dev/null | head -1)
  if [ -z "$jar_file" ]; then
    echo "⚠ 找不到 $svc 的 JAR，跳过"
    return
  fi
  ssh $VM "mkdir -p $VM_PROJECT/$svc/target" 2>/dev/null
  echo "  ↑ $svc: $(basename $jar_file)"
  scp -q "$jar_file" $VM:$VM_PROJECT/$svc/target/
  scp -q "$svc/Dockerfile" $VM:$VM_PROJECT/$svc/Dockerfile 2>/dev/null
}

if [ -n "$TARGET_SERVICE" ]; then
  echo "→ 只部署: $TARGET_SERVICE"
  deploy_service "$TARGET_SERVICE"
else
  echo "→ 全部 6 个服务"
  for svc in "${SERVICES[@]}"; do
    deploy_service "$svc"
  done
fi

# 同步 docker-compose 配置
echo "  ↑ docker-compose.apps.yml"
scp -q docker/$COMPOSE_FILE $VM:$VM_PROJECT/docker/

echo "✓ 上传完成"

# --- 步骤 3: 重建镜像 & 启动 ---
echo ""
echo "╔══════════════════════════════════════╗"
echo "║  [3/4]  Docker 重建 & 启动           ║"
echo "╚══════════════════════════════════════╝"

if [ -n "$TARGET_SERVICE" ]; then
  echo "→ 重建 $TARGET_SERVICE..."
  ssh $VM "cd $VM_PROJECT/docker && docker compose -f $COMPOSE_FILE build $TARGET_SERVICE && docker compose -f $COMPOSE_FILE up -d $TARGET_SERVICE"
else
  echo "→ 重建全部服务..."
  ssh $VM "cd $VM_PROJECT/docker && docker compose -f $COMPOSE_FILE build && docker compose -f $COMPOSE_FILE up -d"
fi
echo "✓ 部署完成"

# --- 步骤 4: 状态检查 ---
echo ""
echo "╔══════════════════════════════════════╗"
echo "║  [4/4]  容器状态                     ║"
echo "╚══════════════════════════════════════╝"

sleep 5
ssh $VM "cd $VM_PROJECT/docker && docker compose -f $COMPOSE_FILE ps"

echo ""
echo "═══ 部署完成 ═══"
