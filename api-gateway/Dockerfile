# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .
COPY eureka-server/pom.xml eureka-server/pom.xml
COPY admin-server/pom.xml admin-server/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY measurement-service/pom.xml measurement-service/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY email-service/pom.xml email-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml

COPY api-gateway/src api-gateway/src

RUN mvn -B -ntp -pl api-gateway -am clean package -DskipTests -Dmaven.wagon.http.retryHandler.count=5

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

USER spring

COPY --from=builder /app/api-gateway/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
