# syntax=docker/dockerfile:1.7
# Build context: a main branch scripts/assemble.sh által összerakott workspace gyökere
# (benne platform-libs/ és a szolgáltatás mappája).
FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE_DIR
WORKDIR /src
COPY platform-libs/ platform-libs/
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q -f platform-libs/pom.xml install -DskipTests
COPY ${SERVICE_DIR}/ svc/
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q -f svc/pom.xml package -DskipTests \
    && cp svc/target/*.jar /app.jar

FROM eclipse-temurin:21-jre
RUN groupadd -r app && useradd -r -g app -u 10001 app
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
