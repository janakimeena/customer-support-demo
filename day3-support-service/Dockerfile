# Two stages: build the jar with the JDK + Maven wrapper, then run it on a slim JRE image.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Dependencies first: this layer is cached until pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /app/target/support-service-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
# Size the heap from the container's memory limit (free tiers give ~512 MB).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "app.jar"]
