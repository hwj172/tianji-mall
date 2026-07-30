FROM amazoncorretto:17-alpine
WORKDIR /app

ENV JAVA_OPTS="-Xms128m -Xmx256m"

COPY user-service/target/user-service-*.jar app.jar

EXPOSE 8081

ENTRYPOINT java ${JAVA_OPTS} -jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD wget -qO- http://localhost:8081/actuator/health || exit 1
