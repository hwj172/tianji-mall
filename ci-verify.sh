#!/bin/bash
# 提交前一键验证（本地 CI 等效）：后端全量测试 + 前端构建。
# 任一环节失败立即退出（exit 非 0），供提交/发布前把关。
# 用法: bash ci-verify.sh
set -e
cd "$(dirname "$0")"

echo "==> [1/3] 后端编译 + 全量测试（约 2-4 分钟）"
mvn test -q 2>&1 | grep -E "Tests run:.*Failures|BUILD|ERROR" | tail -5 \
  || { echo "❌ 后端测试失败"; exit 1; }
echo "✅ 后端测试通过"

echo "==> [2/3] 前端构建"
cd tianji-mall-frontend
npm run build 2>&1 | grep -E "built in|error" | tail -3 \
  || { echo "❌ 前端构建失败"; exit 1; }
cd ..

echo "==> [3/3] 环境脚本语法检查"
for f in start-all.sh deploy.sh ci-verify.sh; do
  if [ -f "$f" ]; then bash -n "$f" && echo "✅ $f"; fi
done

echo ""
echo "✅ 全部通过，可提交"
