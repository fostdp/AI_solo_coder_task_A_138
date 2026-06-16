const API_BASE = '/api';

async function fetchPavements() {
  const res = await fetch(`${API_BASE}/pavements`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

async function fetchPavement(id) {
  const res = await fetch(`${API_BASE}/pavements/${id}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

async function fetchLatestSensorData(pavementId, limit = 50) {
  const res = await fetch(`${API_BASE}/sensor-data/${pavementId}/latest?limit=${limit}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

async function fetchSensorDataRange(pavementId, start, end) {
  const res = await fetch(`${API_BASE}/sensor-data/${pavementId}/range?start=${start}&end=${end}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

async function addSensorData(data) {
  const res = await fetch(`${API_BASE}/sensor-data`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

async function runSimulation(data) {
  const res = await fetch(`${API_BASE}/simulation/run`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

async function fetchSimulationHistory(pavementId) {
  const res = await fetch(`${API_BASE}/simulation/${pavementId}/history`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

async function analyzeAesthetic(pavementId) {
  const res = await fetch(`${API_BASE}/aesthetic/analyze/${pavementId}`, {
    method: 'POST'
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

async function fetchAestheticHistory(pavementId) {
  const res = await fetch(`${API_BASE}/aesthetic/${pavementId}/history`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

async function fetchUnacknowledgedAlerts() {
  const res = await fetch(`${API_BASE}/alerts/unacknowledged`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

async function fetchAlertsByPavement(pavementId) {
  const res = await fetch(`${API_BASE}/alerts/pavement/${pavementId}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

async function acknowledgeAlert(alertId) {
  const res = await fetch(`${API_BASE}/alerts/${alertId}/acknowledge`, {
    method: 'PUT'
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export {
  API_BASE,
  fetchPavements,
  fetchPavement,
  fetchLatestSensorData,
  fetchSensorDataRange,
  addSensorData,
  runSimulation,
  fetchSimulationHistory,
  analyzeAesthetic,
  fetchAestheticHistory,
  fetchUnacknowledgedAlerts,
  fetchAlertsByPavement,
  acknowledgeAlert
};
