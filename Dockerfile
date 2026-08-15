FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 limiteddrop
WORKDIR /app
COPY --from=build /workspace/target/limited-drop-0.0.1-SNAPSHOT.jar app.jar
USER limiteddrop
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
