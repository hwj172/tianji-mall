# SkyWalking Java Agent

## 下载 Agent

```bash
curl -L https://archive.apache.org/dist/skywalking/9.7.0/apache-skywalking-java-agent-9.7.0.tgz -o /tmp/sw.tgz
mkdir -p skywalking/agent
tar xzf /tmp/sw.tgz -C skywalking/
mv skywalking/skywalking-agent skywalking/agent
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
