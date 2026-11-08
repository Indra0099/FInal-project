FROM node:22-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm test && npm run build
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build/backend
COPY backend/pom.xml ./
RUN mvn -B dependency:resolve
COPY backend/src ./src
COPY --from=frontend /build/frontend/dist ./src/main/resources/static
RUN mvn -B package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /data/uploads && chown -R 10001:10001 /data /app
COPY --from=backend /build/backend/target/shardquery-1.0.0.jar /app/app.jar
USER 10001
EXPOSE 8087
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-jar", "/app/app.jar"]
