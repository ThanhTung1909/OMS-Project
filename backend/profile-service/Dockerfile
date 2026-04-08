# --- Stage 1: Build ---
# Dùng image Maven để build ra file .jar
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy file cấu hình dependency trước để tận dụng cache của Docker
COPY pom.xml .
# (Tùy chọn) Nếu project cha nằm ngoài, bạn cần copy cả pom cha.
# Nhưng với cấu trúc microservice độc lập thường thấy thì chỉ cần pom hiện tại.

# Tải dependencies (để cache layer này, lần sau build sẽ nhanh hơn)
RUN mvn dependency:go-offline

# Copy toàn bộ source code vào
COPY src ./src

# Build ứng dụng (Bỏ qua test để build nhanh như đã bàn trước đó)
RUN mvn clean package -DskipTests

# --- Stage 2: Run ---
# Dùng image JRE siêu nhẹ chỉ để chạy ứng dụng
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy file .jar từ Stage 1 sang Stage 2
# Lưu ý: *.jar sẽ lấy file jar được build ra, đổi tên thành app.jar cho ngắn gọn
COPY --from=build /app/target/*.jar app.jar

# Expose port (Identity service thường chạy port 8081 hoặc do bạn cấu hình)
EXPOSE 8088

# Lệnh chạy ứng dụng
ENTRYPOINT ["java", "-jar", "app.jar"]