import requests
import time
import random
import json
import argparse
from datetime import datetime, timedelta

API_BASE = "http://localhost:8080/api"

PAVEMENT_IDS = [
    "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "c3d4e5f6-a7b8-9012-cdef-123456789012",
    "d4e5f6a7-b8c9-0123-defa-234567890123",
    "e5f6a7b8-c9d0-1234-efab-345678901234",
]

WEATHER_STATES = ["clear", "light_rain", "moderate_rain", "heavy_rain", "drizzle", "after_rain"]

class WeatherSimulator:
    def __init__(self, seed=42):
        self.rng = random.Random(seed)
        self.current_state = "clear"
        self.state_duration = 0
        self.rainfall_rate = 0.0

    def update(self):
        self.state_duration -= 1
        if self.state_duration <= 0:
            weights = [0.25, 0.20, 0.20, 0.10, 0.15, 0.10]
            self.current_state = self.rng.choices(WEATHER_STATES, weights=weights)[0]
            self.state_duration = self.rng.randint(2, 8)

        state_rates = {
            "clear": (0.0, 0.0),
            "light_rain": (0.5, 3.0),
            "moderate_rain": (3.0, 10.0),
            "heavy_rain": (10.0, 30.0),
            "drizzle": (0.1, 0.8),
            "after_rain": (0.0, 0.5),
        }
        low, high = state_rates.get(self.current_state, (0.0, 0.0))
        self.rainfall_rate = self.rng.uniform(low, high)

class PavementSensorSimulator:
    def __init__(self, pavement_id, weather, seed=None):
        self.pavement_id = pavement_id
        self.weather = weather
        self.rng = random.Random(seed)
        self.water_depth = 0.0
        self.base_crack_width = self.rng.uniform(0.5, 3.0)
        self.visitor_count = 0

    def generate_reading(self):
        rainfall = self.weather.rainfall_rate

        infiltration = 0.02 * (1 + self.base_crack_width / 5.0)
        runoff = 0.01 * (1 + self.water_depth / 10.0)
        self.water_depth = max(0.0, self.water_depth + rainfall - infiltration - runoff)
        self.water_depth += self.rng.gauss(0, 0.1)
        self.water_depth = max(0.0, self.water_depth)

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

def fetch_pavement_ids():
    try:
        resp = requests.get(f"{API_BASE}/pavements", timeout=5)
        if resp.status_code == 200:
            pavements = resp.json()
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
        return resp.status_code == 201
    except Exception as e:
        print(f"[ERROR] Failed to post data: {e}")
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
        print(f"  [ERROR] Simulation failed: {e}")
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
        print(f"  [ERROR] Aesthetic analysis failed: {e}")
    return None

def main():
    parser = argparse.ArgumentParser(description="Ice-Crack Pavement Sensor Simulator")
    parser.add_argument("--interval", type=int, default=60, help="Interval between readings in seconds")
    parser.add_argument("--pavements", type=int, default=5, help="Number of pavements to simulate")
    parser.add_argument("--simulate", action="store_true", help="Also run drainage simulation")
    parser.add_argument("--aesthetic", action="store_true", help="Also run aesthetic analysis")
    parser.add_argument("--dry-run", action="store_true", help="Print data without posting to API")
    args = parser.parse_args()

    print("=" * 60)
    print("  古代园林铺地冰裂纹 - 传感器模拟器")
    print("=" * 60)

    pavement_ids = fetch_pavement_ids()[:args.pavements]
    print(f"\nSimulating {len(pavement_ids)} pavements at {args.interval}s intervals")
    print(f"Pavement IDs: {pavement_ids[:3]}...\n")

    weather = WeatherSimulator(seed=42)
    simulators = {
        pid: PavementSensorSimulator(pid, weather, seed=hash(pid) % 10000)
        for pid in pavement_ids
    }

    cycle = 0
    aesthetic_interval = 10

    try:
        while True:
            cycle += 1
            weather.update()
            timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            print(f"\n[{timestamp}] Cycle #{cycle} | Weather: {weather.current_state} | Rainfall: {weather.rainfall_rate:.1f} mm/h")

            for pid in pavement_ids:
                sim = simulators[pid]
                reading = sim.generate_reading()
                print(f"  P({pid[:8]}...) Rain:{reading['rainfallMm']:5.1f}mm  "
                      f"Water:{reading['waterDepthMm']:5.2f}mm  "
                      f"Crack:{reading['crackWidthMm']:5.3f}mm  "
                      f"Steps:{reading['stepFrequency']:4.1f}/min")

                if not args.dry_run:
                    post_sensor_data(reading)

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

if __name__ == "__main__":
    main()
