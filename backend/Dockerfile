# syntax=docker/dockerfile:1

# --- Build stage: compile the Spring Boot executable jar ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Resolve dependencies on their own layer so a source-only change does not
# re-download the world on every rebuild.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

COPY src ./src
# Tests need a Docker daemon (Testcontainers) that is not available here; the CI
# pipeline and local builds are responsible for running them.
RUN ./gradlew --no-daemon clean bootJar

# --- Runtime stage: JRE only ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN useradd --system --no-create-home --shell /usr/sbin/nologin wattpilot
COPY --from=build /workspace/build/libs/wattpilot-backend-*.jar app.jar
USER wattpilot

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
