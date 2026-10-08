# Render has no native Java runtime — Docker is required to deploy this
# Spring Boot app there. Multi-stage build: compile with the full JDK +
# Maven wrapper, then ship only the JRE + built jar.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Resolve dependencies first so this layer is cached across builds that
# only change application source, not pom.xml.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
# Tests need a running Postgres + real service credentials that don't
# exist in the build sandbox - they run separately (CI/locally), not here.
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

COPY --from=build /app/target/postcards-*.jar app.jar

EXPOSE 8080

# The default max heap is ~25% of the container's memory, which on Render's
# free 512MB instance is ~128MB - not enough to decode a phone photo even
# with the subsampling ImageStorageService does. 70% leaves room for the
# JVM's own off-heap use (metaspace, thread stacks, NIO buffers) while
# roughly tripling the heap.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
