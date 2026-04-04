# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy root pom.xml first for better caching
COPY pom.xml .

# Copy all module pom.xml files
COPY eureka-server/pom.xml eureka-server/pom.xml
COPY admin-server/pom.xml admin-server/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY measurement-service/pom.xml measurement-service/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY email-service/pom.xml email-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml

# Copy source code for email-service
COPY email-service/src email-service/src

# Build only email-service and its dependencies
RUN mvn -B -ntp -pl email-service -am clean package -DskipTests -Dmaven.wagon.http.retryHandler.count=5

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

USER spring

# Copy built jar from builder stage
COPY --from=builder /app/email-service/target/*.jar app.jar

EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD java -jar app.jar --management.endpoints.web.exposure.include=health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
