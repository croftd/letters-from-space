// Letters from Space - dashboard script. It talks to lettersfromspace.web.DashboardServer through the
// JSON API under /api/ (see DashboardServer.java). No libraries: plain DOM, <canvas> for frames and
// weights, <svg> for the charts, so the page works offline.

import * as engine from './engine/core.js';   // the network in JavaScript: draws the starting weights of the diagram

const W = 8;                 // frame width and height, as in Frame.java
const H = 8;
const N = W * H;
const $ = (id) => document.getElementById(id);

// ------------------------------------------------------------------ state

const DEFAULT_LETTER = 'D';

const state = {
  defaults: null,        // default settings, from /api/status
  videoSize: 20000,
  letters: [],           // [{letter, samples: [...], userSamples: [...]}] from /api/letters
  current: DEFAULT_LETTER,
  set: null,             // the training set of the current letter: {positives: [...], negatives: [{letter, frame}]}
  setKey: '',            // the settings that produced state.set
  samplesVersion: 0,     // bumped when the student adds or removes samples
  results: {},           // letter -> last /api/train answer (+ .setKey)
  replaying: null,       // the letter whose training is being replayed after "Run back-propagation" (figures held back)
  previous: {},          // letter -> the run before it, for comparing error curves
  scans: {},             // letter -> last /api/scan answer
  rejected: {},          // letter -> Set of frame indexes the student unticked
  trained: {},           // letter -> {epochs, finalError, reachedThreshold, hits} as in /api/status
  message: null,         // last /api/message answer
  selectedUnit: -1,      // hidden unit whose weights are shown as numbers
  stopAll: false,
};

const SETTINGS = [       // input id -> setting name -> parser
  ['f-variants', 'variants', 'int'], ['f-noise', 'noise', 'int'], ['f-sky', 'sky', 'int'], ['f-seed', 'seed', 'int'], ['f-fresh', 'fresh', 'bool'],
  ['f-hidden', 'hidden', 'int'], ['f-lr', 'learningRate', 'float'], ['f-momentum', 'momentum', 'float'],
  ['f-epochs', 'maxEpochs', 'int'], ['f-errthr', 'errorThreshold', 'float'], ['f-bias', 'bias', 'bool'],
  ['f-threshold', 'threshold', 'float'],
];

// ------------------------------------------------------------------ small helpers

function el(tag, attrs = {}, ...children) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') e.className = v;
    else if (k === 'text') e.textContent = v;
    else if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
    else e.setAttribute(k, v);
  }
  for (const c of children) e.append(c);
  return e;
}

function svgEl(tag, attrs = {}) {
  const e = document.createElementNS('http://www.w3.org/2000/svg', tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'text') e.textContent = v;
    else e.setAttribute(k, v);
  }
  return e;
}

const fmtInt = (n) => Math.round(n).toLocaleString('en-US');
const fmtScore = (s) => s.toFixed(s >= 0.995 && s < 1 ? 3 : 2);
const fmtSigned = (w) => (w >= 0 ? '+' : '−') + Math.abs(w).toFixed(2);
const letterName = (c) => (c === '_' ? 'space (_)' : c === "'" ? "apostrophe (')" : c);
const debounce = (fn, ms) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; };
const cssVar = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();

function store(key, value) {
  try { localStorage.setItem('lfs.' + key, JSON.stringify(value)); } catch { /* storage unavailable: ignore */ }
}

function recall(key) {
  try { const v = localStorage.getItem('lfs.' + key); return v === null ? undefined : JSON.parse(v); } catch { return undefined; }
}

let noticeTimer;
function notice(message, isError = false) {
  const n = $('notice');
  n.textContent = message;
  n.classList.toggle('error', isError);
  n.classList.remove('hidden');
  clearTimeout(noticeTimer);
  noticeTimer = setTimeout(() => n.classList.add('hidden'), isError ? 8000 : 4000);
}

// ------------------------------------------------------------------ server

async function api(path, params = {}, method = 'GET') {
  if (state.lab) return state.lab.handle(path, params);      // the lab running in this browser (static hosting)
  const body = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null) body.append(k, String(v));
  }
  let url = '/api/' + path;
  const init = { method };
  if (method === 'GET') {
    const q = body.toString();
    if (q) url += '?' + q;
  } else {
    init.body = body;
  }
  let response;
  try {
    response = await fetch(url, init);
  } catch {
    setServerChip(false);
    throw new Error('Cannot reach the server. Is DashboardServer still running?');
  }
  let data;
  try {
    data = await response.json();
  } catch {
    // not JSON: something answered, but not the lab's server (for example a plain web host)
    setServerChip(false);
    throw new Error('Unexpected answer from the server (HTTP ' + response.status + ')');
  }
  setServerChip(true);
  if (!response.ok) throw new Error(data.error || 'HTTP ' + response.status);
  return data;
}

function setServerChip(ok) {
  const chip = $('chip-server');
  chip.classList.toggle('ok', ok);
  chip.classList.toggle('bad', !ok);
  if (state.lab) {
    chip.lastElementChild.textContent = 'runs in your browser · ' + fmtInt(state.videoSize) + ' frames · ' + state.lab.poolSize
      + (state.lab.poolSize === 1 ? ' training thread' : ' training threads');
    return;
  }
  chip.lastElementChild.textContent = ok ? 'server connected · ' + fmtInt(state.videoSize) + ' frames' : 'server not reachable';
}

function updateTrainedChip() {
  const n = Object.keys(state.trained).length;
  $('chip-trained').firstElementChild.textContent = n + (n === 1 ? ' detector' : ' detectors') + ' trained';
  for (const tile of document.querySelectorAll('.letter-tile')) {
    tile.querySelector('.badge').classList.toggle('hidden', !state.trained[tile.dataset.letter]);
  }
}

// ------------------------------------------------------------------ settings

function settings() {
  const s = {};
  for (const [id, name, type] of SETTINGS) {
    const input = $(id);
    if (type === 'bool') s[name] = input.checked;
    else {
      const v = type === 'int' ? parseInt(input.value, 10) : parseFloat(input.value);
      s[name] = Number.isFinite(v) ? v : state.defaults[name];
    }
  }
  return s;
}

function applySettings(values) {
  for (const [id, name, type] of SETTINGS) {
    if (values[name] === undefined) continue;
    if (type === 'bool') $(id).checked = !!values[name];
    else $(id).value = values[name];
  }
  $('threshold-out').textContent = settings().threshold.toFixed(2);
}

/** The settings that decide the training set: the scores of a run only match a set built the same way. */
function setKeyOf(s) {
  return [s.variants, s.noise, s.sky, s.seed, state.samplesVersion].join('/');
}

function trainParams(letter, s) {
  return {
    letter, hidden: s.hidden, learningRate: s.learningRate, momentum: s.momentum, maxEpochs: s.maxEpochs,
    errorThreshold: s.errorThreshold, bias: s.bias, variants: s.variants, noise: s.noise, sky: s.sky, seed: s.seed, fresh: s.fresh,
  };
}

// ------------------------------------------------------------------ frames: 8x8 night sky on a canvas

function sizeCanvas(canvas, width, height) {
  const dpr = Math.max(1, Math.round(window.devicePixelRatio || 1));
  canvas.width = width * dpr;
  canvas.height = height * dpr;
  canvas.style.width = width + 'px';
  canvas.style.height = height + 'px';
}

/**
 * Draws a frame given as 64 characters 'O'/'.' (only the first `upto` pixels when animating). The canvas is
 * one cell wider and taller than the frame: half a cell of dark sky all around keeps the edge stars visible.
 */
function drawFrame(canvas, compact, upto = N) {
  const ctx = canvas.getContext('2d');
  const cell = Math.floor(canvas.width / (W + 1));
  const pad = Math.floor((canvas.width - W * cell) / 2);
  ctx.fillStyle = cssVar('--sky');
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = cssVar('--star');
  for (let i = 0; i < Math.min(upto, N); i++) {
    if (compact[i] === 'O') ctx.fillRect(pad + (i % W) * cell, pad + Math.floor(i / W) * cell, cell, cell);
  }
}

function frameCanvas(compact, cell = 4) {
  const c = el('canvas', { class: 'frame' });
  sizeCanvas(c, (W + 1) * cell, (H + 1) * cell);
  drawFrame(c, compact);
  return c;
}

// ------------------------------------------------------------------ tooltip

const tip = $('tooltip');

/** Shows the tooltip near (x, y). `value` is the strong line; `rows` are secondary lines (strings or [text, keyClass]). */
function showTip(x, y, value, rows = []) {
  const children = [el('div', { class: 'tt-value', text: value })];
  for (const r of rows) {
    const [text, key] = Array.isArray(r) ? r : [r, null];
    const row = el('div', { class: 'tt-row' });
    if (key !== null) row.append(el('span', { class: 'tt-key ' + key }));
    row.append(document.createTextNode(text));
    children.push(row);
  }
  tip.replaceChildren(...children);
  tip.classList.remove('hidden');
  const r = tip.getBoundingClientRect();
  let left = x + 14;
  let top = y + 14;
  if (left + r.width > window.innerWidth - 8) left = x - r.width - 14;
  if (top + r.height > window.innerHeight - 8) top = y - r.height - 14;
  tip.style.left = Math.max(8, left) + 'px';
  tip.style.top = Math.max(8, top) + 'px';
}

function hideTip() {
  tip.classList.add('hidden');
}

/** Hover and keyboard focus on an element show the same tooltip. */
function attachTip(element, content) {
  element.addEventListener('pointermove', (e) => { const [v, rows] = content(); showTip(e.clientX, e.clientY, v, rows); });
  element.addEventListener('pointerleave', hideTip);
  element.addEventListener('focus', () => {
    const b = element.getBoundingClientRect();
    const [v, rows] = content();
    showTip(b.right, b.top, v, rows);
  });
  element.addEventListener('blur', hideTip);
}

// ------------------------------------------------------------------ pixel editor (used twice)

function buildEditor(container, onChange) {
  const cells = [];
  let paintValue = null;
  for (let i = 0; i < N; i++) {
    const b = el('button', { type: 'button', 'aria-pressed': 'false', 'aria-label': 'row ' + (Math.floor(i / W) + 1) + ', column ' + (i % W + 1) });
    b.dataset.i = i;
    b.addEventListener('click', (e) => {     // keyboard activation (pointer clicks are handled on pointerdown)
      if (e.detail === 0) { set(i, b.getAttribute('aria-pressed') !== 'true'); onChange(); }
    });
    b.addEventListener('keydown', (e) => {
      const moves = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -W, ArrowDown: W };
      if (moves[e.key] !== undefined) {
        const j = i + moves[e.key];
        if (j >= 0 && j < N && !(e.key === 'ArrowLeft' && i % W === 0) && !(e.key === 'ArrowRight' && i % W === W - 1)) cells[j].focus();
        e.preventDefault();
      }
    });
    cells.push(b);
    container.append(b);
  }
  const set = (i, on) => cells[i].setAttribute('aria-pressed', on ? 'true' : 'false');
  const cellAt = (e) => {
    const t = document.elementFromPoint(e.clientX, e.clientY);
    return t && t.parentNode === container ? cells.indexOf(t) : -1;
  };
  container.addEventListener('pointerdown', (e) => {
    const i = cellAt(e);
    if (i < 0) return;
    paintValue = cells[i].getAttribute('aria-pressed') !== 'true';
    set(i, paintValue);
    container.setPointerCapture(e.pointerId);
    onChange();
    e.preventDefault();
  });
  container.addEventListener('pointermove', (e) => {
    if (paintValue === null) return;
    const i = cellAt(e);
    if (i >= 0 && (cells[i].getAttribute('aria-pressed') === 'true') !== paintValue) { set(i, paintValue); onChange(); }
  });
  const stop = () => { paintValue = null; };
  container.addEventListener('pointerup', stop);
  container.addEventListener('pointercancel', stop);
  return {
    get: () => cells.map((c) => (c.getAttribute('aria-pressed') === 'true' ? 'O' : '.')).join(''),
    set: (compact) => { cells.forEach((c, i) => set(i, compact[i] === 'O')); onChange(); },
    clear: () => { cells.forEach((c, i) => set(i, false)); onChange(); },
  };
}

function masterOf(letter) {
  const entry = state.letters.find((l) => l.letter === letter);
  return entry ? entry.samples[0] : '.'.repeat(N);
}

/** Flips 1 to 4 random pixels, like the noise on the letters of the video. */
function addNoise(compact) {
  const a = compact.split('');
  const flips = 1 + Math.floor(Math.random() * 4);
  for (let k = 0; k < flips; k++) {
    const i = Math.floor(Math.random() * N);
    a[i] = a[i] === 'O' ? '.' : 'O';
  }
  return a.join('');
}

// ------------------------------------------------------------------ 1. training data

function buildLetterGrid() {
  const grid = $('letter-grid');
  grid.replaceChildren();
  for (const entry of state.letters) {
    const tile = el('button', { type: 'button', class: 'letter-tile', 'aria-pressed': 'false', 'aria-label': 'Letter ' + letterName(entry.letter) });
    tile.dataset.letter = entry.letter;
    tile.append(frameCanvas(entry.samples[0], 4), el('span', { class: 'ch', text: entry.letter }), el('span', { class: 'badge hidden', title: 'detector trained' }));
    tile.addEventListener('click', () => selectLetter(entry.letter, true));
    grid.append(tile);
  }
}

/** Makes `letter` the current letter; `chosen` when the visitor picked it, so the page reopens on it next time. */
function selectLetter(letter, chosen = false) {
  state.current = letter;
  if (state.replaying && state.replaying !== letter) endReplay();
  if (chosen) store('chosenLetter', letter);
  for (const tile of document.querySelectorAll('.letter-tile')) tile.setAttribute('aria-pressed', String(tile.dataset.letter === letter));
  const name = letterName(letter);
  $('nav-letter').textContent = letter;
  $('btn-add-sample').textContent = 'Add to the samples of ' + letter;
  $('btn-clear-samples').textContent = 'Remove my samples of ' + letter;
  $('btn-train').textContent = 'Run back-propagation for ' + letter;
  $('btn-scan').textContent = 'Scan ' + fmtInt(state.videoSize) + ' frames for ' + letter;
  $('test-label').textContent = 'Output of the ' + name + ' detector';
  updateUserSampleCount();
  drawNetworkDiagram();
  loadSet();
  renderTrainResult();
  renderScan();
  renderFilmstrip();
  updateTest();
}

function updateUserSampleCount() {
  const entry = state.letters.find((l) => l.letter === state.current);
  const n = entry ? entry.userSamples.length : 0;
  $('user-sample-count').textContent = n === 0 ? 'No samples of your own yet.' : n + (n === 1 ? ' sample' : ' samples') + ' of your own.';
}

const loadSetSoon = debounce(() => loadSet(), 300);

async function loadSet() {
  const s = settings();
  const key = setKeyOf(s) + '/' + state.current;
  try {
    const set = await api('trainingset', { letter: state.current, variants: s.variants, noise: s.noise, sky: s.sky, seed: s.seed });
    if (set.letter !== state.current) return;           // the student picked another letter meanwhile
    state.set = set;
    state.setKey = key;
    renderSet();
  } catch (e) {
    notice(e.message, true);
  }
}

const MAX_SHOWN = 300;   // frames shown per group; the rest are summarised

function renderSet() {
  const set = state.set;
  if (!set) return;
  const result = state.results[state.current];
  const scored = result && result.setKey === state.setKey
    && result.positiveScores.length === set.positives.length && result.negativeScores.length === set.negatives.length;
  const posScores = scored ? result.positiveScores : null;
  const negScores = scored ? result.negativeScores : null;

  const tile = (frame, caption, score, isPositive) => {
    const wrong = score !== null && (isPositive ? score < 0.5 : score >= 0.5);
    const t = el('div', { class: 'tile' + (wrong ? ' wrong' : ''), tabindex: '0' });
    t.append(frameCanvas(frame, 4));
    t.append(el('span', { class: wrong ? 'score' : '', text: wrong ? fmtScore(score) : caption }));
    t.dataset.frame = frame;
    attachTip(t, () => {
      const what = isPositive ? 'positive: should score 1' : caption === 'sky' ? 'empty sky: should score 0' : 'letter ' + caption + ': should score 0';
      return score === null ? [what, ['not trained with these settings yet']] : ['score ' + fmtScore(score), [what]];
    });
    return t;
  };

  const pos = $('pos-frames');
  pos.replaceChildren(...set.positives.slice(0, MAX_SHOWN).map((f, i) => tile(f, '', posScores ? posScores[i] : null, true)));
  $('pos-title').textContent = 'Positives (' + set.positives.length + ')';

  const letters = [];
  const sky = [];
  set.negatives.forEach((n, i) => (n.letter ? letters : sky).push([n, i]));
  const negTile = ([n, i]) => tile(n.frame, n.letter || 'sky', negScores ? negScores[i] : null, false);
  $('neg-letter-frames').replaceChildren(...letters.slice(0, MAX_SHOWN).map(negTile));
  $('neg-sky-frames').replaceChildren(...sky.slice(0, MAX_SHOWN).map(negTile));
  if (sky.length > MAX_SHOWN) $('neg-sky-frames').append(el('span', { class: 'small muted', text: '… and ' + (sky.length - MAX_SHOWN) + ' more' }));
  $('neg-title').textContent = 'Negatives (' + set.negatives.length + ')';
  $('neg-letters-label').textContent = 'Other letters (' + letters.length + ')';
  $('neg-sky-label').textContent = 'Empty sky (' + sky.length + ')';
  $('neg-sky-label').classList.toggle('hidden', sky.length === 0);
  $('datfile').textContent = datFile(set.positives);
}

function datFile(frames, rowsShown = Infinity) {
  const lines = ['# Positive samples of ' + letterName(state.current) + ': ' + frames.length + ' frames, 8 rows each', '', 'LETTER=' + state.current];
  let rows = 0;
  for (const f of frames) {
    for (let r = 0; r < H && rows < rowsShown; r++, rows++) lines.push(f.slice(r * W, r * W + W));
    if (rows >= rowsShown) break;
    lines.push('');
  }
  return lines.join('\n');
}

/** "Simulate filling in": every sample is drawn again pixel by pixel, the way the 2001 students typed their .dat files. */
let simulation = null;

function simulateFilling() {
  if (simulation) { simulation.skip = true; return; }
  const canvases = [...document.querySelectorAll('#pos-frames .tile, #neg-letter-frames .tile, #neg-sky-frames .tile')];
  if (canvases.length === 0) return;
  const frames = canvases.map((t) => t.dataset.frame);
  const positives = document.querySelectorAll('#pos-frames .tile').length;
  canvases.forEach((t) => drawFrame(t.querySelector('canvas'), frames[0], 0));
  $('datfile-details').open = true;
  const pre = $('datfile');
  const button = $('btn-simulate');
  button.textContent = 'Skip';
  simulation = { skip: false };
  const set = state.set;
  let tileIndex = 0;
  let pixel = 0;
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const step = () => {
    // speed up as we go: the first sample pixel by pixel, the next ones row by row, then whole frames
    let budget = tileIndex === 0 ? 1 : tileIndex < 3 ? 4 : tileIndex < 10 ? 32 : 192;
    if (simulation.skip || reduceMotion) budget = Infinity;
    if (state.set !== set) tileIndex = canvases.length;              // another letter was picked: stop
    while (budget > 0 && tileIndex < canvases.length) {
      const take = Math.min(budget, N - pixel);
      pixel += take;
      budget -= take;
      drawFrame(canvases[tileIndex].querySelector('canvas'), frames[tileIndex], pixel);
      if (tileIndex < positives) {
        const rowsDone = tileIndex * H + Math.floor(pixel / W);
        pre.textContent = datFile(set.positives.slice(0, positives), rowsDone);
        pre.scrollTop = pre.scrollHeight;
      }
      if (pixel >= N) { tileIndex++; pixel = 0; }
    }
    if (tileIndex < canvases.length) {
      requestAnimationFrame(step);
    } else {
      simulation = null;
      button.textContent = 'Simulate filling in';
      pre.textContent = datFile(state.set.positives);
    }
  };
  requestAnimationFrame(step);
}

async function addSample() {
  const frame = drawEditor.get();
  try {
    const r = await api('samples', { letter: state.current, frame }, 'POST');
    state.letters.find((l) => l.letter === r.letter).userSamples = r.userSamples;
    state.samplesVersion++;
    updateUserSampleCount();
    notice('Added. Run back-propagation again so the detector learns from it.');
    loadSet();
  } catch (e) {
    notice(e.message, true);
  }
}

async function clearSamples() {
  try {
    const r = await api('samples/clear', { letter: state.current }, 'POST');
    state.letters.find((l) => l.letter === r.letter).userSamples = [];
    state.samplesVersion++;
    updateUserSampleCount();
    loadSet();
  } catch (e) {
    notice(e.message, true);
  }
}

// ------------------------------------------------------------------ 2. network diagram

// Before training the diagram shows the network training will start from: its random starting weights,
// drawn in this page by the JavaScript translation of MultiLayerNetwork (the same numbers as the Java
// code for the same seed). After "Run back-propagation" it replays the training recorded by the server
// (/api/train?trace=N): the weights, the hidden activations and the answer on the letter's master
// glyph, frame by frame.

const ANIM_SECONDS = 10;
const ANIM_FRAMES = 90;           // snapshots asked from the server
const MAX_HIDDEN_DRAWN = 16;
const START_SCALE = 0.5;          // the weight drawn as a full-strength line before training (they start below 0.2)
let anim = null;                  // the replay: {trace, letter, pos, playing, raf, selected, parts, start}

function drawNetworkDiagram() {
  stopAnim();
  const r = state.results[state.current];
  if (r && r.trace) {
    const pos = state.replaying === state.current ? 0 : 1;          // a replay about to play starts at epoch 0
    anim = { trace: r.trace, letter: state.current, pos, playing: false, raf: 0, selected: busiestPixel(r.trace) };
    buildAnimatedDiagram();
    applyFrame(pos);
  } else {
    const trace = startingTrace();
    if (trace) {
      const master = trace.master;
      anim = { trace, letter: state.current, pos: 0, playing: false, raf: 0, selected: Math.max(0, master.indexOf('O')), start: true };
      buildAnimatedDiagram();
      applyFrame(0);
    } else {
      anim = null;
      buildStaticDiagram();
    }
  }
}

/**
 * The network as training will find it (its first run, before the first epoch), in the format of a
 * one-frame training trace; null when the settings cannot make a network.
 */
function startingTrace() {
  const s = settings();
  if (!Number.isInteger(s.hidden) || s.hidden < 1 || s.hidden > 256 || !Number.isSafeInteger(s.seed)) return null;
  const master = masterOf(state.current);
  const network = new engine.MultiLayerNetwork([N, s.hidden, 1], s.bias, s.seed);
  const tracer = new engine.Tracer(engine.frameFromCompact(master, state.current), 1);
  return { hiddenUnits: s.hidden, weightsPerUnit: N + (s.bias ? 1 : 0), bias: s.bias, master, epochs: 0, run: 1, seed: s.seed,
    frames: [tracer.snapshot(0, NaN, network)] };
}

/** Positions shared by both versions of the diagram, in viewBox units (the SVG scales to the panel). */
function diagramLayout(hiddenDrawn) {
  const spacing = 26;
  const top = 44;
  const width = 760;
  const height = Math.max(250, top + (hiddenDrawn - 1) * spacing + 64);
  const cy = top + ((hiddenDrawn - 1) * spacing) / 2;
  const cell = 15;
  const gx = 34;
  const gy = cy - 4 * cell;
  return {
    width, height, cy, cell, gx, gy, hx: 430, ox: 690,
    hy: (k) => top + k * spacing,
    pixel: (i) => [gx + (i % W) * cell + cell / 2, gy + Math.floor(i / W) * cell + cell / 2],
  };
}

function startDiagram(svg, L, hiddenCount, hiddenDrawn, bias) {
  svg.replaceChildren();
  svg.setAttribute('viewBox', '0 0 ' + L.width + ' ' + L.height);
  svg.append(svgEl('text', { x: L.hx, y: 18, 'text-anchor': 'middle',
    text: hiddenCount + ' hidden units' + (hiddenCount > hiddenDrawn ? ' (' + hiddenDrawn + ' drawn)' : '') }));
  svg.append(svgEl('text', { x: L.gx + 4 * L.cell, y: L.gy + 8 * L.cell + 22, 'text-anchor': 'middle', text: '64 inputs, one per pixel' }));
  svg.append(svgEl('text', { x: L.ox, y: L.cy + 40, 'text-anchor': 'middle', text: '1 output' }));
  svg.append(svgEl('text', { x: L.ox, y: L.cy + 56, 'text-anchor': 'middle', text: '"' + state.current + '?"' }));
  svg.setAttribute('aria-label', '64 inputs, ' + hiddenCount + ' hidden units' + (bias ? ' with a bias node' : '') + ', 1 output for the letter ' + letterName(state.current));
}

function drawInputGrid(svg, L, master, clickable) {
  svg.append(svgEl('rect', { class: 'pix', x: L.gx - 4, y: L.gy - 4, width: 8 * L.cell + 7, height: 8 * L.cell + 7, rx: 3 }));
  const cells = [];
  for (let i = 0; i < N; i++) {
    const r = svgEl('rect', { class: 'pix' + (master[i] === 'O' ? ' on' : '') + (clickable ? ' clickable' : ''),
      x: L.gx + (i % W) * L.cell, y: L.gy + Math.floor(i / W) * L.cell, width: L.cell - 1.5, height: L.cell - 1.5 });
    if (clickable) {
      r.addEventListener('click', () => { anim.selected = i; buildAnimatedDiagram(); applyFrame(anim.pos); });
      r.append(svgEl('title', { text: 'row ' + (Math.floor(i / W) + 1) + ', column ' + (i % W + 1) + ': click to follow the weights of this pixel' }));
    }
    cells.push(r);
    svg.append(r);
  }
  return cells;
}

function buildStaticDiagram() {
  const svg = $('net-diagram');
  $('net-figure-title').textContent = 'Inside the network';
  const s = settings();
  const drawn = Math.min(s.hidden, MAX_HIDDEN_DRAWN);
  const L = diagramLayout(drawn);
  startDiagram(svg, L, s.hidden, drawn, s.bias);
  const from = [L.gx + 8 * L.cell + 4, L.cy];
  for (let k = 0; k < drawn; k++) {
    svg.append(svgEl('line', { class: 'edge', x1: from[0], y1: from[1], x2: L.hx, y2: L.hy(k) }));
    svg.append(svgEl('line', { class: 'edge', x1: L.hx, y1: L.hy(k), x2: L.ox, y2: L.cy }));
  }
  drawInputGrid(svg, L, masterOf(state.current), false);
  for (let k = 0; k < drawn; k++) svg.append(svgEl('circle', { class: 'node', cx: L.hx, cy: L.hy(k), r: 8 }));
  svg.append(svgEl('circle', { class: 'node out', cx: L.ox, cy: L.cy, r: 16 }));
  $('anim-play').disabled = true;
  $('anim-pos').disabled = true;
  $('anim-readout').textContent = 'Run back-propagation for ' + letterName(state.current) + ' to watch the weights change.';
}

/** The input pixel whose 16 weights changed most during training: the one shown first. */
function busiestPixel(trace) {
  const first = trace.frames[0].hidden;
  const last = trace.frames[trace.frames.length - 1].hidden;
  const cols = trace.weightsPerUnit;
  let best = 0;
  let bestChange = -1;
  for (let i = 0; i < N; i++) {
    let change = 0;
    for (let u = 0; u < trace.hiddenUnits; u++) change += Math.abs(last[u * cols + i] - first[u * cols + i]);
    if (change > bestChange) { bestChange = change; best = i; }
  }
  return best;
}

function buildAnimatedDiagram() {
  const svg = $('net-diagram');
  const t = anim.trace;
  const drawn = Math.min(t.hiddenUnits, MAX_HIDDEN_DRAWN);
  const L = diagramLayout(drawn);
  startDiagram(svg, L, t.hiddenUnits, drawn, t.bias);
  const cols = t.weightsPerUnit;
  let maxIn = 1e-6;
  let maxOut = 1e-6;
  for (const f of t.frames) {
    for (let u = 0; u < t.hiddenUnits; u++) for (let i = 0; i < cols; i++) maxIn = Math.max(maxIn, Math.abs(f.hidden[u * cols + i]));
    for (const w of f.output) maxOut = Math.max(maxOut, Math.abs(w));
  }
  if (anim.start) {                                  // the starting weights are small: show them as small
    maxIn = Math.max(maxIn, START_SCALE);
    maxOut = Math.max(maxOut, START_SCALE);
  }
  const parts = { L, drawn, cols, maxIn, maxOut, color: divergingScale(), inLines: [], outLines: [], biasLines: [], inBoxes: [], outBoxes: [], nodes: [] };
  const sel = L.pixel(anim.selected);
  const bundle = [L.gx + 8 * L.cell + 4, L.cy];
  // each weighted line ends (or starts) with a short level stretch beside its hidden unit, where its box sits
  const port = 96;
  const boxOffset = 54;
  for (let k = 0; k < drawn; k++) {
    svg.append(svgEl('line', { class: 'edge', x1: bundle[0], y1: bundle[1], x2: L.hx, y2: L.hy(k), opacity: 0.35 }));
  }
  if (t.bias) {
    const b = [L.gx + 4 * L.cell, L.gy - 30];
    for (let k = 0; k < drawn; k++) parts.biasLines.push(svg.appendChild(svgEl('line', { class: 'wline', x1: b[0], y1: b[1], x2: L.hx, y2: L.hy(k) })));
    svg.append(svgEl('circle', { class: 'node', cx: b[0], cy: b[1], r: 9 }));
    svg.append(svgEl('text', { x: b[0], y: b[1] + 4, 'text-anchor': 'middle', text: '1' }));
    svg.append(svgEl('text', { x: b[0] + 16, y: b[1] + 4, text: 'bias' }));
  }
  for (let k = 0; k < drawn; k++) {
    const y = L.hy(k);
    parts.inLines.push(svg.appendChild(svgEl('polyline', { class: 'wline', fill: 'none',
      points: sel[0] + ',' + sel[1] + ' ' + (L.hx - port) + ',' + y + ' ' + L.hx + ',' + y })));
    parts.outLines.push(svg.appendChild(svgEl('polyline', { class: 'wline', fill: 'none',
      points: L.hx + ',' + y + ' ' + (L.hx + port) + ',' + y + ' ' + L.ox + ',' + L.cy })));
  }
  const cells = drawInputGrid(svg, L, t.master, true);
  const c = cells[anim.selected];
  svg.append(svgEl('rect', { class: 'pix-selected', x: +c.getAttribute('x') - 1.5, y: +c.getAttribute('y') - 1.5, width: L.cell + 1.5, height: L.cell + 1.5, rx: 2 }));
  const box = (x, y) => {
    const g = svgEl('g', { class: 'wbox' });
    const rect = svgEl('rect', { x: x - 25, y: y - 8.5, width: 50, height: 17, rx: 3 });
    const text = svgEl('text', { x, y: y + 4, 'text-anchor': 'middle' });
    g.append(rect, text);
    svg.append(g);
    return { rect, text };
  };
  for (let k = 0; k < drawn; k++) {
    parts.nodes.push(svg.appendChild(svgEl('circle', { class: 'node', cx: L.hx, cy: L.hy(k), r: 8 })));
    parts.inBoxes.push(box(L.hx - boxOffset, L.hy(k)));
    parts.outBoxes.push(box(L.hx + boxOffset, L.hy(k)));
  }
  parts.output = svg.appendChild(svgEl('circle', { class: 'node out', cx: L.ox, cy: L.cy, r: 16 }));
  parts.answer = svg.appendChild(svgEl('text', { class: 'answer', x: L.ox + 24, y: L.cy + 5 }));
  svg.append(svgEl('text', { class: 'hint', x: L.gx + 4 * L.cell, y: L.gy + 8 * L.cell + 38, 'text-anchor': 'middle', text: 'click a pixel to follow its weights' }));
  anim.parts = parts;
  $('anim-play').disabled = !!anim.start;
  $('anim-pos').disabled = !!anim.start;
  $('net-figure-title').textContent = anim.start
    ? 'Inside the network: the ' + letterName(anim.letter) + ' detector before training'
    : 'Inside the network: training the ' + letterName(anim.letter) + ' detector';
}

/** Shows the training at {@code pos} (0 = before the first epoch, 1 = the end), between two recorded frames. */
function applyFrame(pos) {
  if (!anim || !anim.parts) return;
  const t = anim.trace;
  const p = anim.parts;
  const frames = t.frames;
  const x = pos * (frames.length - 1);
  const i = Math.min(frames.length - 2, Math.floor(x));
  const a = frames[Math.max(0, i)];
  const b = frames[Math.min(frames.length - 1, i + 1)];
  const f = frames.length < 2 ? 0 : Math.min(1, x - i);
  const mix = (u, v) => u + (v - u) * f;
  const width = (w, max) => (0.8 + 3.4 * Math.min(1, Math.abs(w) / max)).toFixed(2);
  for (let k = 0; k < p.drawn; k++) {
    const wIn = mix(a.hidden[k * p.cols + anim.selected], b.hidden[k * p.cols + anim.selected]);
    const wOut = mix(a.output[k], b.output[k]);
    const inColor = p.color(wIn / p.maxIn);
    const outColor = p.color(wOut / p.maxOut);
    p.inLines[k].setAttribute('stroke', inColor);
    p.inLines[k].setAttribute('stroke-width', width(wIn, p.maxIn));
    p.outLines[k].setAttribute('stroke', outColor);
    p.outLines[k].setAttribute('stroke-width', width(wOut, p.maxOut));
    p.inBoxes[k].text.textContent = fmtSigned(wIn);
    p.inBoxes[k].rect.setAttribute('stroke', inColor);
    p.outBoxes[k].text.textContent = fmtSigned(wOut);
    p.outBoxes[k].rect.setAttribute('stroke', outColor);
    p.nodes[k].style.fill = 'rgba(204, 153, 51, ' + mix(a.activations[k], b.activations[k]).toFixed(3) + ')';
    if (t.bias) {
      const wb = mix(a.hidden[k * p.cols + N], b.hidden[k * p.cols + N]);
      p.biasLines[k].setAttribute('stroke', p.color(wb / p.maxIn));
      p.biasLines[k].setAttribute('stroke-width', width(wb, p.maxIn));
    }
  }
  const score = mix(a.score, b.score);
  p.output.style.fill = 'rgba(255, 102, 51, ' + score.toFixed(3) + ')';
  p.answer.textContent = score.toFixed(2);
  if (anim.start) {
    $('anim-readout').textContent = 'Starting weights: random between 0 and 0.2 (seed ' + t.seed + ') · answer for '
      + letterName(anim.letter) + ': ' + score.toFixed(2) + ' · run back-propagation to watch them change';
    $('anim-pos').value = 0;
    return;
  }
  const epoch = Math.round(mix(a.epoch, b.epoch));
  const ea = a.error === null ? b.error : a.error;
  const eb = b.error === null ? ea : b.error;
  const error = ea > 0 && eb > 0 ? Math.exp(Math.log(ea) + (Math.log(eb) - Math.log(ea)) * f) : eb;
  $('anim-readout').textContent = 'epoch ' + fmtInt(epoch) + ' of ' + fmtInt(t.epochs)
    + (error === null || error === undefined ? '' : ' · error ' + error.toPrecision(3))
    + ' · answer for ' + letterName(anim.letter) + ': ' + score.toFixed(2);
  $('anim-pos').value = Math.round(pos * 1000);
  anim.epoch = epoch;
  if (state.curveCursor && anim.letter === state.current) state.curveCursor(epoch);
  if (state.replaying === anim.letter && anim.letter === state.current) {
    if (pos >= 1) endReplay();
    else if (state.curveReveal) state.curveReveal(epoch);
  }
}

function playAnim() {
  if (!anim) return;
  if (anim.playing) { pauseAnim(); return; }
  if (anim.pos >= 1) anim.pos = 0;
  anim.playing = true;
  $('anim-play').textContent = 'Pause';
  const from = anim.pos;
  const start = performance.now();
  const step = (now) => {
    if (!anim || !anim.playing) return;
    anim.pos = Math.min(1, from + (now - start) / (ANIM_SECONDS * 1000) * 1);
    applyFrame(anim.pos);
    if (anim.pos < 1) anim.raf = requestAnimationFrame(step);
    else pauseAnim();
  };
  anim.raf = requestAnimationFrame(step);
}

function pauseAnim() {
  if (!anim) return;
  anim.playing = false;
  cancelAnimationFrame(anim.raf);
  $('anim-play').textContent = anim.pos >= 1 ? '▶ Watch it learn again (10 s)' : '▶ Resume';
}

function stopAnim() {
  if (anim) {
    anim.playing = false;
    cancelAnimationFrame(anim.raf);
  }
  $('anim-play').textContent = '▶ Watch it learn (10 s)';
}

// ------------------------------------------------------------------ 2. training

async function train(letter, { quiet = false, trace = 0 } = {}) {
  const s = settings();
  const params = trainParams(letter, s);
  if (trace) params.trace = trace;                 // record the training for the animation of the diagram
  const r = await api('train', params, 'POST');
  r.setKey = setKeyOf(s) + '/' + letter;
  r.settings = s;
  if (state.results[letter]) state.previous[letter] = state.results[letter];
  state.results[letter] = r;
  delete state.scans[letter];                      // the server forgets the old scan when a letter is retrained
  state.trained[letter] = { epochs: r.epochs, finalError: r.finalError, reachedThreshold: r.reachedThreshold, hits: -1 };
  updateTrainedChip();
  if (letter === state.current && !quiet) {
    renderTrainResult();
    renderSet();
    renderScan();
    updateTest();
  }
  return r;
}

async function trainCurrent() {
  const button = $('btn-train');
  const status = $('train-status');
  button.disabled = true;
  endReplay();
  status.replaceChildren(el('span', { class: 'spinner' }), document.createTextNode(' training…'));
  try {
    stopAnim();
    state.replaying = state.current;               // set before train() renders the result, so it starts hidden
    const r = await train(state.current, { trace: ANIM_FRAMES });
    drawNetworkDiagram();
    if (anim && anim.letter === state.replaying) {
      // the training itself takes a second or two; then it is replayed slowly, with the curve drawn as it goes
      // and the figures shown at the end (the visitor asked for it, so this plays even with reduced motion)
      const skip = el('button', { type: 'button', class: 'link', text: 'show the result now' });
      skip.addEventListener('click', () => { if (!anim) return; anim.pos = 1; pauseAnim(); applyFrame(1); });
      status.replaceChildren(document.createTextNode('Trained in ' + fmtSeconds(r.millis) + '. Replaying it slowly… '), skip);
      anim.pos = 0;
      applyFrame(0);
      playAnim();
    } else {
      endReplay();
    }
    refreshMessageSoon();
  } catch (e) {
    endReplay();
    status.textContent = '';
    notice(e.message, true);
  } finally {
    button.disabled = false;
  }
}

function fmtSeconds(millis) {
  return millis < 1000 ? millis + ' ms' : (millis / 1000).toFixed(1) + ' s';
}

/** Shows what the replay after "Run back-propagation" was holding back: the figures, the whole curve, "Done.". */
function endReplay() {
  const letter = state.replaying;
  state.replaying = null;
  $('train-result').classList.remove('replaying');
  if (state.curveReveal) state.curveReveal(null);
  const r = letter && state.results[letter];
  if (!letter) return;
  $('train-status').textContent = r && letter === state.current
    ? (r.reachedThreshold ? 'Done.' : 'Stopped at the epoch limit: the error never got below ' + r.settings.errorThreshold + '.')
    : '';
}

function renderTrainResult() {
  const r = state.results[state.current];
  $('train-result').classList.toggle('hidden', !r);
  $('train-result').classList.toggle('replaying', !!r && state.replaying === state.current);
  if (!r) {
    $('train-status').textContent = state.trained[state.current] ? 'This letter was trained before the page was loaded: run it again to see the details.' : '';
    return;
  }
  if (state.replaying !== state.current) $('train-status').textContent = '';     // the figures below say it all
  const s = r.settings;
  const posOk = r.positiveScores.filter((x) => x >= 0.5).length;
  const negOk = r.negativeScores.filter((x) => x < 0.5).length;
  const total = r.positives + r.negatives;
  const runs = r.attempts > 1 ? (r.reachedThreshold ? ', on run ' + r.attempts + ' (the earlier runs got stuck)' : ' in all ' + r.attempts + ' runs') : '';
  const stats = [
    ['Epochs', fmtInt(r.epochs), (r.reachedThreshold ? 'stopped: error below ' + s.errorThreshold : 'hit the limit of ' + fmtInt(s.maxEpochs)) + runs, r.reachedThreshold ? '' : 'bad'],
    ['Final error', r.finalError < 0.001 ? r.finalError.toExponential(1) : r.finalError.toFixed(4), 'sum over ' + total + ' examples' + (r.fresh ? ', new noise each epoch' : ''), ''],
    ['Examples on the right side', (posOk + negOk) + ' of ' + total, 'of 0.5, after training', posOk + negOk === total ? 'good' : 'bad'],
    ['Training time', fmtSeconds(r.millis), r.structure.join('-') + ' network' + (r.bias ? ', bias node' : ''), ''],
  ];
  $('train-stats').replaceChildren(...stats.map(([label, value, note, cls]) => el('div', { class: 'stat' },
    el('div', { class: 'label', text: label }), el('div', { class: 'value ' + cls, text: value }), el('div', { class: 'note', text: note }))));
  renderCurve();
  renderWeights();
  const wrongPos = r.positives - posOk;
  const wrongNeg = r.negatives - negOk;
  $('fit-summary').textContent = wrongPos + wrongNeg === 0
    ? 'All ' + r.positives + ' positives score above 0.5 (lowest ' + fmtScore(Math.min(...r.positiveScores)) + ') and all ' + r.negatives
      + ' negatives below 0.5 (highest ' + fmtScore(Math.max(...r.negativeScores)) + '). That is the training data: the scan in step 3 shows how it does on frames it has never seen.'
    : wrongPos + ' positives and ' + wrongNeg + ' negatives are on the wrong side of 0.5: see the red rings in step 1.';
}

// ---- error curve (log scale), this run against the previous run of the same letter

function curvePoints(r) {
  const step = r.historyStep;
  const regular = Math.ceil(r.epochs / step);
  return r.errorHistory.map((error, i) => ({ epoch: i < regular ? i * step + 1 : r.epochs, error: Math.max(error, 1e-9) }));
}

function niceStep(range, target) {
  const raw = range / target;
  const mag = Math.pow(10, Math.floor(Math.log10(raw)));
  for (const m of [1, 2, 5, 10]) if (raw <= m * mag) return m * mag;
  return 10 * mag;
}

function decadeLabel(v) {
  return v >= 1 ? fmtInt(v) : v.toFixed(Math.round(-Math.log10(v)));
}

function renderCurve() {
  const r = state.results[state.current];
  const prev = state.previous[state.current];
  const svg = $('curve');
  const width = Math.max(320, svg.getBoundingClientRect().width || 640);
  const height = 240;
  const m = { l: 56, r: 64, t: 14, b: 30 };
  const pts = curvePoints(r);
  const prevPts = prev ? curvePoints(prev) : null;
  const all = prevPts ? pts.concat(prevPts) : pts;
  const threshold = r.settings.errorThreshold;
  const maxEpoch = Math.max(...all.map((p) => p.epoch), 2);
  let lo = Math.log10(Math.min(threshold > 0 ? threshold : Infinity, ...all.map((p) => p.error))) - 0.15;
  let hi = Math.log10(Math.max(...all.map((p) => p.error))) + 0.15;
  if (hi - lo < 1) { const c = (hi + lo) / 2; lo = c - 0.5; hi = c + 0.5; }
  const x = (e) => m.l + ((e - 1) / Math.max(1, maxEpoch - 1)) * (width - m.l - m.r);
  const y = (v) => m.t + (1 - (Math.log10(v) - lo) / (hi - lo)) * (height - m.t - m.b);

  svg.setAttribute('viewBox', '0 0 ' + width + ' ' + height);
  svg.replaceChildren();
  for (let d = Math.ceil(lo); d <= Math.floor(hi); d++) {             // one gridline per power of ten
    const v = Math.pow(10, d);
    svg.append(svgEl('line', { class: 'gridline', x1: m.l, x2: width - m.r, y1: y(v), y2: y(v) }));
    svg.append(svgEl('text', { x: m.l - 8, y: y(v) + 4, 'text-anchor': 'end', text: decadeLabel(v) }));
  }
  const xs = niceStep(maxEpoch, 6);
  for (let e = 0; e <= maxEpoch; e += xs) {
    const xe = x(Math.max(1, e));
    svg.append(svgEl('text', { x: xe, y: height - 10, 'text-anchor': 'middle', text: fmtInt(e) }));
  }
  svg.append(svgEl('line', { class: 'baseline', x1: m.l, x2: width - m.r, y1: height - m.b, y2: height - m.b }));
  svg.append(svgEl('text', { x: width - m.r, y: height - 10, 'text-anchor': 'start', dx: 6, text: 'epoch' }));
  if (threshold > 0 && Math.log10(threshold) >= lo) {
    svg.append(svgEl('line', { class: 'target', x1: m.l, x2: width - m.r, y1: y(threshold), y2: y(threshold) }));
    svg.append(svgEl('text', { class: 'target-label', x: width - m.r + 6, y: y(threshold) + 4, text: 'stop < ' + threshold }));
  }
  const path = (p) => p.map((q, i) => (i ? 'L' : 'M') + x(q.epoch).toFixed(1) + ',' + y(q.error).toFixed(1)).join('');
  if (prevPts) svg.append(svgEl('path', { class: 'curve prev', d: path(prevPts) }));
  // during the replay the curve is drawn up to the epoch on screen (state.curveReveal)
  const clipRect = svgEl('rect', { x: 0, y: 0, width, height });
  const clip = svgEl('clipPath', { id: 'curve-clip' });
  clip.append(clipRect);
  svg.append(clip);
  const line = svgEl('path', { class: 'curve', d: path(pts), 'clip-path': 'url(#curve-clip)' });
  svg.append(line);
  const last = pts[pts.length - 1];
  const end = svg.appendChild(svgEl('circle', { class: 'marker', cx: x(last.epoch), cy: y(last.error), r: 4 }));
  state.curveReveal = (epoch) => {
    clipRect.setAttribute('width', epoch === null ? width : Math.max(0, x(Math.max(1, epoch)) + 1));
    end.classList.toggle('hidden', epoch !== null);
  };

  // legend: a box only when two series are on screen
  const legend = $('curve-legend');
  legend.replaceChildren();
  if (prevPts) {
    legend.append(el('span', { class: 'key' }, el('span', { class: 'line' }), document.createTextNode('this run')));
    legend.append(el('span', { class: 'key' }, el('span', { class: 'line prev' }), document.createTextNode('previous run (' + describeRun(prev, r) + ')')));
  }
  $('curve-title').textContent = 'Error after each epoch, ' + letterName(state.current) + ' detector (logarithmic scale)';

  if (state.replaying === state.current) state.curveReveal(anim && anim.trace === r.trace ? anim.epoch ?? 0 : 0);

  // the epoch shown by the training animation
  const cursor = svgEl('line', { class: 'curve-cursor hidden', y1: m.t, y2: height - m.b });
  svg.append(cursor);
  state.curveCursor = (epoch) => {
    const xe = x(Math.min(maxEpoch, Math.max(1, epoch)));
    cursor.setAttribute('x1', xe);
    cursor.setAttribute('x2', xe);
    cursor.classList.remove('hidden');
  };

  // crosshair + tooltip
  const hair = svgEl('line', { class: 'crosshair hidden', y1: m.t, y2: height - m.b });
  const dot = svgEl('circle', { class: 'marker hidden', r: 4 });
  const hit = svgEl('rect', { x: m.l, y: m.t, width: width - m.l - m.r, height: height - m.t - m.b, fill: 'transparent', tabindex: '0' });
  svg.append(hair, dot, hit);
  const nearest = (list, epoch) => list.reduce((best, p) => (Math.abs(p.epoch - epoch) < Math.abs(best.epoch - epoch) ? p : best), list[0]);
  let focusIndex = pts.length - 1;
  const show = (p, cx, cy) => {
    hair.setAttribute('x1', x(p.epoch));
    hair.setAttribute('x2', x(p.epoch));
    hair.classList.remove('hidden');
    dot.setAttribute('cx', x(p.epoch));
    dot.setAttribute('cy', y(p.error));
    dot.classList.remove('hidden');
    const rows = prevPts
      ? [['this run, epoch ' + fmtInt(p.epoch), ''], ['previous run ' + nearest(prevPts, p.epoch).error.toPrecision(3), 'prev']]
      : ['error after epoch ' + fmtInt(p.epoch)];
    showTip(cx, cy, p.error.toPrecision(3), rows);
  };
  hit.addEventListener('pointermove', (e) => {
    const b = svg.getBoundingClientRect();
    const epoch = 1 + ((e.clientX - b.left) * (width / b.width) - m.l) / (width - m.l - m.r) * (maxEpoch - 1);
    const p = nearest(pts, epoch);
    focusIndex = pts.indexOf(p);
    show(p, e.clientX, e.clientY);
  });
  const leave = () => { hair.classList.add('hidden'); dot.classList.add('hidden'); hideTip(); };
  hit.addEventListener('pointerleave', leave);
  hit.addEventListener('blur', leave);
  hit.addEventListener('keydown', (e) => {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
    focusIndex = Math.max(0, Math.min(pts.length - 1, focusIndex + (e.key === 'ArrowLeft' ? -1 : 1)));
    const b = svg.getBoundingClientRect();
    const p = pts[focusIndex];
    show(p, b.left + x(p.epoch) * (b.width / width), b.top + y(p.error));
    e.preventDefault();
  });

  // the table view: every sampled point
  const rows = pts.map((p) => {
    const tr = el('tr', {}, el('td', { text: fmtInt(p.epoch) }), el('td', { text: p.error.toPrecision(4) }));
    if (prevPts) tr.append(el('td', { text: nearest(prevPts, p.epoch).error.toPrecision(4) }));
    return tr;
  });
  const head = el('tr', {}, el('th', { text: 'Epoch' }), el('th', { text: 'Error' }));
  if (prevPts) head.append(el('th', { text: 'Previous run' }));
  $('curve-table').replaceChildren(el('table', { class: 'data' }, el('thead', {}, head), el('tbody', {}, ...rows)));
}

/** What changed between two runs, for the legend: "hidden 8", "sky 0", ... */
function describeRun(prev, now) {
  const a = prev.settings;
  const b = now.settings;
  const names = { hidden: 'hidden', learningRate: 'rate', momentum: 'momentum', maxEpochs: 'max epochs', errorThreshold: 'stop', bias: 'bias',
    variants: 'copies', noise: 'noise', sky: 'sky', seed: 'seed', fresh: 'new noise' };
  const show = (v) => (typeof v === 'boolean' ? (v ? 'on' : 'off') : v);
  const diffs = Object.keys(names).filter((k) => a[k] !== b[k]).map((k) => names[k] + ' ' + show(a[k]));
  return diffs.length ? diffs.join(', ') : 'same settings';
}

// ---- hidden-unit weights as 8x8 diverging heatmaps

function hexToRgb(hex) {
  const h = hex.replace('#', '');
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16) / 255);
}

function toOklab([r, g, b]) {
  const lin = (c) => (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
  const [R, G, B] = [lin(r), lin(g), lin(b)];
  const l = Math.cbrt(0.4122214708 * R + 0.5363325363 * G + 0.0514459929 * B);
  const m = Math.cbrt(0.2119034982 * R + 0.6806995451 * G + 0.1073969566 * B);
  const s = Math.cbrt(0.0883024619 * R + 0.2817188376 * G + 0.6299787005 * B);
  return [0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s, 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s];
}

function fromOklab([L, a, b]) {
  const l = Math.pow(L + 0.3963377774 * a + 0.2158037573 * b, 3);
  const m = Math.pow(L - 0.1055613458 * a - 0.0638541728 * b, 3);
  const s = Math.pow(L - 0.0894841775 * a - 1.291485548 * b, 3);
  const enc = (c) => Math.round(255 * Math.min(1, Math.max(0, c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055)));
  return 'rgb(' + [4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s, -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s].map(enc).join(',') + ')';
}

/** Diverging scale: -1 -> negative pole (blue), 0 -> dark neutral, +1 -> positive pole (orange), mixed in OKLab. */
function divergingScale() {
  const mid = toOklab(hexToRgb(cssVar('--mid')));
  const pos = toOklab(hexToRgb(cssVar('--pole-pos')));
  const neg = toOklab(hexToRgb(cssVar('--pole-neg')));
  return (t) => {
    const pole = t >= 0 ? pos : neg;
    const k = Math.min(1, Math.abs(t));
    return fromOklab(mid.map((v, i) => v + (pole[i] - v) * k));
  };
}

function renderWeights() {
  const r = state.results[state.current];
  const color = divergingScale();
  const hidden = r.hiddenWeights;
  let maxAbs = 1e-9;
  for (const unit of hidden) for (let i = 0; i < N; i++) maxAbs = Math.max(maxAbs, Math.abs(unit[i]));
  const cell = 8;
  const units = hidden.map((weights, u) => {
    const c = el('canvas', { tabindex: '0', 'aria-label': 'hidden unit ' + (u + 1) });
    sizeCanvas(c, W * cell, H * cell);
    const ctx = c.getContext('2d');
    const k = c.width / W;
    for (let i = 0; i < N; i++) {
      ctx.fillStyle = color(weights[i] / maxAbs);
      ctx.fillRect((i % W) * k, Math.floor(i / W) * k, Math.ceil(k), Math.ceil(k));
    }
    c.addEventListener('pointermove', (e) => {
      const b = c.getBoundingClientRect();
      const col = Math.min(W - 1, Math.floor((e.clientX - b.left) / (b.width / W)));
      const row = Math.min(H - 1, Math.floor((e.clientY - b.top) / (b.height / H)));
      showTip(e.clientX, e.clientY, 'weight ' + fmtSigned(weights[row * W + col]), ['hidden unit ' + (u + 1) + ' · row ' + (row + 1) + ', column ' + (col + 1)]);
    });
    c.addEventListener('pointerleave', hideTip);
    c.addEventListener('click', () => { state.selectedUnit = state.selectedUnit === u ? -1 : u; renderUnitTable(); });
    c.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { state.selectedUnit = u; renderUnitTable(); e.preventDefault(); } });
    const lines = [el('span', { text: 'unit ' + (u + 1) }), el('span', { class: 'out', text: 'out ' + fmtSigned(r.outputWeights[u]) })];
    if (r.bias) lines.push(el('span', { text: 'bias ' + fmtSigned(weights[N]) }));
    return el('div', { class: 'unit' }, c, ...lines);
  });
  $('weights').replaceChildren(...units);
  const stops = Array.from({ length: 11 }, (_, i) => color(-1 + i / 5)).join(',');
  $('weights-legend').replaceChildren(el('span', { text: fmtSigned(-maxAbs) }),
    el('span', { class: 'bar', style: 'background: linear-gradient(90deg,' + stops + ')' }), el('span', { text: fmtSigned(maxAbs) }));
  if (state.selectedUnit >= hidden.length) state.selectedUnit = -1;
  renderUnitTable();
}

function renderUnitTable() {
  const r = state.results[state.current];
  const u = state.selectedUnit;
  if (!r || u < 0) { $('unit-table').replaceChildren(); return; }
  const w = r.hiddenWeights[u];
  const rows = [];
  for (let row = 0; row < H; row++) {
    rows.push(el('tr', {}, el('th', { text: 'row ' + (row + 1) }), ...Array.from({ length: W }, (_, col) => el('td', { text: fmtSigned(w[row * W + col]) }))));
  }
  const head = el('tr', {}, el('th', { text: '' }), ...Array.from({ length: W }, (_, col) => el('th', { text: 'col ' + (col + 1) })));
  $('unit-table').replaceChildren(el('p', { class: 'small ink-2', text: 'Incoming weights of hidden unit ' + (u + 1)
    + (r.bias ? ' (bias weight ' + fmtSigned(w[N]) + ')' : '') + '; weight to the output ' + fmtSigned(r.outputWeights[u]) + '.' }),
  el('div', { class: 'table-scroll' }, el('table', { class: 'data' }, el('thead', {}, head), el('tbody', {}, ...rows))));
}

// ---- test a frame

const updateTest = debounce(async () => {
  const s = settings();
  $('test-thr').style.left = 'calc(' + (s.threshold * 100) + '% - 1px)';
  const letter = state.current;
  if (!state.trained[letter]) {
    $('test-score').textContent = '–';
    $('test-fill').style.width = '0';
    $('test-verdict').textContent = 'Train the ' + letterName(letter) + ' detector first (step 2), then draw here: the score updates as you draw.';
    return;
  }
  const frame = testEditor.get();
  try {
    const r = await api('recognize', { letter, frame });
    if (letter !== state.current) return;
    $('test-score').textContent = r.score.toFixed(3);
    $('test-fill').style.width = (r.score * 100).toFixed(1) + '%';
    $('test-verdict').textContent = r.score >= s.threshold
      ? 'At or above the threshold (' + s.threshold.toFixed(2) + '): the scan would report this frame as ' + letterName(letter) + '.'
      : 'Below the threshold (' + s.threshold.toFixed(2) + '): the scan would skip this frame.';
  } catch (e) {
    $('test-verdict').textContent = e.message;
  }
}, 120);

async function randomVideoFrame() {
  try {
    const r = await api('video', { offset: Math.floor(Math.random() * state.videoSize), limit: 1 });
    testEditor.set(r.frames[0]);
  } catch (e) {
    notice(e.message, true);
  }
}

// ------------------------------------------------------------------ 3. scan

async function scan(letter, { quiet = false } = {}) {
  const r = await api('scan', { letter, threshold: settings().threshold }, 'POST');
  state.scans[letter] = r;
  if (state.trained[letter]) state.trained[letter].hits = r.hits.length;
  if (letter === state.current && !quiet) { renderScan(); renderFilmstrip(); }
  return r;
}

async function scanCurrent() {
  const status = $('scan-status');
  const button = $('btn-scan');
  if (!state.trained[state.current]) { notice('Train the ' + letterName(state.current) + ' detector first (step 2).', true); return; }
  button.disabled = true;
  status.replaceChildren(el('span', { class: 'spinner' }), document.createTextNode(' scanning…'));
  try {
    const r = await scan(state.current, { quiet: true });
    status.textContent = '';
    renderScan();
    if (r.hits.length > 0 && filmPosition() === 0) setFilmPosition(Math.max(0, r.hits[0].index - 5));
    else renderFilmstrip();
    refreshMessageSoon();
  } catch (e) {
    status.textContent = '';
    notice(e.message, true);
  } finally {
    button.disabled = false;
  }
}

const rescanSoon = debounce(() => { if (state.scans[state.current]) scanCurrent(); }, 250);

function rejectedOf(letter) {
  if (!state.rejected[letter]) state.rejected[letter] = new Set();
  return state.rejected[letter];
}

const MAX_HITS_SHOWN = 400;

/** Detections scoring less than this barely passed the threshold: they are the ones to check first. */
function weakBelow() {
  return Math.min(1, settings().threshold + 0.03);
}

function renderScan() {
  const r = state.scans[state.current];
  $('scan-result').classList.toggle('hidden', !r);
  if (!r) {
    $('scan-status').textContent = state.trained[state.current] ? '' : 'Train the ' + letterName(state.current) + ' detector first (step 2).';
    return;
  }
  const rejected = rejectedOf(state.current);
  renderScanSummary();

  const shown = r.hits.slice(0, MAX_HITS_SHOWN).map((h) => {
    const box = el('input', { type: 'checkbox' });
    box.checked = !rejected.has(h.index);
    const card = el('div', { class: 'hit' + (box.checked ? '' : ' rejected') });
    box.addEventListener('change', () => {
      if (box.checked) rejected.delete(h.index); else rejected.add(h.index);
      card.classList.toggle('rejected', !box.checked);
      renderScanSummary();
      renderMessage();
    });
    const goto = el('button', { type: 'button', class: 'link', text: '#' + fmtInt(h.index), title: 'Show this frame in the video below' });
    goto.addEventListener('click', () => { setFilmPosition(Math.max(0, h.index - 5)); $('filmstrip').scrollIntoView({ block: 'nearest', behavior: 'smooth' }); });
    card.append(frameCanvas(h.frame, 5), goto, el('span', { text: 'score ' + fmtScore(h.score) }), el('label', {}, box, document.createTextNode(state.current === '_' ? 'space' : 'is ' + state.current)));
    return card;
  });
  if (r.hits.length > MAX_HITS_SHOWN) shown.push(el('p', { class: 'small muted', text: '… ' + (r.hits.length - MAX_HITS_SHOWN) + ' more. Raise the threshold or improve the detector.' }));
  $('hits').replaceChildren(...shown);
}

/** The line above the candidates and the strip chart: both change when the student unticks a frame. */
function renderScanSummary() {
  const r = state.scans[state.current];
  if (!r) return;
  const rejected = rejectedOf(state.current);
  const kept = r.hits.filter((h) => !rejected.has(h.index)).length;
  $('scan-status').textContent = r.hits.length + (r.hits.length === 1 ? ' candidate' : ' candidates') + ' at threshold ' + r.threshold.toFixed(2)
    + ' (' + r.millis + ' ms for ' + fmtInt(r.frames) + ' frames)' + (kept < r.hits.length ? '; ' + kept + ' kept' : '');
  $('scan-strip-title').textContent = 'Where the ' + letterName(state.current) + ' candidates are in the video';
  drawStrip($('scan-strip'), r.hits.map((h) => ({ index: h.index, score: h.score, letter: state.current, rejected: rejected.has(h.index) })), weakBelow());
}

/** A strip chart of frames along the video: one tick per detection. */
function drawStrip(svg, marks, weak = 0) {
  const width = Math.max(320, svg.getBoundingClientRect().width || 640);
  const height = 58;
  const m = { l: 8, r: 8, t: 8, b: 22 };
  const x = (i) => m.l + (i / state.videoSize) * (width - m.l - m.r);
  svg.setAttribute('viewBox', '0 0 ' + width + ' ' + height);
  svg.replaceChildren();
  svg.append(svgEl('line', { class: 'baseline', x1: m.l, x2: width - m.r, y1: height - m.b, y2: height - m.b }));
  for (let f = 0; f <= state.videoSize; f += 5000) {
    svg.append(svgEl('text', { x: x(f), y: height - 6, 'text-anchor': f === 0 ? 'start' : f === state.videoSize ? 'end' : 'middle', text: fmtInt(f) }));
  }
  for (const mk of marks) {
    const cls = 'tick-mark' + (mk.rejected ? ' rejected' : mk.score < weak ? ' weak' : '');
    svg.append(svgEl('rect', { class: cls, x: x(mk.index) - 1, y: m.t, width: 2, height: height - m.t - m.b }));
  }
  const band = svgEl('rect', { class: 'hover-band hidden', y: m.t - 2, width: 8, height: height - m.t - m.b + 4 });
  svg.insertBefore(band, svg.firstChild);
  const hit = svgEl('rect', { x: 0, y: 0, width, height, fill: 'transparent' });
  svg.append(hit);
  hit.addEventListener('pointermove', (e) => {
    if (marks.length === 0) return;
    const b = svg.getBoundingClientRect();
    const frame = ((e.clientX - b.left) * (width / b.width) - m.l) / (width - m.l - m.r) * state.videoSize;
    const near = marks.reduce((best, mk) => (Math.abs(mk.index - frame) < Math.abs(best.index - frame) ? mk : best), marks[0]);
    band.setAttribute('x', x(near.index) - 4);
    band.classList.remove('hidden');
    showTip(e.clientX, e.clientY, letterName(near.letter) + ' · score ' + fmtScore(near.score), ['frame ' + fmtInt(near.index) + (near.rejected ? ' · rejected by you' : '')]);
  });
  hit.addEventListener('pointerleave', () => { band.classList.add('hidden'); hideTip(); });
}

// ---- the video, 40 frames at a time

const FILM = 40;
const videoCache = new Map();

function filmPosition() {
  return parseInt($('film-pos').value, 10) || 0;
}

function setFilmPosition(p) {
  $('film-pos').value = Math.max(0, Math.min(state.videoSize - FILM, p));
  renderFilmstrip();
}

const renderFilmstrip = debounce(async () => {
  const offset = filmPosition();
  $('film-range').textContent = 'frames ' + fmtInt(offset) + '–' + fmtInt(offset + FILM - 1) + ' of ' + fmtInt(state.videoSize);
  let frames = videoCache.get(offset);
  if (!frames) {
    try {
      frames = (await api('video', { offset, limit: FILM })).frames;
    } catch (e) {
      notice(e.message, true);
      return;
    }
    videoCache.set(offset, frames);
    if (videoCache.size > 50) videoCache.delete(videoCache.keys().next().value);
  }
  if (offset !== filmPosition()) return;
  const scanResult = state.scans[state.current];
  const hits = new Map(scanResult ? scanResult.hits.map((h) => [h.index, h.score]) : []);
  $('filmstrip').replaceChildren(...frames.map((f, k) => {
    const index = offset + k;
    const isHit = hits.has(index);
    const box = el('div', { class: 'film' + (isHit ? ' hit-frame' : '') }, frameCanvas(f, 4), el('span', { text: fmtInt(index) }));
    if (isHit) box.title = 'Candidate for ' + letterName(state.current) + ', score ' + fmtScore(hits.get(index));
    return box;
  }));
}, 60);

function nextCandidate() {
  const r = state.scans[state.current];
  if (!r || r.hits.length === 0) { notice('Scan the video for ' + letterName(state.current) + ' first.'); return; }
  const from = filmPosition() + 6;
  const next = r.hits.find((h) => h.index > from) || r.hits[0];
  setFilmPosition(next.index - 5);
}

// ------------------------------------------------------------------ 4. the message

const refreshMessageSoon = debounce(() => refreshMessage(), 300);

async function refreshMessage() {
  try {
    state.message = await api('message');
    renderMessage();
  } catch (e) {
    notice(e.message, true);
  }
}

function renderMessage() {
  const m = state.message;
  const box = $('message-text');
  if (!m || m.detections.length === 0) {
    box.replaceChildren(el('span', { class: 'placeholder', text: 'No letters found yet. Train and scan some letters, or all of them at once.' }));
    $('message-note').textContent = '';
    drawStrip($('message-strip'), []);
    $('msg-table').replaceChildren();
    return;
  }
  const detections = m.detections.filter((d) => !rejectedOf(d.letter).has(d.index)).sort((a, b) => a.index - b.index);
  const rejectedCount = m.detections.length - detections.length;
  const weakLimit = weakBelow();
  $('legend-clear').textContent = 'score ≥ ' + weakLimit.toFixed(2);
  $('legend-weak').textContent = 'barely passed (below ' + weakLimit.toFixed(2) + ')';
  box.replaceChildren(...detections.map((d) => {
    const span = el('span', { class: 'c' + (d.score < weakLimit ? ' weak' : ''), tabindex: '0', text: d.letter === '_' ? ' ' : d.letter });
    attachTip(span, () => [letterName(d.letter) + ' · score ' + fmtScore(d.score), ['frame ' + fmtInt(d.index), 'click to inspect']]);
    span.addEventListener('click', () => inspect(d));
    span.addEventListener('keydown', (e) => { if (e.key === 'Enter') inspect(d); });
    return span;
  }));
  const weak = detections.filter((d) => d.score < weakLimit).length;
  const parts = [];
  // a letter that is missing everywhere is usually a detector problem, not a message problem: say so first
  const stuck = Object.keys(state.trained).filter((L) => !state.trained[L].reachedThreshold);
  if (stuck.length) parts.push('⚠ the training of ' + stuck.join(' ') + ' did not reach the error threshold, so its letters may be missing: retrain with another seed');
  const total = m.scanned.length + m.missing.length;
  if (m.missing.length && m.missing.length < total) parts.push('⚠ trained or retrained but not scanned yet, so missing from the message: ' + m.missing.join(' '));
  parts.push(detections.length + ' characters from ' + m.scanned.length + ' of ' + total + ' letters');
  if (weak) parts.push(weak + ' shaded: they barely passed the threshold (score below ' + weakLimit.toFixed(2) + '), check them first');
  if (rejectedCount) parts.push(rejectedCount + ' rejected by you');
  $('message-note').textContent = parts.join(' · ') + '.';
  drawStrip($('message-strip'), detections.map((d) => ({ index: d.index, score: d.score, letter: d.letter })), weakLimit);
  const rows = detections.map((d) => el('tr', {}, el('td', { text: fmtInt(d.index) }), el('td', { text: letterName(d.letter) }), el('td', { text: d.score.toFixed(3) })));
  $('msg-table').replaceChildren(el('table', { class: 'data' },
    el('thead', {}, el('tr', {}, el('th', { text: 'Frame' }), el('th', { text: 'Letter' }), el('th', { text: 'Score' }))), el('tbody', {}, ...rows)));
}

async function inspect(d) {
  const box = $('inspect');
  try {
    const r = await api('frames', { indexes: d.index });
    const reject = el('button', { type: 'button', text: 'Reject: this is not ' + (d.letter === '_' ? 'a space' : d.letter) });
    reject.addEventListener('click', () => { rejectedOf(d.letter).add(d.index); box.replaceChildren(); renderMessage(); if (d.letter === state.current) renderScan(); });
    const show = el('button', { type: 'button', text: 'Show in the video' });
    show.addEventListener('click', () => { selectLetter(d.letter, true); setFilmPosition(d.index - 5); $('filmstrip').scrollIntoView({ block: 'center', behavior: 'smooth' }); });
    const close = el('button', { type: 'button', class: 'link', text: 'close' });
    close.addEventListener('click', () => box.replaceChildren());
    box.replaceChildren(el('div', { class: 'inspect' }, frameCanvas(r.frames[0].frame, 7),
      el('div', {}, el('div', { class: 'ink-2', text: 'Frame ' + fmtInt(d.index) + ': ' + letterName(d.letter) + ', score ' + d.score.toFixed(3) }),
        el('div', { class: 'row', style: 'margin-top:8px;gap:8px' }, reject, show, close))));
  } catch (e) {
    notice(e.message, true);
  }
}

async function trainAll() {
  const letters = state.letters.map((l) => l.letter);
  const grid = $('progress');
  const cells = new Map();
  grid.replaceChildren(...letters.map((L) => {
    const state_ = el('span', { class: 'state', text: 'waiting' });
    const c = el('div', { class: 'prog' }, el('span', { class: 'ch', text: L }), state_);
    cells.set(L, c);
    return c;
  }));
  grid.classList.remove('hidden');
  const mark = (L, text, cls) => { const c = cells.get(L); c.className = 'prog ' + (cls || ''); c.querySelector('.state').textContent = text; };
  state.stopAll = false;
  $('btn-all').disabled = true;
  $('btn-train').disabled = true;
  $('btn-stop').classList.remove('hidden');
  const t0 = performance.now();
  let next = 0;
  let done = 0;
  let failed = 0;
  const status = $('all-status');
  const worker = async () => {
    while (!state.stopAll && next < letters.length) {
      const L = letters[next++];
      try {
        mark(L, 'training', 'busy');
        const r = await train(L, { quiet: true });
        mark(L, 'scanning', 'busy');
        const s = await scan(L, { quiet: true });
        mark(L, s.hits.length + (s.hits.length === 1 ? ' hit' : ' hits') + (r.reachedThreshold ? '' : ' ⚠'), r.reachedThreshold ? 'done' : 'fail');
        if (!r.reachedThreshold) failed++;
      } catch (e) {
        mark(L, 'error', 'fail');
        failed++;
        notice(L + ': ' + e.message, true);
      }
      done++;
      status.textContent = done + ' of ' + letters.length + ' letters · ' + ((performance.now() - t0) / 1000).toFixed(1) + ' s';
      refreshMessageSoon();
    }
  };
  const workers = state.lab ? state.lab.poolSize : Math.max(2, Math.min(6, Math.floor((navigator.hardwareConcurrency || 4) / 2)));
  await Promise.all(Array.from({ length: workers }, worker));
  status.textContent = (state.stopAll ? 'Stopped after ' : 'Trained and scanned ') + done + ' letters in ' + ((performance.now() - t0) / 1000).toFixed(1) + ' s'
    + (failed ? '; ⚠ ' + failed + ' did not reach the error threshold' : '') + '.';
  $('btn-all').disabled = false;
  $('btn-train').disabled = false;
  $('btn-stop').classList.add('hidden');
  await refreshMessage();
  renderTrainResult();
  renderSet();
  renderScan();
  renderFilmstrip();
  updateTest();
  drawNetworkDiagram();
}

async function resetAll() {
  try {
    await api('reset', {}, 'POST');
  } catch (e) {
    notice(e.message, true);
    return;
  }
  state.results = {};
  state.previous = {};
  state.scans = {};
  state.trained = {};
  state.rejected = {};
  $('progress').classList.add('hidden');
  $('all-status').textContent = '';
  drawNetworkDiagram();
  $('inspect').replaceChildren();
  updateTrainedChip();
  renderTrainResult();
  renderSet();
  renderScan();
  renderFilmstrip();
  updateTest();
  await refreshMessage();
  notice('All detectors forgotten.');
}

// ------------------------------------------------------------------ visitor counter

const ABACUS = 'https://abacus.jasoncameron.dev/';

/**
 * The old-school counter at the top. On a static host it uses the Abacus counter named in the lfs-abacus
 * meta tag (a free counting service, no account): the first page load of a browser session counts a
 * visit (/hit), later loads in the same session only read the total (/get). Served by the lab's Java
 * server without that tag, it shows the server's own count of page loads.
 */
async function showVisitorCounter(serverAvailable) {
  const counter = (document.querySelector('meta[name="lfs-abacus"]')?.content || '').trim();
  let count = null;
  try {
    if (counter) {
      let counted = false;
      try { counted = sessionStorage.getItem('lfs.counted') === counter; } catch { /* no storage: count this load */ }
      const name = counter.split('/').map(encodeURIComponent).join('/');
      const response = await fetch(ABACUS + (counted ? 'get/' : 'hit/') + name);
      if (response.ok) {
        count = (await response.json()).value;
        try { sessionStorage.setItem('lfs.counted', counter); } catch { /* ignore */ }
      }
    } else if (serverAvailable) {
      count = (await api('visits', {}, 'POST')).count;
    }
  } catch {
    count = null;                                         // the counter is decoration: never an error message
  }
  if (!Number.isFinite(count)) return;
  const digits = String(count).padStart(6, '0');
  $('visit-digits').replaceChildren(...digits.split('').map((d) => el('span', { text: d })));
  $('visit-text').textContent = 'You are visitor number ' + count.toLocaleString('en-US') + '.';
  $('visit-counter').title = counter ? 'Visits counted by Abacus, one per browser session' : 'Page loads since this server started';
  $('visit-counter').classList.remove('hidden');
}

// ------------------------------------------------------------------ start

let drawEditor;
let testEditor;

/**
 * Where the lab runs: in this browser when the page says so (SiteBuilder, which makes the static site for
 * GitHub Pages, adds <meta name="lfs-engine" content="browser">), otherwise on the Java server.
 */
const inBrowser = () => document.querySelector('meta[name="lfs-engine"]')?.content === 'browser';

async function startBrowserLab() {
  if (!inBrowser()) return;
  const chip = $('chip-server');
  chip.lastElementChild.textContent = 'loading the video…';
  const { BrowserLab } = await import('./engine/lab.js');
  state.lab = await BrowserLab.load();
  $('engine-note').classList.remove('hidden');
}

async function init() {
  let status;
  try {
    await startBrowserLab();
    status = await api('status');
  } catch (e) {
    if (inBrowser()) {
      notice('The lab could not start in this browser (' + e.message + ').', true);
      showVisitorCounter(false);
      return;
    }
    notice('The lab needs its small Java server, which is not answering (' + e.message + '). Download the code at the bottom of '
      + 'this page, start run_dashboard.sh or run_dashboard.bat, and open http://localhost:8080/.', true);
    showVisitorCounter(false);
    return;
  }
  showVisitorCounter(true);
  state.defaults = status.defaults;
  state.videoSize = status.videoFrames;
  state.trained = status.trained || {};
  const downloads = status.downloads || {};
  if (!downloads.french) $('dl-french').classList.add('hidden');      // a student copy has no 2001 materials next to it
  if (!downloads.english) $('dl-english').classList.add('hidden');
  if (!downloads.french || !downloads.english) $('dl-sep').classList.add('hidden');
  setServerChip(true);
  $('film-pos').max = state.videoSize - FILM;
  applySettings({ ...status.defaults, ...(recall('settings') || {}) });
  state.letters = (await api('letters')).letters;
  buildLetterGrid();
  updateTrainedChip();

  drawEditor = buildEditor($('draw-editor'), () => {});
  testEditor = buildEditor($('test-editor'), () => updateTest());

  // settings that change the training set reload it; the network settings redraw the diagram
  for (const id of ['f-variants', 'f-noise', 'f-sky', 'f-seed']) $(id).addEventListener('input', () => { saveSettings(); loadSetSoon(); });
  $('f-seed').addEventListener('input', () => drawNetworkDiagram());     // the seed also draws the starting weights
  for (const id of ['f-hidden', 'f-bias', 'f-lr', 'f-momentum', 'f-epochs', 'f-errthr']) $(id).addEventListener('input', () => { saveSettings(); drawNetworkDiagram(); });
  $('f-threshold').addEventListener('input', () => {
    $('threshold-out').textContent = settings().threshold.toFixed(2);
    saveSettings();
    updateTest();
    renderMessage();
    rescanSoon();
  });
  $('btn-defaults-set').addEventListener('click', () => { applySettings(state.defaults); saveSettings(); drawNetworkDiagram(); loadSet(); });
  $('btn-simulate').addEventListener('click', simulateFilling);
  $('btn-add-sample').addEventListener('click', addSample);
  $('btn-clear-samples').addEventListener('click', clearSamples);
  $('btn-draw-master').addEventListener('click', () => drawEditor.set(masterOf(state.current)));
  $('btn-draw-clear').addEventListener('click', () => drawEditor.clear());
  $('btn-train').addEventListener('click', trainCurrent);
  $('btn-test-master').addEventListener('click', () => testEditor.set(masterOf(state.current)));
  $('btn-test-noise').addEventListener('click', () => testEditor.set(addNoise(testEditor.get())));
  $('btn-test-video').addEventListener('click', randomVideoFrame);
  $('btn-test-clear').addEventListener('click', () => testEditor.clear());
  $('btn-scan').addEventListener('click', scanCurrent);
  $('film-pos').addEventListener('input', renderFilmstrip);
  $('btn-film-prev').addEventListener('click', () => setFilmPosition(filmPosition() - FILM));
  $('btn-film-next').addEventListener('click', () => setFilmPosition(filmPosition() + FILM));
  $('btn-film-hit').addEventListener('click', nextCandidate);
  $('btn-all').addEventListener('click', trainAll);
  $('btn-stop').addEventListener('click', () => { state.stopAll = true; });
  $('btn-reset').addEventListener('click', resetAll);
  $('anim-play').addEventListener('click', playAnim);
  $('anim-pos').addEventListener('input', () => {
    if (!anim) return;
    if (anim.playing) pauseAnim();
    anim.pos = $('anim-pos').value / 1000;
    applyFrame(anim.pos);
    pauseAnim();
  });
  for (const [button, table] of [['curve-table-toggle', 'curve-table'], ['msg-table-toggle', 'msg-table']]) {
    $(button).addEventListener('click', () => {
      const hidden = $(table).classList.toggle('hidden');
      $(button).textContent = hidden ? 'Show as a table' : 'Hide the table';
    });
  }
  window.addEventListener('resize', debounce(() => {
    if (state.results[state.current]) renderCurve();
    if (anim && !anim.playing) applyFrame(anim.pos);
    renderScan();
    renderMessage();
  }, 150));

  const known = (letter) => state.letters.some((l) => l.letter === letter);
  const remembered = recall('chosenLetter');
  selectLetter(known(remembered) ? remembered : known(DEFAULT_LETTER) ? DEFAULT_LETTER : state.letters[0].letter);
  testEditor.set(masterOf(state.current));
  await refreshMessage();
}

function saveSettings() {
  store('settings', settings());
}

init();
