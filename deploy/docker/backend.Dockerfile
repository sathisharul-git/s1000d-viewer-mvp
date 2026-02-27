FROM gradle:8.10.2-jdk17 AS builder
WORKDIR /workspace

COPY settings.gradle build.gradle gradle.properties* ./
COPY application ./application

RUN gradle --no-daemon :application:bootJar -x test

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=builder /workspace/application/build/libs/*.jar /app/application.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
