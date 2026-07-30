FROM amazoncorretto:17-alpine
WORKDIR /app

COPY skywalking/agent /skywalking/agent

ENV JAVA_OPTS="-Xms128m -Xmx256m"

COPY mall-goods-order/target/mall-goods-order-*.jar app.jar

EXPOSE 8082

ENTRYPOINT java ${JAVA_OPTS} \
  -javaagent:/skywalking/agent/skywalking-agent.jar \
  -Dskywalking.agent.service_name=${SW_SERVICE_NAME:mall-goods-order} \
  -Dskywalking.collector.backend_service=${SW_OAP_ADDR:skywalking-oap:11800} \
  -jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD wget -qO- http://localhost:8082/actuator/health || exit 1
