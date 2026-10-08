# syntax=docker/dockerfile:1

# --- Build stage: compile the Spring Boot executable jar ---
# Base images are pinned by digest so a rebuild cannot silently pick up a different image.
# Dependabot (docker ecosystem) proposes updates; to refresh by hand, pull the tag and read
# `docker inspect --format '{{index .RepoDigests 0}}' eclipse-temurin:21-jdk`.
FROM eclipse-temurin:25-jdk@sha256:8c0a84ea11c8f6ed52600fc19f1040121f2a162998e9f50a5faebbbad9172dcc AS build
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
FROM eclipse-temurin:25-jre@sha256:fcd7fd7b387f94bb2ac461478a7436ad8e349924c374ea8313919624dceae636 AS runtime
WORKDIR /app

RUN useradd --system --no-create-home --shell /usr/sbin/nologin wattpilot
COPY --from=build /workspace/build/libs/wattpilot-backend-*.jar app.jar
USER wattpilot

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
