FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/credential-registry-api.jar /app/api.jar
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/api.jar"]
