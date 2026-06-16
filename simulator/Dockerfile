FROM python:3.11-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY sensor_simulator.py .

ENV API_BASE="http://backend:8080/api"
ENV MQTT_BROKER="mqtt"
ENV MQTT_PORT=1883
ENV SIM_INTERVAL=60
ENV PAVEMENTS=5

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -f http://${API_BASE%/api}/actuator/health || exit 0

CMD ["python", "sensor_simulator.py", "--mqtt", "--simulate", "--aesthetic", "--wait-api"]
