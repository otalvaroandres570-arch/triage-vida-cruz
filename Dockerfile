# ---- Build stage: compiles the executable jar ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY src src

# The wrapper may have Windows line endings if the repository was checked out on Windows
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# The Gradle cache survives between builds, so only the first one downloads every dependency
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar -x test

# ---- Runtime stage: only a JRE and the jar ----
FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --no-create-home app

WORKDIR /app
COPY --from=build /workspace/build/libs/app.jar app.jar

USER app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8081

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -fs http://localhost:8081/actuator/health/liveness || exit 1

# "exec" makes the JVM the main process, so it receives the stop signal and shuts down gracefully
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]