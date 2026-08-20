FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/riftchallenge-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
# Skip the C2 JIT tier and use the single-threaded GC: on Render's free-tier (shared, ~0.1 CPU),
# fewer background compiler/GC threads competing for that sliver of CPU means a faster cold start.
ENTRYPOINT ["java", "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-Xss256k", "-jar", "app.jar"]
