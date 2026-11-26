# Build:  ./gradlew :server:fatJar && docker build -t scamsense-assistant server/
# Run:    see docs/CALL_ASSISTANT.md (all config via environment variables)
FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 scamsense && mkdir /data && chown scamsense /data
COPY build/libs/scamsense-server.jar /app/scamsense-server.jar
USER scamsense
ENV DB_PATH=/data/scamsense.db PORT=8080
VOLUME ["/data"]
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost:8080/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/scamsense-server.jar"]
