# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Chỉ copy những thứ thuộc về discovery-server
COPY discovery-server/pom.xml ./discovery-server/
COPY discovery-server/src ./discovery-server/src

# Build chỉ mình nó
RUN mvn -f discovery-server/pom.xml clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/discovery-server/target/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-Dloader.path=/app/target/classes,/common-library/target/classes", "-jar", "app.jar"]