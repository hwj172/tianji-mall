# SkyWalking Java Agent

## 下载 Agent

> 注意：SkyWalking Java Agent 是独立版本线（与 OAP 版本号不同），9.2.0 兼容 OAP 9.7.0。

```bash
curl -L https://archive.apache.org/dist/skywalking/java-agent/9.2.0/apache-skywalking-java-agent-9.2.0.tgz -o /tmp/sw-agent.tgz
mkdir -p skywalking/agent
tar xzf /tmp/sw-agent.tgz -C skywalking/
mv skywalking/skywalking-agent/* skywalking/agent/
rmdir skywalking/skywalking-agent
```

## IDEA VM Options 模板

每个服务的 Run Configuration → Modify options → Add VM options：

```
-javaagent:D:\TEST\tianji-mall\skywalking\agent\skywalking-agent.jar
-Dskywalking.agent.service_name={service-name}
-Dskywalking.collector.backend_service=192.168.150.11:11800
```

| 服务 | service_name |
|------|-------------|
| gateway | gateway |
| user-service | user-service |
| mall-goods-order | mall-goods-order |
| pay-service | pay-service |
| mcp-server | mcp-server |
| ai-chat-service | ai-chat-service |

`service_name` 必须与 `spring.application.name` 一致。

## 验证

```bash
java -jar skywalking/agent/skywalking-agent.jar 2>&1 | head -3
# 预期输出：SkyWalking Agent 版本信息
```

启动服务并发送请求后，访问 http://192.168.150.11:8090 查看链路。
