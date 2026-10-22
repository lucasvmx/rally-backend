FROM maven:3.9-eclipse-temurin-25 AS compilacao
WORKDIR /app

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install --no-install-recommends --yes curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 rally \
    && useradd --system --uid 10001 --gid rally --home-dir /nonexistent --shell /usr/sbin/nologin rally

COPY --from=compilacao --chown=10001:10001 /app/target/*.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
USER 10001:10001
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8080/api/v1/saude || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
