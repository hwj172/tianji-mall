FROM amazoncorretto:17-alpine
WORKDIR /app

COPY skywalking/agent /skywalking/agent

ENV JAVA_OPTS="-Xms128m -Xmx256m"

COPY user-service/target/user-service-*.jar app.jar

EXPOSE 8081

ENTRYPOINT java ${JAVA_OPTS} \
  -javaagent:/skywalking/agent/skywalking-agent.jar \
  -Dskywalking.agent.service_name=${SW_SERVICE_NAME:user-service} \
  -Dskywalking.collector.backend_service=${SW_OAP_ADDR:skywalking-oap:11800} \
  -jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD wget -qO- http://localhost:8081/actuator/health || exit 1
