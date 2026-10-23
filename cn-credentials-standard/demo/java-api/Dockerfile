FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY java-api/pom.xml .
RUN mvn -B dependency:go-offline
COPY java-api/src src
COPY fixtures /fixtures
RUN mvn -B package

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/target/quarkus-app/lib/ /app/lib/
COPY --from=build /workspace/target/quarkus-app/*.jar /app/
COPY --from=build /workspace/target/quarkus-app/app/ /app/app/
COPY --from=build /workspace/target/quarkus-app/quarkus/ /app/quarkus/
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
