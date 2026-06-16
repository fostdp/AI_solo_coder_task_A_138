import requests
import time
import random
import json
import argparse
import os
import sys
from datetime import datetime, timedelta

try:
    import paho.mqtt.client as mqtt
    HAS_MQTT = True
except ImportError:
    HAS_MQTT = False

API_BASE = os.environ.get("API_BASE", "http://localhost:8080/api")
MQTT_BROKER = os.environ.get("MQTT_BROKER", "localhost")
MQTT_PORT = int(os.environ.get("MQTT_PORT", "1883"))
MQTT_TOPIC_PREFIX = os.environ.get("MQTT_TOPIC_PREFIX", "sensors")

PAVEMENT_IDS = [
    "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "c3d4e5f6-a7b8-9012-cdef-123456789012",
    "d4e5f6a7-b8c9-0123-defa-234567890123",
    "e5f6a7b8-c9d0-1234-efab-345678901234",
]

WEATHER_STATES = ["clear", "light_rain", "moderate_rain", "heavy_rain", "drizzle", "after_rain"]

STATE_RANGES = {
    "clear": (0.0, 0.0),
    "light_rain": (0.5, 3.0),
    "moderate_rain": (3.0, 10.0),
    "heavy_rain": (10.0, 30.0),
    "drizzle": (0.1, 0.8),
    "after_rain": (0.0, 0.5),
}


class WeatherSimulator:
    def __init__(self, seed=42, fixed_rainfall=None):
        self.rng = random.Random(seed)
        self.current_state = "clear"
        self.state_duration = 0
        self.rainfall_rate = 0.0
        self.fixed_rainfall = fixed_rainfall

    def update(self):
        if self.fixed_rainfall is not None:
            self.rainfall_rate = self.fixed_rainfall
            return

        self.state_duration -= 1
        if self.state_duration <= 0:
            weights = [0.25, 0.20, 0.20, 0.10, 0.15, 0.10]
            self.current_state = self.rng.choices(WEATHER_STATES, weights=weights)[0]
            self.state_duration = self.rng.randint(2, 8)

        low, high = STATE_RANGES.get(self.current_state, (0.0, 0.0))
        self.rainfall_rate = self.rng.uniform(low, high)


class PavementSensorSimulator:
    def __init__(self, pavement_id, weather, seed=None, fixed_crack_width=None):
        self.pavement_id = pavement_id
        self.weather = weather
        self.rng = random.Random(seed)
        self.water_depth = 0.0
        self.base_crack_width = fixed_crack_width if fixed_crack_width is not None else self.rng.uniform(0.5, 3.0)
        self.fixed_crack_width = fixed_crack_width
        self.visitor_count = 0

    def generate_reading(self):
        rainfall = self.weather.rainfall_rate

        infiltration = 0.02 * (1 + self.base_crack_width / 5.0)
        runoff = 0.01 * (1 + self.water_depth / 10.0)
        self.water_depth = max(0.0, self.water_depth + rainfall - infiltration - runoff)
        self.water_depth += self.rng.gauss(0, 0.1)
        self.water_depth = max(0.0, self.water_depth)

        if self.fixed_crack_width is not None:
            crack_width = self.fixed_crack_width + self.rng.gauss(0, self.fixed_crack_width * 0.05)
        else:
            crack_width = self.base_crack_width + self.rng.gauss(0, 0.1)
            crack_width += self.water_depth * 0.05
        crack_width = max(0.1, crack_width)

        hour = datetime.now().hour
        if 9 <= hour <= 17:
            base_freq = self.rng.uniform(0.5, 5.0)
        elif 7 <= hour <= 20:
            base_freq = self.rng.uniform(0.1, 2.0)
        else:
            base_freq = self.rng.uniform(0.0, 0.3)
        step_frequency = max(0.0, base_freq + self.rng.gauss(0, 0.3))

        temperature = 20 + 8 * (1 if 6 <= hour <= 14 else -1) * self.rng.uniform(0.5, 1.0)
        temperature += self.rng.gauss(0, 1.5)

        humidity = 50 + self.water_depth * 5 + rainfall * 2
        humidity = min(100, max(30, humidity + self.rng.gauss(0, 3)))

        return {
            "pavementId": self.pavement_id,
            "recordedAt": datetime.now().isoformat(),
            "rainfallMm": round(rainfall, 2),
            "waterDepthMm": round(self.water_depth, 2),
            "crackWidthMm": round(crack_width, 3),
            "stepFrequency": round(step_frequency, 2),
            "temperature": round(temperature, 1),
            "humidity": round(humidity, 1),
        }


class MqttPublisher:
    def __init__(self, broker, port, prefix="sensors"):
        if not HAS_MQTT:
            raise RuntimeError("paho-mqtt is not installed. Run: pip install paho-mqtt")
        self.broker = broker
        self.port = port
        self.prefix = prefix
        self.client = mqtt.Client(
            client_id=f"icecrack-simulator-{os.getpid()}",
            clean_session=True,
            protocol=mqtt.MQTTv311,
        )
        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect
        self.connected = False

    def _on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            self.connected = True
            print(f"[MQTT] Connected to {self.broker}:{self.port}")
        else:
            print(f"[MQTT] Connection failed with code {rc}")
            self.connected = False

    def _on_disconnect(self, client, userdata, rc):
        self.connected = False
        print(f"[MQTT] Disconnected (rc={rc})")

    def connect(self):
        try:
            self.client.connect_async(self.broker, self.port, keepalive=60)
            self.client.loop_start()
            deadline = time.time() + 10
            while time.time() < deadline and not self.connected:
                time.sleep(0.1)
            return self.connected
        except Exception as e:
            print(f"[MQTT] Connect error: {e}")
            return False

    def publish(self, pavement_id, payload):
        if not self.connected:
            return False
        try:
            topic = f"{self.prefix}/{pavement_id}/data"
            msg = json.dumps(payload, ensure_ascii=False)
            result = self.client.publish(topic, msg, qos=1, retain=False)
            return result.rc == mqtt.MQTT_ERR_SUCCESS
        except Exception as e:
            print(f"[MQTT] Publish error: {e}")
            return False

    def disconnect(self):
        try:
            self.client.loop_stop()
            self.client.disconnect()
        except Exception:
            pass


def fetch_pavement_ids():
    try:
        resp = requests.get(f"{API_BASE}/pavements", timeout=5)
        if resp.status_code == 200:
            pavements = resp.json()
            if pavements:
                return [p["id"] for p in pavements]
    except Exception:
        pass
    return PAVEMENT_IDS


def post_sensor_data(data):
    try:
        resp = requests.post(
            f"{API_BASE}/sensor-data",
            json=data,
            headers={"Content-Type": "application/json"},
            timeout=10,
        )
        return resp.status_code in (200, 201)
    except Exception as e:
        print(f"[HTTP] POST failed: {e}")
        return False


def run_simulation(pavement_id, sensor_data):
    try:
        sim_req = {
            "pavementId": pavement_id,
            "rainfallMm": sensor_data["rainfallMm"],
            "initialWaterDepthMm": sensor_data["waterDepthMm"],
            "crackWidthMm": sensor_data["crackWidthMm"],
            "stepFrequency": sensor_data["stepFrequency"],
            "simulationDurationSec": 3600.0,
            "gridResolution": 20,
        }
        resp = requests.post(
            f"{API_BASE}/simulation/run",
            json=sim_req,
            headers={"Content-Type": "application/json"},
            timeout=30,
        )
        if resp.status_code == 201:
            result = resp.json()
            print(f"  [SIM] Recession time: {result['recessionTimeSec']:.1f}s, "
                  f"Peak depth: {result['peakWaterDepth']:.4f}m, "
                  f"Alert: {result['alertTriggered']}")
            return result
    except Exception as e:
        print(f"  [SIM] Error: {e}")
    return None


def run_aesthetic_analysis(pavement_id):
    try:
        resp = requests.post(
            f"{API_BASE}/aesthetic/analyze/{pavement_id}",
            headers={"Content-Type": "application/json"},
            timeout=30,
        )
        if resp.status_code == 201:
            result = resp.json()
            print(f"  [AES] Fractal dim: {result['fractalDimension']:.4f}, "
                  f"Entropy: {result['infoEntropy']:.4f}, "
                  f"Complexity: {result['visualComplexity']:.4f}")
            return result
    except Exception as e:
        print(f"  [AES] Error: {e}")
    return None


def wait_for_api(timeout=120):
    print(f"Waiting for API at {API_BASE} to be ready...")
    start = time.time()
    while time.time() - start < timeout:
        try:
            resp = requests.get(f"{API_BASE}/pavements", timeout=5)
            if resp.status_code == 200:
                print(f"API ready after {time.time() - start:.1f}s")
                return True
        except Exception:
            pass
        time.sleep(3)
    print(f"[WARNING] API not ready after {timeout}s, continuing anyway")
    return False


def main():
    parser = argparse.ArgumentParser(description="Ice-Crack Pavement Sensor Simulator")
    parser.add_argument("--interval", type=int, default=int(os.environ.get("SIM_INTERVAL", "60")),
                        help="Interval between readings in seconds")
    parser.add_argument("--pavements", type=int, default=int(os.environ.get("PAVEMENTS", "5")),
                        help="Number of pavements to simulate")
    parser.add_argument("--simulate", action="store_true",
                        default=os.environ.get("RUN_SIMULATION", "").lower() in ("true", "1", "yes"),
                        help="Also run drainage simulation")
    parser.add_argument("--aesthetic", action="store_true",
                        default=os.environ.get("RUN_AESTHETIC", "").lower() in ("true", "1", "yes"),
                        help="Also run aesthetic analysis")
    parser.add_argument("--dry-run", action="store_true",
                        help="Print data without posting to API")
    parser.add_argument("--mqtt", action="store_true",
                        default=os.environ.get("USE_MQTT", "").lower() in ("true", "1", "yes"),
                        help="Publish data via MQTT")
    parser.add_argument("--rainfall", type=float, default=None,
                        help=("Fixed rainfall intensity in mm/h. "
                              "Overrides weather simulation. "
                              "Env: RAINFALL_MM"))
    parser.add_argument("--crack-width", type=float, default=None,
                        help=("Fixed base crack width in mm. "
                              "Overrides per-pavement random width. "
                              "Env: CRACK_WIDTH_MM"))
    parser.add_argument("--wait-api", action="store_true",
                        help="Wait for API to be ready before starting")
    args = parser.parse_args()

    env_rainfall = os.environ.get("RAINFALL_MM")
    if env_rainfall and args.rainfall is None:
        try:
            args.rainfall = float(env_rainfall)
        except ValueError:
            pass

    env_crack = os.environ.get("CRACK_WIDTH_MM")
    if env_crack and args.crack_width is None:
        try:
            args.crack_width = float(env_crack)
        except ValueError:
            pass

    print("=" * 60)
    print("  古代园林铺地冰裂纹 - 传感器模拟器")
    print("=" * 60)
    print(f"  API Base     : {API_BASE}")
    print(f"  MQTT Broker  : {MQTT_BROKER}:{MQTT_PORT} {'(enabled)' if args.mqtt else '(disabled)'}")
    print(f"  Interval     : {args.interval}s")
    print(f"  Pavements    : {args.pavements}")
    print(f"  Simulate     : {args.simulate}")
    print(f"  Aesthetic    : {args.aesthetic}")
    if args.rainfall is not None:
        print(f"  Fixed Rainfall: {args.rainfall} mm/h")
    if args.crack_width is not None:
        print(f"  Fixed CrackWidth: {args.crack_width} mm")
    print("=" * 60)

    if args.wait_api and not args.dry_run:
        wait_for_api()

    pavement_ids = fetch_pavement_ids()[:args.pavements]
    print(f"\nSimulating {len(pavement_ids)} pavements")
    if len(pavement_ids) > 0:
        print(f"Pavement IDs: {pavement_ids[:3]}...\n")

    weather = WeatherSimulator(seed=42, fixed_rainfall=args.rainfall)
    simulators = {
        pid: PavementSensorSimulator(pid, weather, seed=hash(pid) % 10000, fixed_crack_width=args.crack_width)
        for pid in pavement_ids
    }

    mqtt_pub = None
    if args.mqtt:
        if not HAS_MQTT:
            print("[ERROR] paho-mqtt not installed. Install with: pip install paho-mqtt")
            sys.exit(1)
        mqtt_pub = MqttPublisher(MQTT_BROKER, MQTT_PORT, prefix=MQTT_TOPIC_PREFIX)
        if not mqtt_pub.connect():
            print("[ERROR] Failed to connect to MQTT broker")
            mqtt_pub = None

    cycle = 0
    aesthetic_interval = 10

    try:
        while True:
            cycle += 1
            weather.update()
            timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            weather_label = "fixed" if args.rainfall is not None else weather.current_state
            print(f"\n[{timestamp}] Cycle #{cycle} | Weather: {weather_label} | "
                  f"Rainfall: {weather.rainfall_rate:.1f} mm/h")

            for pid in pavement_ids:
                sim = simulators[pid]
                reading = sim.generate_reading()
                print(f"  P({pid[:8]}...) Rain:{reading['rainfallMm']:5.1f}mm  "
                      f"Water:{reading['waterDepthMm']:5.2f}mm  "
                      f"Crack:{reading['crackWidthMm']:5.3f}mm  "
                      f"Steps:{reading['stepFrequency']:4.1f}/min")

                if not args.dry_run:
                    post_sensor_data(reading)

                if mqtt_pub:
                    mqtt_pub.publish(pid, reading)

                if args.simulate and not args.dry_run:
                    run_simulation(pid, reading)

            if args.aesthetic and cycle % aesthetic_interval == 0 and not args.dry_run:
                print("\n  --- Running aesthetic analysis ---")
                for pid in pavement_ids:
                    run_aesthetic_analysis(pid)

            if args.dry_run and cycle >= 3:
                print("\n[Dry run mode - stopping after 3 cycles]")
                break

            time.sleep(args.interval)

    except KeyboardInterrupt:
        print("\n\nSimulator stopped by user.")
    finally:
        if mqtt_pub:
            mqtt_pub.disconnect()


if __name__ == "__main__":
    main()
