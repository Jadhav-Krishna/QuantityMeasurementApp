FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache wget \
    && addgroup -S spring \
    && adduser -S spring -G spring

USER spring

COPY payment-service/target/*.jar app.jar

EXPOSE 8084

HEALTHCHECK --interval=30s --timeout=10s --start-period=20s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8084/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
