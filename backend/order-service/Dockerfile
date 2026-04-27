# --- Stage 1: Build ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 1. Copy file POM tổng và module common-library TRƯỚC
# Lưu ý: Lệnh này yêu cầu Build Context phải đứng ở thư mục "backend"
COPY pom.xml .

RUN mvn install -N -DskipTests


COPY common-library ./common-library

# 2. Cài đặt common-library vào kho chứa cục bộ của Docker
# Bước này cực kỳ quan trọng để giải quyết lỗi "Missing artifact"
RUN mvn -f common-library/pom.xml clean install -DskipTests

# 3. Copy mã nguồn của Service cụ thể (Ví dụ này là order-service)
# Hãy thay "order-service" bằng tên thư mục service tương ứng
COPY order-service ./order-service

# 4. Build Service chính
RUN mvn -f order-service/pom.xml clean package -DskipTests

# --- Stage 2: Run ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy file .jar từ Stage 1 sang Stage 2
# Lưu ý sửa đường dẫn copy đúng theo tên service
COPY --from=build /app/order-service/target/*.jar app.jar

ENTRYPOINT ["java", "-Dloader.path=/app/target/classes,/common-library/target/classes", "-jar", "app.jar"]