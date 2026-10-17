// Letters from Space - the lab running entirely in the visitor's browser. It answers the same requests
// as the Java DashboardServer (status, letters, trainingset, train, scan, message...) with the same
// JSON, so the page works unchanged on a static host such as GitHub Pages. Training happens in Web
// Workers, one per processor core; nothing is computed on a server.
import * as core from './core.js';

const MAX_TRACE = 300;

export class BrowserLab {
  /** Loads the letter files and the video next to the page. */
  static async load(base = '') {
    const get = async (path) => {
      const response = await fetch(base + path);
      if (!response.ok) throw new Error('Cannot load ' + path + ' (HTTP ' + response.status + ')');
      return response.text();
    };
    // data/site.json, written by SiteBuilder, lists the downloads it zipped
    const site = await fetch(base + 'data/site.json').then((r) => (r.ok ? r.json() : {}), () => ({}));
    const [allLetters, letterA, video] = await Promise.all([
      get('data/letters/allLetters.dat'), get('data/letters/letterA.dat'), get('data/space_video.txt')]);
    return new BrowserLab(core.studentSamples(allLetters, letterA), core.readFrames(video), site.downloads || {});
  }

  constructor(samplesByLetter, video, downloads = {}) {
    this.samplesByLetter = samplesByLetter;
    this.video = video;
    this.downloads = downloads;
    this.userSamples = new Map();
    this.detectors = new Map();              // letter -> {network, result}
    this.hits = new Map();
    this.poolSize = Math.max(1, Math.min(8, (navigator.hardwareConcurrency || 4) - 1));
    this.workers = [];
    this.queue = [];
    this.nextId = 1;
  }

  // ---------------------------------------------------------------- requests, as in DashboardServer

  async handle(path, p = {}) {
    switch (path) {
      case 'status': return this.status();
      case 'letters': return this.letters();
      case 'trainingset': return this.trainingSet(p);
      case 'video': return this.videoFrames(p);
      case 'frames': return this.frames(p);
      case 'samples': return this.addSample(p);
      case 'samples/clear': return this.clearSamples(p);
      case 'train': return this.train(p);
      case 'recognize': return this.recognize(p);
      case 'scan': return this.scan(p);
      case 'message': return this.message();
      case 'reset': this.detectors.clear(); this.hits.clear(); return { ok: true };
      case 'visits': throw new Error('No visit counter in the browser');
      default: throw new Error('Unknown API path /api/' + path);
    }
  }

  defaults() {
    const c = core.DEFAULT_CONFIG;
    const o = core.DEFAULT_OPTIONS;
    return { hidden: c.hiddenUnits, learningRate: c.learningRate, momentum: c.momentum, maxEpochs: c.maxEpochs,
      errorThreshold: c.errorThreshold, bias: c.biasNode, seed: c.seed, restarts: c.restarts, variants: o.variants,
      noise: o.noisePercent, sky: o.skyFrames, fresh: o.freshNoise, threshold: core.DEFAULT_THRESHOLD };
  }

  status() {
    const trained = {};
    for (const [letter, d] of this.detectors) {
      trained[letter] = { epochs: d.result.epochs, finalError: d.result.finalError, reachedThreshold: d.result.reachedThreshold,
        hits: this.hits.has(letter) ? this.hits.get(letter).length : -1 };
    }
    return { videoFrames: this.video.length, frameHeight: core.H, frameWidth: core.W, letters: [...this.samplesByLetter.keys()],
      trained, defaults: this.defaults(), engine: 'browser', downloads: this.downloads };
  }

  letters() {
    const list = [...this.samplesByLetter].map(([letter, frames]) => ({ letter, samples: frames.map(core.toCompact),
      userSamples: (this.userSamples.get(letter) || []).map(core.toCompact) }));
    return { letters: list, source: 'data/letters' };
  }

  allSamples() {
    const all = new Map();
    for (const [letter, frames] of this.samplesByLetter) all.set(letter, frames.concat(this.userSamples.get(letter) || []));
    return all;
  }

  letter(p) {
    const s = p.letter;
    if (typeof s !== 'string' || s.length !== 1) throw new Error("Parameter 'letter' must be a single character");
    if (!this.samplesByLetter.has(s)) throw new Error("There are no samples for the letter '" + s + "'");
    return s;
  }

  options(p) {
    const d = core.DEFAULT_OPTIONS;
    const o = { variants: int(p, 'variants', d.variants), noisePercent: int(p, 'noise', d.noisePercent),
      skyFrames: int(p, 'sky', d.skyFrames), freshNoise: bool(p, 'fresh', d.freshNoise) };
    if (o.variants < 0 || o.variants > 100) throw new Error('variants must be between 0 and 100');
    if (o.noisePercent < 0 || o.noisePercent > 50) throw new Error('noise must be between 0 and 50 percent');
    if (o.skyFrames < 0 || o.skyFrames > 1000) throw new Error('sky must be between 0 and 1000 frames');
    return o;
  }

  /** The first epoch's examples, as /api/trainingset and the scores of /api/train see them. */
  displaySets(letter, p, seed) {
    return core.buildSets(letter, this.allSamples(), this.options(p), new core.JavaRandom(seed));
  }

  trainingSet(p) {
    const letter = this.letter(p);
    const sets = this.displaySets(letter, p, int(p, 'seed', core.DEFAULT_CONFIG.seed));
    return { letter, positives: sets.positives.map(core.toCompact),
      negatives: sets.negatives.map((f) => ({ letter: f.letter, frame: core.toCompact(f) })) };
  }

  videoFrames(p) {
    const offset = Math.max(0, int(p, 'offset', 0));
    const limit = Math.min(500, Math.max(1, int(p, 'limit', 100)));
    return { size: this.video.length, offset, frames: this.video.slice(offset, offset + limit).map(core.toCompact) };
  }

  frames(p) {
    const indexes = String(p.indexes || '').split(',').filter((s) => s.trim() !== '').map((s) => parseInt(s, 10));
    return { frames: indexes.map((index) => {
      if (!(index >= 0 && index < this.video.length)) throw new Error('Frame index ' + index + ' is not valid for this video (' + this.video.length + ' frames)');
      return { index, frame: core.toCompact(this.video[index]) };
    }) };
  }

  addSample(p) {
    const letter = this.letter(p);
    const compact = String(p.frame || '');
    if (compact.length !== core.N) throw new Error('Expected ' + core.N + ' characters, got ' + compact.length);
    const frame = core.frameFromCompact(compact, letter);
    if (!frame.pixels.some((x) => x)) throw new Error('The sample is empty: draw a few stars first');
    if (!this.userSamples.has(letter)) this.userSamples.set(letter, []);
    this.userSamples.get(letter).push(frame);
    return { letter, userSamples: this.userSamples.get(letter).map(core.toCompact) };
  }

  clearSamples(p) {
    const letter = this.letter(p);
    this.userSamples.delete(letter);
    return { letter, userSamples: [] };
  }

  async train(p) {
    const letter = this.letter(p);
    const d = core.DEFAULT_CONFIG;
    const config = { hiddenUnits: int(p, 'hidden', d.hiddenUnits), learningRate: num(p, 'learningRate', d.learningRate),
      momentum: num(p, 'momentum', d.momentum), maxEpochs: int(p, 'maxEpochs', d.maxEpochs),
      errorThreshold: num(p, 'errorThreshold', d.errorThreshold), biasNode: bool(p, 'bias', d.biasNode),
      seed: int(p, 'seed', d.seed), restarts: int(p, 'restarts', d.restarts) };
    if (config.hiddenUnits < 1 || config.hiddenUnits > 256) throw new Error('hidden units must be between 1 and 256');
    if (config.maxEpochs < 1 || config.maxEpochs > 200000) throw new Error('maxEpochs must be between 1 and 200000');
    if (config.restarts < 0 || config.restarts > 5) throw new Error('restarts must be between 0 and 5');
    const traceFrames = int(p, 'trace', 0);
    if (traceFrames < 0 || traceFrames > MAX_TRACE) throw new Error('trace must be between 0 and ' + MAX_TRACE + ' frames');
    const options = this.options(p);
    const sets = this.displaySets(letter, p, config.seed);
    const samples = [...this.allSamples()].map(([l, frames]) => [l, frames.map(core.toCompact)]);
    const r = await this.runInWorker({ letter, samples, options, config, traceFrames, master: core.toCompact(this.samplesByLetter.get(letter)[0]) });

    // the trained network, rebuilt here for scanning and testing frames
    const network = new core.MultiLayerNetwork([core.N, config.hiddenUnits, 1], config.biasNode, 0);
    network.setWeights(1, r.hidden);
    network.setWeights(2, r.output);
    this.detectors.set(letter, { network, result: r });
    this.hits.delete(letter);

    const history = r.errorHistory;
    const step = Math.max(1, Math.ceil(history.length / 600));
    const curve = [];
    for (let i = 0; i < history.length; i += step) curve.push(history[i]);
    if ((history.length - 1) % step !== 0) curve.push(history[history.length - 1]);
    const result = { letter, structure: [core.N, config.hiddenUnits, 1], bias: config.biasNode, positives: sets.positives.length,
      negatives: sets.negatives.length, fresh: options.freshNoise, attempts: r.attempts, epochs: r.epochs, finalError: r.finalError,
      reachedThreshold: r.reachedThreshold, millis: r.millis, errorHistory: curve, historyStep: step, hiddenWeights: r.hidden,
      outputWeights: r.output[0], positiveScores: sets.positives.map((f) => core.score(network, f)),
      negativeScores: sets.negatives.map((f) => core.score(network, f)) };
    if (r.trace) result.trace = r.trace;
    return result;
  }

  detectorFor(letter) {
    const d = this.detectors.get(letter);
    if (!d) throw new Error("No detector has been trained for the letter '" + letter + "' yet");
    return d;
  }

  recognize(p) {
    const letter = this.letter(p);
    const frame = core.frameFromCompact(String(p.frame || ''), letter);
    return { letter, score: core.score(this.detectorFor(letter).network, frame) };
  }

  scan(p) {
    const letter = this.letter(p);
    const d = this.detectorFor(letter);
    const threshold = num(p, 'threshold', core.DEFAULT_THRESHOLD);
    const start = performance.now();
    const found = core.scan(d.network, this.video, threshold);
    const millis = Math.round(performance.now() - start);
    this.hits.set(letter, found);
    return { letter, threshold, frames: this.video.length, millis,
      hits: found.map((h) => ({ index: h.index, score: h.score, frame: core.toCompact(this.video[h.index]) })) };
  }

  message() {
    const detections = core.mergeHits(this.hits);
    const scanned = [];
    const missing = [];
    for (const letter of this.samplesByLetter.keys()) (this.hits.has(letter) ? scanned : missing).push(letter);
    return { text: core.toText(detections), detections, scanned, missing };
  }

  // ---------------------------------------------------------------- the worker pool

  runInWorker(task) {
    return new Promise((resolve, reject) => {
      this.queue.push({ task: { ...task, id: this.nextId++ }, resolve, reject });
      this.dispatch();
    });
  }

  dispatch() {
    while (this.queue.length) {
      let worker = this.workers.find((w) => !w.busy);
      if (!worker && this.workers.length < this.poolSize) {
        worker = { thread: new Worker(new URL('./worker.js', import.meta.url), { type: 'module' }), busy: false };
        this.workers.push(worker);
      }
      if (!worker) return;
      const job = this.queue.shift();
      worker.busy = true;
      worker.thread.onmessage = (event) => {
        worker.busy = false;
        if (event.data.ok) job.resolve(event.data); else job.reject(new Error(event.data.error));
        this.dispatch();
      };
      worker.thread.onerror = (event) => {
        worker.busy = false;
        job.reject(new Error(event.message || 'The training thread failed'));
        this.dispatch();
      };
      worker.thread.postMessage(job.task);
    }
  }
}

// request parameters arrive as strings or numbers, as from a query string
function int(p, name, def) {
  const v = p[name];
  if (v === undefined || v === null || v === '') return def;
  const n = Number(v);
  if (!Number.isInteger(n)) throw new Error("Parameter '" + name + "' must be a whole number (got '" + v + "')");
  return n;
}

function num(p, name, def) {
  const v = p[name];
  if (v === undefined || v === null || v === '') return def;
  const n = Number(v);
  if (!Number.isFinite(n)) throw new Error("Parameter '" + name + "' must be a number (got '" + v + "')");
  return n;
}

function bool(p, name, def) {
  const v = p[name];
  if (v === undefined || v === null || v === '') return def;
  return v === true || v === 'true';
}
