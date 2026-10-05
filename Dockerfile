# syntax=docker/dockerfile:1

# ---- Build stage -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY where-to-park-connector/pom.xml where-to-park-connector/
COPY where-to-park-rest/pom.xml where-to-park-rest/
# Warm the dependency cache before copying sources.
RUN mvn -q -DskipTests dependency:go-offline || true
COPY . .
RUN mvn -q -DskipTests package

# ---- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:17-jre
# Run as a non-root user.
RUN groupadd -r app && useradd -r -g app app
WORKDIR /app
COPY --from=build /build/where-to-park-rest/target/where-to-park-rest-0.0.1-SNAPSHOT.jar app.jar
# The embedded H2 database file lives in a mounted volume.
RUN mkdir -p /app/data && chown -R app:app /app
USER app
EXPOSE 8080
# JAVA_OPTS lets the hosting platform inject JVM flags (e.g. -Xmx on memory-capped free tiers) without rebuilding the image.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dfile.encoding=UTF-8 -jar app.jar"]
