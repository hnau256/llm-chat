FROM eclipse-temurin:21-jre

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY app/build/install/app/ /app/

ENV HEALTH_PORT=8080

HEALTHCHECK --interval=15s --timeout=5s --retries=3 --start-period=30s \
    CMD curl -f http://localhost:${HEALTH_PORT}/health || exit 1

ENTRYPOINT ["/app/bin/app"]
