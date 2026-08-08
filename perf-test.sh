#!/bin/bash
# 核心接口并发压测：并发 curl 测延迟（平均/最小/最大）+ QPS。
# 用法: bash perf-test.sh [endpoint] [concurrency] [total]
# 例:   bash perf-test.sh /api/home 20 100
BASE="http://192.168.150.11:8080"
EP="${1:-/api/home}"
CONC="${2:-20}"
TOTAL="${3:-100}"

echo "压测: GET $EP  (并发 $CONC, 共 $TOTAL 请求)"
START=$(date +%s.%N)
seq 1 "$TOTAL" | xargs -P "$CONC" -I{} curl -s -o /dev/null -w "%{time_total}\n" -m 20 "$BASE$EP" > "/tmp/perf_$$.out"
END=$(date +%s.%N)

awk -v s="$START" -v e="$END" -v total="$TOTAL" '
  { sum += $1; if ($1 > max) max = $1; if (min == 0 || $1 < min) min = $1; n++ }
  END {
    elapsed = e - s;
    printf "完成:%d/%d 耗时:%.2fs 失败:%d\n", n, total, elapsed, total - n;
    if (n > 0) printf "平均:%.3fs 最小:%.3fs 最大:%.3fs\nQPS:%.0f\n", sum/n, min, max, n/elapsed;
  }' "/tmp/perf_$$.out"
rm -f "/tmp/perf_$$.out"
