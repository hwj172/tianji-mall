#!/bin/bash
# ============================================================
# 天机商城 压测脚本 — 无需 JMeter，curl + 后台并发即可运行
# ============================================================
# 用法:
#   bash loadtest/load-test.sh                    # 默认: localhost:8080, 并发10, 每场景100请求
#   bash loadtest/load-test.sh 192.168.1.1 8080   # 指定主机和端口
#   CONCURRENT=20 TOTAL=500 bash loadtest/load-test.sh  # 自定义并发数和总请求数
# ============================================================

HOST="${1:-localhost}"
PORT="${2:-8080}"
BASE="http://${HOST}:${PORT}"
CONCURRENT="${CONCURRENT:-10}"
TOTAL="${TOTAL:-100}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

results_dir="loadtest/results"
mkdir -p "$results_dir"
rm -f "$results_dir"/*.tmp

echo "============================================================"
echo " 天机商城 压测工具"
echo " 目标: $BASE"
echo " 并发: $CONCURRENT, 每场景请求数: $TOTAL"
echo " 时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================================"
echo ""

# ---------- helper: run concurrent requests ----------
run_load_test() {
  local name="$1"
  local method="$2"
  local path="$3"
  local token="$4"
  local body="$5"

  echo -e "${YELLOW}[$(date +%H:%M:%S)] 开始: $name${NC}"
  local start_time=$(date +%s%3N)

  # 并发执行
  local per_worker=$((TOTAL / CONCURRENT))
  for i in $(seq 1 $CONCURRENT); do
    (
      for j in $(seq 1 $per_worker); do
        local req_start=$(date +%s%3N)
        if [ "$method" = "POST" ]; then
          if [ -n "$token" ]; then
            curl -s -o /dev/null -w "%{http_code} %{time_total}\n" \
              -X POST "$BASE$path" \
              -H "Content-Type: application/json" \
              -H "Authorization: Bearer $token" \
              -d "$body" >> "$results_dir/${name}.tmp" 2>/dev/null
          else
            curl -s -o /dev/null -w "%{http_code} %{time_total}\n" \
              -X POST "$BASE$path" \
              -H "Content-Type: application/json" \
              -d "$body" >> "$results_dir/${name}.tmp" 2>/dev/null
          fi
        else
          if [ -n "$token" ]; then
            curl -s -o /dev/null -w "%{http_code} %{time_total}\n" \
              "$BASE$path" \
              -H "Authorization: Bearer $token" >> "$results_dir/${name}.tmp" 2>/dev/null
          else
            curl -s -o /dev/null -w "%{http_code} %{time_total}\n" \
              "$BASE$path" >> "$results_dir/${name}.tmp" 2>/dev/null
          fi
        fi
      done
    ) &
  done
  wait

  local end_time=$(date +%s%3N)
  local total_time=$((end_time - start_time))

  # 统计结果
  local total_requests=$(wc -l < "$results_dir/${name}.tmp" 2>/dev/null || echo 0)
  local success=$(grep -c "^200 " "$results_dir/${name}.tmp" 2>/dev/null || echo 0)
  local failed=$((total_requests - success))

  # 响应时间统计 (ms)
  local times=$(awk '{print int($2 * 1000)}' "$results_dir/${name}.tmp" 2>/dev/null)
  local min_time=$(echo "$times" | sort -n | head -1)
  local max_time=$(echo "$times" | sort -n | tail -1)
  local avg_time=$(echo "$times" | awk '{sum+=$1} END {printf "%.1f", sum/NR}')

  local qps=$(awk "BEGIN {printf \"%.1f\", $total_requests / ($total_time / 1000)}")

  echo -e "  ${GREEN}✓${NC} 总请求: $total_requests | 成功: $success | 失败: $failed"
  echo "  响应时间: min=${min_time}ms  avg=${avg_time}ms  max=${max_time}ms"
  echo "  QPS: $qps | 总耗时: ${total_time}ms"
  echo ""

  # 保存汇总
  echo "$name,$total_requests,$success,$failed,$min_time,$avg_time,$max_time,$qps" >> "$results_dir/summary.csv"
}

# ---------- 获取 JWT Token ----------
echo -e "${YELLOW}[$(date +%H:%M:%S)] 获取测试 Token...${NC}"
# 先注册测试用户（可能已存在，忽略错误）
curl -s -X POST "$BASE/api/user/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"loadtest","password":"test123456"}' > /dev/null 2>&1

# 登录获取 token
TOKEN=$(curl -s -X POST "$BASE/api/user/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"loadtest","password":"test123456"}' | \
  grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo -e "  ${RED}✗ 无法获取 Token，认证相关测试将跳过${NC}"
  echo ""
fi

# ---------- 场景 1: 公开端点 — 商品列表 ----------
run_load_test "product_list" "GET" "/api/product/list?page=1&size=20" "" ""

# ---------- 场景 2: 公开端点 — 商品搜索 ----------
run_load_test "product_search" "GET" "/api/product/list?keyword=手机&page=1&size=10" "" ""

# ---------- 场景 3: 公开端点 — 商品详情 ----------
run_load_test "product_detail" "GET" "/api/product/1" "" ""

# ---------- 场景 4: 认证端点 — 购物车列表 ----------
if [ -n "$TOKEN" ]; then
  run_load_test "cart_list" "GET" "/api/cart/list" "$TOKEN" ""
fi

# ---------- 场景 5: 认证端点 — 用户信息 ----------
if [ -n "$TOKEN" ]; then
  run_load_test "user_info" "GET" "/api/user/info" "$TOKEN" ""
fi

# ---------- 汇总 ----------
echo "============================================================"
echo -e " ${GREEN}压测完成${NC}"
echo " 详细结果: $results_dir/"
echo " 汇总 CSV: $results_dir/summary.csv"
echo "============================================================"
echo ""
echo "场景,总请求,成功,失败,最小响应(ms),平均响应(ms),最大响应(ms),QPS"
cat "$results_dir/summary.csv" 2>/dev/null

# ---------- Sentinel 提示 ----------
echo ""
echo "Sentinel Dashboard: http://192.168.150.11:8858 (sentinel/sentinel)"
echo "压测过程中可在 Dashboard 实时查看 QPS、限流、降级情况"
echo ""
echo "配置限流规则 (Sentinel Dashboard):"
echo "  1. 进入 簇点链路 → 选择 /api/product/list"
echo "  2. 流控规则 → 新增 → QPS=10 → 生效"
echo "  3. 重新运行压测脚本，观察限流效果"
