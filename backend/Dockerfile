# syntax=docker/dockerfile:1

# --- Build stage: compile the Spring Boot executable jar ---
# Base images are pinned by digest so a rebuild cannot silently pick up a different image.
# Dependabot (docker ecosystem) proposes updates; to refresh by hand, pull the tag and read
# `docker inspect --format '{{index .RepoDigests 0}}' eclipse-temurin:21-jdk`.
FROM eclipse-temurin:21-jdk@sha256:3e3c176ffed168beb42c607be9bc1639b466cf00261a0fb04425562c9d0c5c2b AS build
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
FROM eclipse-temurin:21-jre@sha256:cff19e6215689161eb6162c11b86b0c60ddf802164f2eaf48d570f8fb79a36c5 AS runtime
WORKDIR /app

RUN useradd --system --no-create-home --shell /usr/sbin/nologin wattpilot
COPY --from=build /workspace/build/libs/wattpilot-backend-*.jar app.jar
USER wattpilot

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
