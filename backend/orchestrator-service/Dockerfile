# --- Stage 1: Build ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 1. Copy file POM tổng và module common-library TRƯỚC
COPY pom.xml .
RUN mvn install -N -DskipTests

COPY common-library ./common-library

# 2. Cài đặt common-library vào kho chứa cục bộ của Docker
RUN mvn -f common-library/pom.xml clean install -DskipTests

# 3. Copy mã nguồn của Service cụ thể
COPY orchestrator-service ./orchestrator-service

# 4. Build Service chính
RUN mvn -f orchestrator-service/pom.xml clean package -DskipTests

# --- Stage 2: Run ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy file .jar từ Stage 1 sang Stage 2
COPY --from=build /app/orchestrator-service/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
