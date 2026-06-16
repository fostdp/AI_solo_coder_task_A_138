import { IceCrackScene } from './ice_crack_3d.js';
import { AestheticsPanel } from './aesthetics_panel.js';
import * as api from './api.js';
import { AlertWebSocket } from './websocket.js';
import { DashboardCharts } from './charts.js';

let scene = null;
let aestheticsPanel = null;
let charts = null;
let ws = null;
let pavements = [];
let currentPavement = null;

function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => {
        if (toast.parentNode) toast.parentNode.removeChild(toast);
    }, 5000);
}

function populateDropdown(pavementList) {
    const select = document.getElementById('pavement-selector');
    if (!select) return;
    select.innerHTML = '';
    for (const p of pavementList) {
        const option = document.createElement('option');
        option.value = p.id;
        option.textContent = p.name || `Pavement ${p.id}`;
        select.appendChild(option);
    }
}

function populateSensorTable(data) {
    const tbody = document.querySelector('#sensor-table tbody');
    if (!tbody) return;
    tbody.innerHTML = '';
    if (!data || data.length === 0) {
        const tr = document.createElement('tr');
        tr.innerHTML = '<td colspan="3">暂无数据</td>';
        tbody.appendChild(tr);
        return;
    }
    const latest = data[0];
    const rows = [
        ['降雨量', latest.rainfallMm != null ? latest.rainfallMm.toFixed(2) : '-', 'mm/h'],
        ['积水深度', latest.waterDepthMm != null ? latest.waterDepthMm.toFixed(2) : '-', 'mm'],
        ['裂缝宽度', latest.crackWidthMm != null ? latest.crackWidthMm.toFixed(3) : '-', 'mm'],
        ['踩踏频率', latest.stepFrequency != null ? latest.stepFrequency.toFixed(1) : '-', '次/min'],
        ['温度', latest.temperature != null ? latest.temperature.toFixed(1) : '-', '°C'],
        ['湿度', latest.humidity != null ? latest.humidity.toFixed(1) : '-', '%'],
    ];
    for (const [label, value, unit] of rows) {
        const tr = document.createElement('tr');
        tr.innerHTML = `<td>${label}</td><td>${value}</td><td>${unit}</td>`;
        tbody.appendChild(tr);
    }
}

function populateSimulationPanel(result) {
    const panel = document.getElementById('simulation-results');
    if (!panel) return;
    panel.innerHTML = '';
    if (!result) return;

    const metrics = [
        { label: '积水消退时间', value: `${result.recessionTimeSec.toFixed(1)} 秒`, highlight: result.recessionTimeSec > 1800 },
        { label: '峰值积水深度', value: `${(result.peakWaterDepth * 1000).toFixed(2)} mm` },
        { label: '排水速率', value: `${result.drainageRate.toFixed(6)} m/s` },
        { label: '渗透速率', value: `${result.infiltrationRate.toFixed(6)} m/s` },
        { label: '地表径流速率', value: `${result.surfaceRunoffRate.toFixed(6)} m/s` },
        { label: '告警触发', value: result.alertTriggered ? '是' : '否', highlight: result.alertTriggered },
    ];

    const grid = document.createElement('div');
    grid.className = 'metric-grid';
    for (const m of metrics) {
        const card = document.createElement('div');
        card.className = 'result-card';
        if (m.highlight) card.style.borderLeftColor = 'var(--accent-red)';
        card.innerHTML = `<div class="label">${m.label}</div><div class="value">${m.value}</div>`;
        grid.appendChild(card);
    }
    panel.appendChild(grid);

    if (result.timeSeries) {
        charts.drawTimeSeries(result.timeSeries, 'time-series-canvas');
    }
}

function populateAlertsPanel(alerts) {
    const panel = document.getElementById('alerts-panel');
    if (!panel) return;
    panel.innerHTML = '';
    if (!alerts || alerts.length === 0) {
        panel.innerHTML = '<div style="color:var(--text-muted);font-size:12px">暂无告警</div>';
        return;
    }
    for (const alert of alerts) {
        const div = document.createElement('div');
        const severity = (alert.severity || 'WARNING').toLowerCase();
        div.className = `alert-item ${severity}`;
        const timeStr = alert.createdAt ? new Date(alert.createdAt).toLocaleString('zh-CN') : '';
        div.innerHTML = `
            <div class="alert-time">${timeStr} | ${alert.pavementName || ''}</div>
            <div class="alert-msg"><strong>[${alert.alertType || '告警'}]</strong> ${alert.message || ''}</div>
            ${!alert.acknowledged ? `<button class="btn-primary ack-btn" data-id="${alert.id}" style="margin-top:6px;padding:4px 12px;width:auto;font-size:11px">确认</button>` : ''}
        `;
        panel.appendChild(div);
    }
    panel.querySelectorAll('.ack-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
            const id = btn.getAttribute('data-id');
            try {
                await api.acknowledgeAlert(id);
                showToast('告警已确认', 'info');
                loadAlerts();
            } catch (e) {
                showToast('确认告警失败', 'error');
            }
        });
    });
}

async function loadAlerts() {
    try {
        const alerts = await api.fetchUnacknowledgedAlerts();
        populateAlertsPanel(alerts);
    } catch (e) {
        console.error('Failed to load alerts', e);
    }
}

async function selectPavement(id) {
    try {
        const pavement = await api.fetchPavement(id);
        currentPavement = pavement;
        const sensorData = await api.fetchLatestSensorData(id, 10);
        populateSensorTable(sensorData);

        if (scene) {
            scene.clearScene();
            const length = pavement.areaLength || 10;
            const width = pavement.areaWidth || 10;
            scene.initPavement(length, width);

            let pattern = pavement.crackPattern;
            if (pattern && typeof pattern === 'string') {
                try { pattern = JSON.parse(pattern); } catch (e) { pattern = null; }
            }
            if (pattern) {
                scene.generateIceCracks(pattern);
            }
        }

        loadAlerts();
    } catch (e) {
        showToast('加载铺地数据失败', 'error');
    }
}

async function handleRunSimulation() {
    if (!currentPavement) {
        showToast('请先选择铺地', 'warning');
        return;
    }

    const rainfall = parseFloat(document.getElementById('rainfall-slider').value) || 50;
    const crackWidth = parseFloat(document.getElementById('crack-width').value) || 5;
    const stepFreq = parseFloat(document.getElementById('step-frequency').value) || 30;
    const gridRes = parseInt(document.getElementById('grid-resolution').value) || 20;
    const simDuration = parseFloat(document.getElementById('sim-duration').value) || 3600;
    const initDepth = parseFloat(document.getElementById('initial-water-depth').value) || 10;

    try {
        const result = await api.runSimulation({
            pavementId: currentPavement.id,
            rainfallMm: rainfall,
            initialWaterDepthMm: initDepth,
            crackWidthMm: crackWidth,
            stepFrequency: stepFreq,
            simulationDurationSec: simDuration,
            gridResolution: gridRes
        });
        populateSimulationPanel(result);
        showToast('排水仿真完成', 'info');

        if (scene && result.gridData) {
            try {
                const gridData = typeof result.gridData === 'string' ? JSON.parse(result.gridData) : result.gridData;
                scene.updateWaterSurface(gridData);
            } catch (e) {
                console.error('Failed to update water surface', e);
            }
        }
    } catch (e) {
        showToast('排水仿真失败', 'error');
    }
}

async function handleAnalyzeAesthetic() {
    if (!currentPavement) {
        showToast('请先选择铺地', 'warning');
        return;
    }
    try {
        const result = await aestheticsPanel.runAnalysis(currentPavement.id);
        showToast('美学分析完成', 'info');

        if (scene && result.crackSegments) {
            try {
                const segments = typeof result.crackSegments === 'string' ? JSON.parse(result.crackSegments) : result.crackSegments;
                const indices = segments.map((_, i) => i).filter(i => i % 3 === 0);
                scene.highlightCracks(indices);
            } catch (e) {
                console.error('Failed to highlight cracks', e);
            }
        }
    } catch (e) {
        showToast('美学分析失败', 'error');
    }
}

document.addEventListener('DOMContentLoaded', async () => {
    const container = document.getElementById('three-container');
    if (container) {
        scene = new IceCrackScene(container);
    }

    aestheticsPanel = new AestheticsPanel();
    charts = new DashboardCharts();

    ws = new AlertWebSocket((alert) => {
        showToast(`[${alert.alertType || '告警'}] ${alert.message || ''}`, alert.severity === 'HIGH' ? 'error' : 'warning');
        loadAlerts();
    });

    const rainfallSlider = document.getElementById('rainfall-slider');
    const rainfallValue = document.getElementById('rainfall-value');
    if (rainfallSlider && rainfallValue) {
        rainfallSlider.addEventListener('input', () => {
            rainfallValue.textContent = rainfallSlider.value;
        });
    }

    try {
        pavements = await api.fetchPavements();
        populateDropdown(pavements);
        if (pavements.length > 0) {
            await selectPavement(pavements[0].id);
        }
    } catch (e) {
        showToast('加载铺地列表失败', 'error');
    }

    const selectEl = document.getElementById('pavement-selector');
    if (selectEl) {
        selectEl.addEventListener('change', (e) => {
            selectPavement(e.target.value);
        });
    }

    const simBtn = document.getElementById('run-simulation');
    if (simBtn) {
        simBtn.addEventListener('click', handleRunSimulation);
    }

    const aestheticBtn = document.getElementById('run-aesthetic');
    if (aestheticBtn) {
        aestheticBtn.addEventListener('click', handleAnalyzeAesthetic);
    }

    loadAlerts();
});
