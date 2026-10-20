# Build the jar on the host first: ./gradlew bootJar
# (no Gradle inside the build, so it also works on networks that block DNS in Docker builds)

# Stage 1: split the fat jar into layers. Dependencies change rarely, so their layer stays cached
# and a code change only rebuilds and pushes the small application layer.
FROM eclipse-temurin:17-jre-noble AS extract
WORKDIR /build
COPY build/libs/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# Stage 2: JRE only (no compiler or JDK tools), non-root user.
FROM eclipse-temurin:17-jre-noble
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=extract /build/extracted/dependencies/ ./
COPY --from=extract /build/extracted/spring-boot-loader/ ./
COPY --from=extract /build/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/extracted/application/ ./
USER app
EXPOSE 8080
# Size the heap from the container memory limit instead of the host's RAM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=30s --timeout=3s CMD ["curl", "-fs", "http://127.0.0.1:8080/actuator/health"]
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
