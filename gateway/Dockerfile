FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# SkyWalking Java Agent
COPY skywalking/agent /skywalking/agent

# JVM 参数（可被 docker-compose 环境变量覆盖）
ENV JAVA_OPTS="-Xms128m -Xmx256m"

# 应用 jar（context 是项目根目录）
COPY gateway/target/gateway-*.jar app.jar

EXPOSE 8080

# SkyWalking agent + JVM 参数
ENTRYPOINT java ${JAVA_OPTS} \
  -javaagent:/skywalking/agent/skywalking-agent.jar \
  -Dskywalking.agent.service_name=${SW_SERVICE_NAME:gateway} \
  -Dskywalking.collector.backend_service=${SW_OAP_ADDR:skywalking-oap:11800} \
  -jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
