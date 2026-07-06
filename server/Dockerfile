FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S cove && adduser -S cove -G cove
COPY --from=build /workspace/build/libs/*.jar /app/cove-server.jar
USER cove
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/cove-server.jar"]
