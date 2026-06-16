import * as api from './api.js';
import { DashboardCharts } from './charts.js';

export class AestheticsPanel {

  constructor() {
    this.charts = new DashboardCharts();
  }

  populatePanel(result) {
    const panel = document.getElementById('aesthetic-results');
    if (!panel) return;
    panel.innerHTML = '';
    if (!result) return;

    const metrics = [
      { label: '分形维数', value: result.fractalDimension.toFixed(4) },
      { label: '盒子计数维', value: result.boxCountingDim.toFixed(4) },
      { label: '信息熵', value: result.infoEntropy.toFixed(4) },
      { label: '视觉复杂度', value: result.visualComplexity.toFixed(4) },
      { label: '裂缝数量', value: `${result.crackCount}` },
      { label: '平均裂缝长度', value: `${result.avgCrackLength.toFixed(3)} m` },
      { label: '裂缝密度', value: result.crackDensity.toFixed(4) },
      { label: '图案对称性', value: result.patternSymmetry.toFixed(4) },
    ];

    const grid = document.createElement('div');
    grid.className = 'metric-grid';
    for (const m of metrics) {
      const card = document.createElement('div');
      card.className = 'result-card';
      card.innerHTML = `<div class="label">${m.label}</div><div class="value">${m.value}</div>`;
      grid.appendChild(card);
    }
    panel.appendChild(grid);

    this.charts.drawFractalPlot(result, 'fractal-canvas');
  }

  async runAnalysis(pavementId) {
    try {
      const result = await api.analyzeAesthetic(pavementId);
      this.populatePanel(result);
      return result;
    } catch (e) {
      throw e;
    }
  }
}
