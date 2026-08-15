FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY . .
RUN chmod +x gradlew \
    && ./gradlew bootJar --no-daemon \
    && cp build/libs/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S spring \
    && adduser -S spring -G spring

WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/app.jar /app/app.jar

USER spring:spring
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
