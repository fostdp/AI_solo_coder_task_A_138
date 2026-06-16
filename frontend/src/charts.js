class DashboardCharts {
  constructor() {
    this.ctx = null;
  }

  drawTimeSeries(timeSeries, containerId) {
    const canvas = document.getElementById(containerId);
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const data = typeof timeSeries === 'string' ? JSON.parse(timeSeries) : timeSeries;
    if (!data || data.length === 0) return;

    const w = canvas.width;
    const h = canvas.height;
    const padding = 60;
    const chartW = w - padding * 2;
    const chartH = h - padding * 2;

    ctx.clearRect(0, 0, w, h);

    const times = data.map(d => d.time);
    const avgDepths = data.map(d => d.avgDepth);
    const maxDepths = data.map(d => d.maxDepth);

    const minTime = Math.min(...times);
    const maxTime = Math.max(...times);
    const allDepths = [...avgDepths, ...maxDepths];
    const minDepth = Math.min(...allDepths);
    const maxDepth = Math.max(...allDepths);

    const xScale = (v) => padding + ((v - minTime) / (maxTime - minTime || 1)) * chartW;
    const yScale = (v) => padding + chartH - ((v - minDepth) / (maxDepth - minDepth || 1)) * chartH;

    ctx.strokeStyle = '#ccc';
    ctx.lineWidth = 0.5;
    for (let i = 0; i <= 5; i++) {
      const y = padding + (chartH / 5) * i;
      ctx.beginPath();
      ctx.moveTo(padding, y);
      ctx.lineTo(w - padding, y);
      ctx.stroke();
    }
    for (let i = 0; i <= 5; i++) {
      const x = padding + (chartW / 5) * i;
      ctx.beginPath();
      ctx.moveTo(x, padding);
      ctx.lineTo(x, h - padding);
      ctx.stroke();
    }

    ctx.strokeStyle = '#333';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(padding, padding);
    ctx.lineTo(padding, h - padding);
    ctx.lineTo(w - padding, h - padding);
    ctx.stroke();

    ctx.fillStyle = '#333';
    ctx.font = '12px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('Time (s)', w / 2, h - 10);
    ctx.save();
    ctx.translate(15, h / 2);
    ctx.rotate(-Math.PI / 2);
    ctx.fillText('Water Depth (m)', 0, 0);
    ctx.restore();

    for (let i = 0; i <= 5; i++) {
      const tVal = minTime + ((maxTime - minTime) / 5) * i;
      const dVal = minDepth + ((maxDepth - minDepth) / 5) * i;
      ctx.textAlign = 'center';
      ctx.fillText(tVal.toFixed(1), padding + (chartW / 5) * i, h - padding + 20);
      ctx.textAlign = 'right';
      ctx.fillText(dVal.toFixed(2), padding - 8, yScale(dVal) + 4);
    }

    ctx.strokeStyle = '#00a86b';
    ctx.lineWidth = 2;
    ctx.beginPath();
    for (let i = 0; i < data.length; i++) {
      const x = xScale(times[i]);
      const y = yScale(avgDepths[i]);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();

    ctx.strokeStyle = '#ffd700';
    ctx.lineWidth = 2;
    ctx.beginPath();
    for (let i = 0; i < data.length; i++) {
      const x = xScale(times[i]);
      const y = yScale(maxDepths[i]);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();

    ctx.font = '12px sans-serif';
    ctx.fillStyle = '#00a86b';
    ctx.textAlign = 'left';
    ctx.fillText('Avg Depth', w - padding - 120, padding + 15);
    ctx.fillStyle = '#ffd700';
    ctx.fillText('Max Depth', w - padding - 120, padding + 30);
  }

  drawFractalPlot(result, containerId) {
    const canvas = document.getElementById(containerId);
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const data = typeof result === 'string' ? JSON.parse(result) : result;

    const w = canvas.width;
    const h = canvas.height;

    ctx.clearRect(0, 0, w, h);

    ctx.fillStyle = '#333';
    ctx.font = '16px sans-serif';
    ctx.textAlign = 'left';
    const fractalDim = data && data.fractalDimension != null ? data.fractalDimension : 'N/A';
    ctx.fillText(`Fractal Dimension: ${fractalDim}`, 20, 30);

    const angles = data && data.crackAngles ? data.crackAngles : [];
    const bins = 18;
    const binSize = 180 / bins;
    const counts = new Array(bins).fill(0);
    for (const angle of angles) {
      const idx = Math.min(Math.floor(angle / binSize), bins - 1);
      counts[idx]++;
    }
    const maxCount = Math.max(...counts, 1);

    const padding = 60;
    const chartW = w - padding * 2;
    const chartH = h - padding * 2 - 40;
    const barW = chartW / bins;

    ctx.strokeStyle = '#333';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(padding, padding + 40);
    ctx.lineTo(padding, h - padding);
    ctx.lineTo(w - padding, h - padding);
    ctx.stroke();

    for (let i = 0; i < bins; i++) {
      const barH = (counts[i] / maxCount) * chartH;
      const x = padding + i * barW;
      const y = h - padding - barH;
      const t = i / (bins - 1);
      const r = Math.round(0 + t * 255);
      const g = Math.round(168 + t * (215 - 168));
      const b = Math.round(107 + t * (0 - 107));
      ctx.fillStyle = `rgb(${r},${g},${b})`;
      ctx.fillRect(x + 1, y, barW - 2, barH);
    }

    ctx.fillStyle = '#333';
    ctx.font = '12px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('Crack Angle Distribution (0°-180°)', w / 2, h - 10);
    ctx.textAlign = 'left';
    ctx.fillText('Count', 10, padding + 40 + chartH / 2);
  }

  clearCanvas(containerId) {
    const canvas = document.getElementById(containerId);
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, canvas.width, canvas.height);
  }
}

export { DashboardCharts };
