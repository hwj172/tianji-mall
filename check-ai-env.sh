#!/bin/bash
# 检查 ai-chat-service 的 API key 配置情况（不打印密钥值，只看长度）
# 用法: bash check-ai-env.sh
SSH_ARGS="-i C:/Users/黄文杰/.ssh/id_rsa -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=C:/Users/黄文杰/.ssh/known_hosts"
VM="root@192.168.150.11"

ssh $SSH_ARGS $VM "
C=\$(docker ps -qf name=ai-chat)
echo 'container=' \$C
echo '--- key lengths (values hidden) ---'
docker inspect \$C --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -E '^(DEEPSEEK_API_KEY|SILICONFLOW_API_KEY)=' | awk -F= '{print \$1 \" len=\" length(\$2)}'
echo '--- /root/tianji-mall/docker/.env ---'
if [ -f /root/tianji-mall/docker/.env ]; then echo 'EXISTS'; else echo 'MISSING'; fi
"
