# Builds the backend into a small runtime image. Tests don't run here: they need
# Docker themselves (Testcontainers), so CI runs them before Render deploys.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Strip Windows line endings in case the wrapper was checked out with CRLF.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests && cp target/backend-*.jar app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER app
# Render's free plan has 512 MB. Let the heap use most of it, and use the
# serial collector, which has the smallest overhead on 0.1 CPU.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
