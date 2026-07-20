# 天机商城 压测方案

验证 Sentinel 限流在真实负载下的表现。

## 前置条件

1. 所有服务启动（gateway + 5 个业务模块）
2. Sentinel Dashboard 可访问：http://192.168.150.11:8858（sentinel/sentinel）
3. 测试用户已存在（脚本自动注册）

## 方案一：curl 并发脚本（推荐，无需安装）

```bash
# 默认参数：localhost:8080，并发10，每场景100请求
bash loadtest/load-test.sh

# 自定义目标
bash loadtest/load-test.sh 192.168.1.100 8080

# 自定义并发和请求量
CONCURRENT=20 TOTAL=500 bash loadtest/load-test.sh
```

**测试场景：**
1. 商品列表（公开） — `GET /api/product/list`
2. 商品搜索（公开） — `GET /api/product/list?keyword=手机`
3. 商品详情（公开） — `GET /api/product/1`
4. 购物车列表（需认证） — `GET /api/cart/list`
5. 用户信息（需认证） — `GET /api/user/info`

结果输出到 `loadtest/results/`，包含每个场景的 `min/avg/max` 响应时间和 QPS。

## 方案二：JMeter（标准工具）

### 安装 JMeter

```bash
# 下载（Windows）
# https://jmeter.apache.org/download_jmeter.cgi
# 解压到任意目录，如 D:\apache-jmeter-5.6.3

# 启动 GUI
D:\apache-jmeter-5.6.3\bin\jmeter.bat
```

### 运行测试

1. 打开 JMeter GUI
2. File → Open → 选择 `loadtest/tianji-mall-load-test.jmx`
3. 确认 `HOST` 和 `PORT` 变量（默认 `localhost:8080`）
4. 点击绿色三角 ▶ 运行
5. 查看"汇总报告"和"结果树"

**CLI 模式（无 GUI）：**
```bash
jmeter -n -t loadtest/tianji-mall-load-test.jmx -l loadtest/results/jmeter-result.jtl -e -o loadtest/results/html-report
```

## Sentinel 限流验证步骤

### 1. 建立基线（无限流）

- 确保 Sentinel 中没有针对测试端点的限流规则
- 运行压测，记录基线 QPS

### 2. 配置限流规则

在 Sentinel Dashboard (http://192.168.150.11:8858)：
1. 进入 **簇点链路** → 找到 `/api/product/list`
2. 点击 **流控** → **新增流控规则**
3. 设置：
   - 资源名：`/api/product/list`
   - 阈值类型：QPS
   - 单机阈值：10
   - 流控模式：直接
4. 点击新增

### 3. 验证限流生效

- 重新运行压测（并发 20，请求 200→QPS 会超过 10）
- 观察：
  - 压测脚本：部分请求返回 429 或 block 提示
  - Sentinel Dashboard：实时监控 → 通过 QPS / 拒绝 QPS

### 4. 验证熔断降级

1. Sentinel Dashboard → **降级规则** → 新增
2. 设置慢调用比例 / 异常比例阈值
3. 模拟慢请求或异常，观察熔断开启

### 5. 恢复

- 删除限流/降级规则
- 观察流量恢复正常

## 关键指标

| 指标 | 无 Sentinel | 有 Sentinel (QPS=10) |
|------|------------|----------------------|
| 平均响应时间 | — | — |
| 99% 响应时间 | — | — |
| 通过 QPS | — | ≤10 |
| 拒绝 QPS | 0 | 超出部分 |
| 错误率 | 0% | 超出部分返回限流 |

## 文件说明

| 文件 | 说明 |
|------|------|
| `load-test.sh` | curl 并发脚本，立即可用 |
| `tianji-mall-load-test.jmx` | JMeter 测试计划，4 个线程组 |
| `results/` | 测试结果输出目录 |
