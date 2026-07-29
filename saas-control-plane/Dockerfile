FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package dependency:copy-dependencies

FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app
ENV SERVER_PORT=8080
COPY --from=builder /app/target/saas-control-plane-0.1.0-SNAPSHOT.jar ./app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]

FROM builder AS smoke-client
ENTRYPOINT ["java", "-cp", "target/classes:target/dependency/*", "com.prosec.saas.client.SmokeClient"]

