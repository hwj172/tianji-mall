FROM amazoncorretto:17-alpine
WORKDIR /app

ENV JAVA_OPTS="-Xms128m -Xmx256m"

COPY mcp-server/target/mcp-server-*.jar app.jar

EXPOSE 8084

ENTRYPOINT java ${JAVA_OPTS} -jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD wget -qO- http://localhost:8084/actuator/health || exit 1
