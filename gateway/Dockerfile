FROM amazoncorretto:17-alpine
WORKDIR /app

# JVM 参数（可被 docker-compose 环境变量覆盖）
ENV JAVA_OPTS="-Xms128m -Xmx256m"

# 应用 jar（context 是项目根目录）
COPY gateway/target/gateway-*.jar app.jar

EXPOSE 8080

ENTRYPOINT java ${JAVA_OPTS} -jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
