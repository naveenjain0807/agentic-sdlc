# =====================================================================
# Stage 1 - build the fat jar
# =====================================================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy poms first so dependency resolution is cached independently of source changes.
COPY pom.xml ./
COPY url-shortener-service/pom.xml ./url-shortener-service/
RUN mvn -B -pl url-shortener-service -am -DskipTests dependency:go-offline || true

COPY url-shortener-service/src ./url-shortener-service/src
RUN mvn -B -pl url-shortener-service -am -DskipTests clean package

# =====================================================================
# Stage 2 - slim runtime
# =====================================================================
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build /workspace/url-shortener-service/target/url-shortener-service.jar /app/app.jar
RUN chown -R app:app /app

USER app
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport" \
    APP_BASE_URL="http://localhost:8080"

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
