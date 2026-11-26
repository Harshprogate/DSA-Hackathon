/* =============================================================
   Live Arrival Index – app.js
   Full frontend logic: navigation, KPIs, visualizer, forms,
   algorithm lab, simulator, benchmark, analytics, audit log,
   export, reset/undo dialogs.
   ============================================================= */

/* ---- API wrapper ----------------------------------------- */
async function api(url, options = {}) {
  const defaults = { headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' } };
  const cfg = Object.assign({}, defaults, options);
  if (options.headers) cfg.headers = Object.assign({}, defaults.headers, options.headers);
  const res = await fetch(url, cfg);
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw Object.assign(new Error(err.message || `HTTP ${res.status}`), { status: res.status, body: err });
  }
  const ct = res.headers.get('Content-Type') || '';
  if (ct.includes('text/csv') || ct.includes('application/pdf') || ct.includes('octet-stream')) {
    return { blob: await res.blob(), disposition: res.headers.get('Content-Disposition') || '' };
  }
  return res.json();
}

/* ---- Navigation ------------------------------------------ */
const viewSections = document.querySelectorAll('.view-section');
const navItems     = document.querySelectorAll('.nav-item');

function showView(viewName) {
  viewSections.forEach(s => { s.hidden = s.id !== `view-${viewName}`; });
  navItems.forEach(b => {
    const active = b.dataset.view === viewName;
    b.classList.toggle('active', active);
    b.setAttribute('aria-current', active ? 'page' : 'false');
  });
  document.getElementById('view-title').textContent = {
    overview:  'Overview',
    explorer:  'Interval Explorer',
    lab:       'Algorithm Laboratory',
    simulator: 'Arrival Simulator',
    benchmark: 'Performance Benchmark',
    analytics: 'Warehouse Analytics',
    logs:      'Activity Logs'
  }[viewName] || viewName;
}

navItems.forEach(btn => btn.addEventListener('click', () => showView(btn.dataset.view)));
showView('overview');

/* ---- Health check ---------------------------------------- */
const healthBadge = document.getElementById('api-health-badge');
const healthText  = document.getElementById('api-health-text');

async function checkHealth() {
  try {
    await api('/api/packages/statistics');
    healthBadge.className = 'status-pill status-online';
    healthText.textContent = 'Live Backend Connected';
  } catch {
    healthBadge.className = 'status-pill status-offline';
    healthText.textContent = 'Backend Offline';
  }
}
checkHealth();
setInterval(checkHealth, 15000);

/* ---- KPI Stats ------------------------------------------- */
let kpiRangeStart = 1, kpiRangeEnd = 100;

async function loadStats() {
  try {
    const s = await api('/api/packages/statistics');
    setText('kpi-unique',     s.totalUniquePackages ?? 0);
    setText('kpi-intervals',  s.totalIntervals ?? 0);
    setText('kpi-min',        s.smallestId ?? '—');
    setText('kpi-max',        s.largestId ?? '—');
    setText('kpi-inserts',    s.totalSuccessfulInsertions ?? 0);
    setText('kpi-duplicates', s.totalDuplicates ?? 0);
    setText('kpi-merges',     s.bridgeMergeCount ?? 0);
    setText('kpi-extensions', s.extensionCount ?? 0);
  } catch (e) { console.error('stats', e); }
  // Missing in range
  try {
    const m = await api(`/api/packages/analytics?start=${kpiRangeStart}&end=${kpiRangeEnd}`);
    setText('kpi-missing-range', m.missingCount ?? '—');
    setText('kpi-completion', (m.coveragePercent ?? 0).toFixed(1) + '%');
  } catch { setText('kpi-missing-range', '—'); }
}

function setText(id, v) {
  const el = document.getElementById(id);
  if (el) el.textContent = v;
}

document.getElementById('refresh-btn').addEventListener('click', () => { loadStats(); renderTopology(); });

/* ---- KPI Range form -------------------------------------- */
document.getElementById('overview-range-form').addEventListener('submit', e => {
  e.preventDefault();
  kpiRangeStart = Number(document.getElementById('ov-range-start').value) || 1;
  kpiRangeEnd   = Number(document.getElementById('ov-range-end').value)   || 100;
  loadStats();
});

/* ---- Quick Single Insert --------------------------------- */
let lastModifiedInterval = null;

const quickForm = document.getElementById('quick-single-form');
const quickInput = document.getElementById('quick-package-id');
const feedbackEl = document.getElementById('overview-feedback');

function showFeedback(msg, type) {
  feedbackEl.textContent = msg;
  feedbackEl.className = `banner banner-${type}`;
  feedbackEl.hidden = false;
  clearTimeout(feedbackEl._t);
  feedbackEl._t = setTimeout(() => { feedbackEl.hidden = true; }, 4000);
}

quickForm.addEventListener('submit', async e => {
  e.preventDefault();
  const id = parseInt(quickInput.value, 10);
  if (isNaN(id) || id < 0 || id > 1_000_000_000) {
    showFeedback('Enter a valid integer between 0 and 1,000,000,000.', 'error'); return;
  }
  try {
    const r = await api('/api/packages', { method: 'POST', body: JSON.stringify({ packageId: id }) });
    const type = r.status === 'ADDED' ? 'success' : 'warn';
    showFeedback(`ID ${id} ${r.status === 'ADDED' ? 'ADDED' : 'DUPLICATE'} — ${r.message}`, type);
    if (r.trace) lastModifiedInterval = r.trace.createdOrUpdatedInterval;
    quickInput.value = '';
    loadStats();
    renderTopology();
    logActivity(`POST /api/packages → ${r.status}: ID ${id}`, type === 'success' ? '#34d399' : '#fbbf24');
  } catch (err) {
    showFeedback(err.message, 'error');
  }
});

/* ---- Quick Batch ----------------------------------------- */
const batchForm  = document.getElementById('quick-batch-form');
const batchInput = document.getElementById('quick-batch-ids');

batchForm.addEventListener('submit', async e => {
  e.preventDefault();
  const raw = batchInput.value.trim();
  const ids = raw.split(/[\s,]+/).filter(Boolean).map(Number);
  if (!ids.length || ids.some(x => isNaN(x) || x < 0 || x > 1_000_000_000)) {
    showFeedback('Enter valid nonneg integers separated by commas or spaces.', 'error'); return;
  }
  try {
    const r = await api('/api/packages/batch', { method: 'POST', body: JSON.stringify({ packageIds: ids }) });
    showFeedback(`Processed: ${r.processed} total — ${r.newlyAdded} added, ${r.duplicates} duplicates.`, 'success');
    batchInput.value = '';
    loadStats();
    renderTopology();
    logActivity(`POST /api/packages/batch → processed ${r.processed}`, '#34d399');
  } catch (err) {
    showFeedback(err.message, 'error');
  }
});

// Live scenario presets
document.querySelectorAll('.scenario-btn').forEach(btn => {
  btn.addEventListener('click', () => { batchInput.value = btn.dataset.seq; });
});

/* ---- Live Interval Topology Visualizer ------------------- */
const vizTrack = document.getElementById('interval-visualizer');
const inspector = document.getElementById('viz-inspector');
const zoomSlider = document.getElementById('viz-zoom');

async function renderTopology() {
  try {
    const r = await api('/api/packages/intervals');
    const intervals = r.intervals || [];
    vizTrack.innerHTML = '';

    if (!intervals.length) {
      vizTrack.innerHTML = '<div class="empty-msg">No intervals indexed yet. Insert a package ID or launch the Arrival Simulator.</div>';
      return;
    }

    const zoom = parseFloat(zoomSlider?.value || 1);
    const minId = intervals[0].start;
    const maxId = intervals[intervals.length - 1].end;
    const totalSpan = Math.max(maxId - minId + 1, 1);

    intervals.forEach((iv, i) => {
      const isModified = lastModifiedInterval &&
        iv.start === lastModifiedInterval.start && iv.end === lastModifiedInterval.end;

      // Gap before this interval
      const prevEnd = i === 0 ? minId - 1 : intervals[i - 1].end;
      const gapSize = iv.start - prevEnd - 1;
      if (gapSize > 0) {
        const gapW = Math.max(30, (gapSize / totalSpan) * 500 * zoom);
        const gap = makevizBlock('gap', `[${prevEnd + 1}, ${iv.start - 1}]`, gapW, `Gap: ${gapSize}`);
        vizTrack.appendChild(gap);
      }

      // Received interval
      const rxSize = iv.end - iv.start + 1;
      const rxW = Math.max(36, (rxSize / totalSpan) * 500 * zoom);
      const cls = isModified ? 'mod' : 'rx';
      const block = makevizBlock(cls, `[${iv.start}, ${iv.end}]`, rxW, `${iv.start} – ${iv.end} (${rxSize})`);
      block.addEventListener('click', () => {
        inspector.textContent = `Interval [${iv.start}, ${iv.end}] — ${rxSize} package(s)`;
        inspector.hidden = false;
      });
      vizTrack.appendChild(block);
    });

    // Axis
    const axis = document.getElementById('viz-axis');
    if (axis) {
      axis.innerHTML = `<span>Min: ${minId}</span><span>Span: ${(maxId - minId + 1).toLocaleString()}</span><span>Max: ${maxId}</span>`;
    }
  } catch (e) { console.error('topology', e); }
}

function makevizBlock(cls, label, widthPx, title) {
  const el = document.createElement('div');
  el.className = `viz-block ${cls}`;
  el.style.width = widthPx + 'px';
  el.title = title;
  el.textContent = label.length > 10 ? label.slice(0, 9) + '…' : label;
  return el;
}

zoomSlider?.addEventListener('input', renderTopology);

/* ---- Interval Explorer ----------------------------------- */
document.getElementById('explorer-range-form').addEventListener('submit', async e => {
  e.preventDefault();
  const start = Number(document.getElementById('exp-start').value);
  const end   = Number(document.getElementById('exp-end').value);
  const errEl = document.getElementById('explorer-error');
  errEl.hidden = true;
  try {
    const [rxR, missR] = await Promise.all([
      api(`/api/packages/intervals/range?start=${start}&end=${end}`),
      api(`/api/packages/missing?start=${start}&end=${end}`)
    ]);
    renderPills('exp-received-pills', rxR.intervals || [], 'pill-rx');
    renderPills('exp-missing-pills',  missR.missingIntervals || [], 'pill-miss');
    document.getElementById('explorer-results').hidden = false;
    // Reload table
    loadIntervalTable();
  } catch (err) {
    errEl.textContent = err.message;
    errEl.hidden = false;
  }
});

async function loadIntervalTable() {
  try {
    const r = await api('/api/packages/intervals');
    const intervals = r.intervals || [];
    const tbody = document.getElementById('explorer-tbody');
    document.getElementById('explorer-count-badge').textContent = `${intervals.length} intervals`;
    if (!intervals.length) {
      tbody.innerHTML = '<tr><td colspan="5" class="empty-cell">No active intervals in index.</td></tr>';
      return;
    }
    tbody.innerHTML = intervals.map((iv, i) =>
      `<tr>
        <td>${i + 1}</td>
        <td>[${iv.start}, ${iv.end}]</td>
        <td>${iv.start}</td>
        <td>${iv.end}</td>
        <td>${iv.end - iv.start + 1}</td>
      </tr>`
    ).join('');
  } catch (e) { console.error('table', e); }
}

function renderPills(containerId, items, cls) {
  const c = document.getElementById(containerId);
  if (!c) return;
  if (!items.length) { c.innerHTML = '<span style="color:var(--text-muted);font-size:.78rem;">None</span>'; return; }
  c.innerHTML = items.map(iv => `<span class="pill ${cls}">[${iv.start}, ${iv.end}]</span>`).join('');
}

/* ---- Snapshots ------------------------------------------- */
document.getElementById('refresh-snapshots-btn').addEventListener('click', async () => {
  try {
    const snaps = await api('/api/packages/history');
    ['snap-a', 'snap-b'].forEach(id => {
      const sel = document.getElementById(id);
      sel.innerHTML = snaps.map((s, i) =>
        `<option value="${i}">${s.trigger || 'snapshot'} @ ${s.capturedAt ? s.capturedAt.substring(11, 19) : i}</option>`
      ).join('');
    });
    window._snaps = snaps;
  } catch (e) { console.error(e); }
});

document.getElementById('compare-snaps-btn').addEventListener('click', () => {
  const snaps = window._snaps;
  if (!snaps) { document.getElementById('snap-diff-output').textContent = 'Load snapshots first.'; return; }
  const a = snaps[Number(document.getElementById('snap-a').value)];
  const b = snaps[Number(document.getElementById('snap-b').value)];
  if (!a || !b) return;
  const toSet = s => new Set((s.intervals || []).map(iv => `[${iv.start},${iv.end}]`));
  const setA = toSet(a), setB = toSet(b);
  const added   = [...setB].filter(x => !setA.has(x));
  const removed = [...setA].filter(x => !setB.has(x));
  document.getElementById('snap-diff-output').textContent =
    `Added:   ${added.join(', ') || 'none'}\nRemoved: ${removed.join(', ') || 'none'}`;
});

/* ---- Algorithm Lab --------------------------------------- */
const labForm    = document.getElementById('lab-form');
const labInput   = document.getElementById('lab-package-id');
const labErrBnr  = document.getElementById('lab-error-banner');

document.getElementById('lab-preview-btn').addEventListener('click', async () => {
  const id = parseInt(labInput.value, 10);
  if (isNaN(id) || id < 0) { labInput.classList.add('invalid'); return; }
  labInput.classList.remove('invalid');
  try {
    const t = await api('/api/packages/preview', { method: 'POST', body: JSON.stringify({ packageId: id }) });
    renderTrace(t, 'PREVIEW');
    labErrBnr.hidden = true;
  } catch (err) { showLabError(err.message); }
});

labForm.addEventListener('submit', async e => {
  e.preventDefault();
  const id = parseInt(labInput.value, 10);
  if (isNaN(id) || id < 0) return;
  try {
    const r = await api('/api/packages', { method: 'POST', body: JSON.stringify({ packageId: id }) });
    renderTrace(r.trace, 'CONFIRMED');
    labErrBnr.hidden = true;
    lastModifiedInterval = r.trace?.createdOrUpdatedInterval;
    loadStats(); renderTopology();
  } catch (err) { showLabError(err.message); }
});

function showLabError(msg) {
  labErrBnr.textContent = msg; labErrBnr.hidden = false;
}

function renderTrace(t, mode) {
  if (!t) return;
  document.getElementById('preview-summary').hidden = false;
  setText('trace-mode',    mode);
  const opEl = document.getElementById('trace-op');
  opEl.textContent = t.operation || '—';
  opEl.className = 'op-badge ' + opClass(t.operation);
  setText('trace-floor',   t.floorEntry   ? `[${t.floorEntry.start},${t.floorEntry.end}]` : 'null');
  setText('trace-ceiling', t.ceilingEntry ? `[${t.ceilingEntry.start},${t.ceilingEntry.end}]` : 'null');
  setText('trace-dup',     t.alreadyExists ? 'true' : 'false');
  setText('trace-left',    t.connectsLeft  ? 'true' : 'false');
  setText('trace-right',   t.connectsRight ? 'true' : 'false');
  setText('trace-counts',  `${t.intervalsBefore ?? '?'} → ${t.intervalsAfter ?? '?'}`);
  setText('trace-latency', t.durationNanos ? `${(t.durationNanos / 1000).toFixed(1)} µs` : '—');

  // Steps
  const list = document.getElementById('trace-steps-list');
  const steps = t.steps || [];
  list.innerHTML = steps.length
    ? steps.map((s, i) => `<li><span class="step-idx">${i + 1}</span>${escHtml(s)}</li>`).join('')
    : '<li class="step-empty">No step trace available.</li>';

  // Diff visual
  const diffVis = document.getElementById('trace-diff-visual');
  diffVis.hidden = false;
  renderPills('trace-before-pills',  t.beforeIntervals || [], 'pill-rx');
  renderPills('trace-removed-pills', t.removedIntervals?.map(i => i) || [], 'pill-rem');
  renderPills('trace-created-pills', t.createdOrUpdatedInterval ? [t.createdOrUpdatedInterval] : [], 'pill-new');
  renderPills('trace-after-pills',   t.afterIntervals || [], 'pill-rx');
}

function opClass(op) {
  if (!op) return '';
  if (op.includes('BRIDGE') || op.includes('MERGE')) return 'op-bridge-merge';
  if (op.includes('DUPLICATE')) return 'op-duplicate';
  if (op.includes('LEFT')) return 'op-left-extension';
  if (op.includes('RIGHT')) return 'op-right-extension';
  return '';
}

/* ---- Arrival Simulator ----------------------------------- */
let simSession = null;
let simAutoTimer = null;

const PRESETS = {
  demo:       '1, 3, 7, 2, 6, 4, 5, 3',
  sequential: '1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12',
  reverse:    '12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1',
  sparse:     '0, 500000, 1000000000, 1, 499999',
  random:     [...Array(15)].map(() => Math.floor(Math.random() * 100)).join(', '),
  duplicates: '5, 6, 5, 7, 6, 4, 8, 5'
};

document.getElementById('sim-preset-select').addEventListener('change', e => {
  document.getElementById('sim-sequence-input').value = PRESETS[e.target.value] || '';
});

document.getElementById('sim-hackathon-demo-btn').addEventListener('click', () => {
  document.getElementById('sim-sequence-input').value = PRESETS.demo;
  startSimulation();
});

document.getElementById('sim-start-btn').addEventListener('click', startSimulation);

async function startSimulation() {
  const raw  = document.getElementById('sim-sequence-input').value;
  const ids  = raw.split(/[\s,]+/).filter(Boolean).map(Number).filter(n => !isNaN(n));
  if (!ids.length) return;
  try {
    const r = await api('/api/simulation/start', { method: 'POST', body: JSON.stringify({ sequence: ids, modeName: 'STEP' }) });
    simSession = r.sessionId;
    renderSimState(r);
    setSimControls(true);
    clearInterval(simAutoTimer);
  } catch (e) { console.error(e); }
}

document.getElementById('sim-next-btn').addEventListener('click', async () => {
  if (!simSession) return;
  try { renderSimState(await api('/api/simulation/step', { method: 'POST', body: JSON.stringify({ sessionId: simSession, direction: 'FORWARD' }) })); }
  catch (e) { console.error(e); }
});

document.getElementById('sim-prev-btn').addEventListener('click', async () => {
  if (!simSession) return;
  try { renderSimState(await api('/api/simulation/step', { method: 'POST', body: JSON.stringify({ sessionId: simSession, direction: 'BACKWARD' }) })); }
  catch (e) { console.error(e); }
});

document.getElementById('sim-play-btn').addEventListener('click', () => {
  const speed = Number(document.getElementById('sim-speed').value) || 800;
  simAutoTimer = setInterval(async () => {
    if (!simSession) return clearInterval(simAutoTimer);
    try {
      const r = await api('/api/simulation/step', { method: 'POST', body: JSON.stringify({ sessionId: simSession, direction: 'FORWARD' }) });
      renderSimState(r);
      if (r.currentStep >= r.totalSteps) clearInterval(simAutoTimer);
    } catch { clearInterval(simAutoTimer); }
  }, speed);
  document.getElementById('sim-pause-btn').disabled = false;
  document.getElementById('sim-play-btn').disabled  = true;
});

document.getElementById('sim-pause-btn').addEventListener('click', () => {
  clearInterval(simAutoTimer);
  document.getElementById('sim-play-btn').disabled  = false;
  document.getElementById('sim-pause-btn').disabled = true;
});

document.getElementById('sim-reset-btn').addEventListener('click', () => {
  clearInterval(simAutoTimer); simSession = null; setSimControls(false);
  setText('sim-step-counter', 'Step 0 / 0');
  setText('sim-cur-id', '—'); setText('sim-op', '—');
  document.getElementById('sim-visualizer').innerHTML = '<div class="empty-msg">Simulation idle.</div>';
});

function setSimControls(active) {
  ['sim-play-btn','sim-pause-btn','sim-prev-btn','sim-next-btn','sim-reset-btn']
    .forEach(id => { document.getElementById(id).disabled = !active; });
  document.getElementById('sim-pause-btn').disabled = true;
}

function renderSimState(r) {
  setText('sim-step-counter', `Step ${r.currentStep ?? 0} / ${r.totalSteps ?? 0}`);
  setText('sim-cur-id', r.currentPackageId ?? '—');
  setText('sim-op',     r.lastOperation    ?? '—');
  setText('sim-unique', r.totalUniquePackages ?? 0);
  setText('sim-icount', r.intervalCount ?? 0);
  setText('sim-dups',   r.duplicateCount ?? 0);
  document.getElementById('sim-message').textContent = r.message || '';
  renderSimVisualizer(r.intervals || []);
}

function renderSimVisualizer(intervals) {
  const track = document.getElementById('sim-visualizer');
  track.innerHTML = '';
  if (!intervals.length) { track.innerHTML = '<div class="empty-msg">No intervals yet.</div>'; return; }
  const pills = document.getElementById('sim-pills');
  pills.innerHTML = intervals.map(iv =>
    `<span class="pill pill-rx">[${iv.start}, ${iv.end}]</span>`
  ).join('');
}

/* ---- Benchmark ------------------------------------------- */
document.getElementById('benchmark-form').addEventListener('submit', async e => {
  e.preventDefault();
  const btn = document.getElementById('bench-run-btn');
  btn.disabled = true; btn.textContent = 'Running…';
  try {
    const r = await api('/api/benchmark/run', {
      method: 'POST',
      body: JSON.stringify({
        datasetSize:  Number(document.getElementById('bench-size').value),
        pattern:      document.getElementById('bench-pattern').value,
        repetitions:  Number(document.getElementById('bench-runs').value)
      })
    });
    document.getElementById('bench-results-area').hidden = false;
    setText('bench-tree-ms',  (r.treeMapMedianMs ?? 0).toFixed(2) + ' ms');
    setText('bench-sort-ms',  (r.sortingMedianMs ?? 0).toFixed(2) + ' ms');
    const speedup = r.sortingMedianMs > 0 ? (r.sortingMedianMs / r.treeMapMedianMs).toFixed(2) : '—';
    setText('bench-speedup',  speedup + 'x');
    setText('bench-merges',   r.bridgeMerges ?? 0);
    setText('bench-extensions', r.extensions ?? 0);

    renderBarChart('chart-exec-time', [
      { label: 'TreeMap', val: r.treeMapMedianMs ?? 0, cls: 'bar-tree' },
      { label: 'Sorting', val: r.sortingMedianMs ?? 0, cls: 'bar-sort' }
    ]);
    renderBarChart('chart-latency', [
      { label: 'TreeMap avg', val: (r.treeMapAvgLatencyNs ?? 0) / 1000, cls: 'bar-tree' },
      { label: 'Sort avg',    val: (r.sortingAvgLatencyNs ?? 0) / 1000, cls: 'bar-sort' }
    ]);

    document.getElementById('bench-tbody').innerHTML = `
      <tr><td>TreeMap Incremental</td><td class="success-text">${(r.treeMapMedianMs??0).toFixed(2)}</td>
          <td>${(r.treeMapAvgLatencyNs??0).toFixed(0)}</td><td>${(r.treeMapMaxLatencyNs??0).toFixed(0)}</td>
          <td>${r.treeMapIntervals??0}</td><td>${r.treeMapDuplicates??0}</td></tr>
      <tr><td>Sorting Baseline</td><td class="warn-text">${(r.sortingMedianMs??0).toFixed(2)}</td>
          <td>${(r.sortingAvgLatencyNs??0).toFixed(0)}</td><td>${(r.sortingMaxLatencyNs??0).toFixed(0)}</td>
          <td>—</td><td>${r.sortingDuplicates??0}</td></tr>`;
  } catch (err) {
    console.error(err);
    document.getElementById('bench-note').textContent = 'Benchmark failed: ' + err.message;
    document.getElementById('bench-note').hidden = false;
  } finally {
    btn.disabled = false; btn.textContent = 'Run Live Benchmark';
  }
});

function renderBarChart(containerId, bars) {
  const c = document.getElementById(containerId);
  if (!c) return;
  const max = Math.max(...bars.map(b => b.val), 1);
  c.innerHTML = bars.map(b => {
    const pct = Math.max((b.val / max) * 100, 2);
    return `<div class="bar-col">
      <div class="bar ${b.cls}" style="height:${pct}px;" title="${b.val.toFixed(2)}"></div>
      <span class="bar-lbl">${b.label}</span>
      <span class="bar-lbl" style="color:var(--text)">${b.val.toFixed(1)}</span>
    </div>`;
  }).join('');
}

/* ---- Analytics ------------------------------------------- */
document.getElementById('analytics-form').addEventListener('submit', async e => {
  e.preventDefault();
  const start = Number(document.getElementById('ana-start').value);
  const end   = Number(document.getElementById('ana-end').value);
  const errEl = document.getElementById('analytics-error');
  errEl.hidden = true;
  try {
    const r = await api(`/api/packages/analytics?start=${start}&end=${end}`);
    document.getElementById('analytics-output').hidden = false;
    const pct = r.coveragePercent ?? 0;
    setText('ana-pct-label', pct.toFixed(1) + '%');
    document.getElementById('ana-progress').value = pct;
    setText('ana-expected', r.expectedCount ?? 0);
    setText('ana-received', r.receivedCount ?? 0);
    setText('ana-missing',  r.missingCount  ?? 0);
    setText('ana-largest-gap',  r.largestMissingRange  ? `[${r.largestMissingRange.start},${r.largestMissingRange.end}]` : '—');
    setText('ana-smallest-gap', r.smallestMissingRange ? `[${r.smallestMissingRange.start},${r.smallestMissingRange.end}]` : '—');
    setText('ana-rx-count',   (r.receivedIntervals || []).length);
    setText('ana-miss-count', (r.missingIntervals  || []).length);
    renderPills('ana-rx-pills',   r.receivedIntervals || [], 'pill-rx');
    renderPills('ana-miss-pills', r.missingIntervals  || [], 'pill-miss');
  } catch (err) {
    errEl.textContent = err.message; errEl.hidden = false;
  }
});

/* ---- Export ---------------------------------------------- */
document.querySelectorAll('.export-btn').forEach(btn => {
  btn.addEventListener('click', async () => {
    const type = btn.dataset.export;
    const start = document.getElementById('ana-start')?.value || 1;
    const end   = document.getElementById('ana-end')?.value   || 100;
    try {
      const r = await api(`/api/packages/export?type=${type}&start=${start}&end=${end}`);
      const url  = URL.createObjectURL(r.blob);
      const name = (r.disposition.match(/filename="?([^"]+)"?/) || [])[1] || `export.${type}`;
      const a = Object.assign(document.createElement('a'), { href: url, download: name });
      document.body.appendChild(a); a.click(); document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (e) { alert('Export failed: ' + e.message); }
  });
});

/* ---- Audit Log ------------------------------------------- */
async function loadAuditLog() {
  const filter = document.getElementById('audit-filter').value;
  try {
    const entries = await api(`/api/packages/activity?operation=${filter}`);
    const tbody = document.getElementById('audit-tbody');
    if (!entries.length) {
      tbody.innerHTML = '<tr><td colspan="8" class="empty-cell">No audit entries recorded yet.</td></tr>';
      return;
    }
    tbody.innerHTML = entries.map(e => `
      <tr>
        <td>${e.sequenceId ?? ''}</td>
        <td>${e.timestamp ? e.timestamp.substring(11, 19) : ''}</td>
        <td>${e.packageId ?? '—'}</td>
        <td><span class="op-badge ${opClass(e.operation)}">${e.operation ?? ''}</span></td>
        <td><span class="pill ${e.status === 'SUCCESS' ? 'pill-rx' : 'pill-miss'}">${e.status ?? ''}</span></td>
        <td>${e.intervalsBefore ?? '?'} → ${e.intervalsAfter ?? '?'}</td>
        <td>${e.durationNanos ? Math.round(e.durationNanos / 1000) + ' µs' : '—'}</td>
        <td>${escHtml(e.message || '')}</td>
      </tr>
    `).join('');

    // Update audit KPIs from stats
    const stats = await api('/api/packages/statistics');
    setText('audit-dups',     stats.totalDuplicates ?? 0);
    setText('audit-invalid',  stats.invalidRequestCount ?? 0);
    const total = (stats.totalUniquePackages ?? 0) + (stats.totalDuplicates ?? 0);
    const pct = total > 0 ? ((stats.totalDuplicates / total) * 100).toFixed(1) : '0.0';
    setText('audit-dup-pct', pct + '%');
  } catch (e) { console.error(e); }
}

document.getElementById('refresh-audit-btn').addEventListener('click', loadAuditLog);
document.getElementById('audit-filter').addEventListener('change', loadAuditLog);

/* ---- Live API Activity Log (small feed) ------------------ */
function logActivity(msg, color) {
  const container = document.getElementById('api-activity-log');
  if (!container) return;
  const entry = document.createElement('div');
  entry.style.cssText = `font-size:.75rem;font-family:var(--mono);color:${color || 'var(--text-sec)'};padding:.25rem 0;border-bottom:1px solid var(--border-subtle)`;
  entry.textContent = `${new Date().toLocaleTimeString()} ${msg}`;
  container.prepend(entry);
  while (container.children.length > 50) container.lastChild.remove();
  document.getElementById('api-log-empty')?.remove();
}

document.getElementById('clear-log-btn')?.addEventListener('click', () => {
  const c = document.getElementById('api-activity-log');
  if (c) c.innerHTML = '<div id="api-log-empty" style="font-size:.78rem;color:var(--text-muted);padding:.5rem 0">Waiting for API operations...</div>';
});

/* ---- Reset dialog ---------------------------------------- */
document.getElementById('open-reset-btn').addEventListener('click', () => document.getElementById('reset-dialog').showModal());
document.getElementById('cancel-reset-btn').addEventListener('click', () => document.getElementById('reset-dialog').close());
document.getElementById('confirm-reset-btn').addEventListener('click', async () => {
  document.getElementById('reset-dialog').close();
  try {
    await api('/api/packages', { method: 'DELETE' });
    lastModifiedInterval = null;
    loadStats(); renderTopology(); loadIntervalTable(); loadAuditLog();
    logActivity('DELETE /api/packages → Index reset', '#f87171');
    showFeedback('Index successfully reset.', 'success');
  } catch (e) { showFeedback('Reset failed: ' + e.message, 'error'); }
});

/* ---- Undo dialog ----------------------------------------- */
document.getElementById('open-undo-btn').addEventListener('click', () => document.getElementById('undo-dialog').showModal());
document.getElementById('cancel-undo-btn').addEventListener('click', () => document.getElementById('undo-dialog').close());
document.getElementById('confirm-undo-btn').addEventListener('click', async () => {
  document.getElementById('undo-dialog').close();
  try {
    const r = await api('/api/packages/undo', { method: 'POST' });
    loadStats(); renderTopology(); loadIntervalTable();
    logActivity(`POST /api/packages/undo → reverted ID ${r.revertedPackageId ?? '?'}`, '#fbbf24');
    showFeedback(`Undone: ID ${r.revertedPackageId ?? '?'} reverted (${r.operation ?? ''})`, 'warn');
  } catch (e) { showFeedback('Undo failed: ' + e.message, 'error'); }
});

/* ---- View switch callbacks ------------------------------- */
navItems.forEach(btn => {
  btn.addEventListener('click', () => {
    switch (btn.dataset.view) {
      case 'explorer':  loadIntervalTable(); break;
      case 'logs':      loadAuditLog(); break;
    }
  });
});

/* ---- Utilities ------------------------------------------- */
function escHtml(s) {
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

/* ---- Overview Intervals mini-table ----------------------- */
async function loadOverviewTable() {
  try {
    const r = await api('/api/packages/intervals');
    const intervals = r.intervals || [];
    const tbody = document.getElementById('ov-intervals-tbody');
    const badge = document.getElementById('explorer-count-badge-ov');
    if (badge) badge.textContent = `${intervals.length} intervals`;
    if (!tbody) return;
    if (!intervals.length) {
      tbody.innerHTML = '<tr><td colspan="5" class="empty-cell">No active intervals in index.</td></tr>';
      return;
    }
    tbody.innerHTML = intervals.slice(0, 50).map((iv, i) =>
      `<tr>
        <td>${i + 1}</td>
        <td>[${iv.start}, ${iv.end}]</td>
        <td>${iv.start}</td>
        <td>${iv.end}</td>
        <td>${iv.end - iv.start + 1}</td>
      </tr>`
    ).join('');
  } catch (e) { console.error('ov-table', e); }
}

/* ---- Initial load ---------------------------------------- */
loadStats();
renderTopology();
loadOverviewTable();
