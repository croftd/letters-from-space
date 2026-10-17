// Letters from Space - the lab's Java code translated to JavaScript, so the network can train in a web
// browser (on GitHub Pages there is no server). Each part follows its Java class line by line, in the same
// order of operations, and java.util.Random is reproduced exactly: a seed gives the same training sets,
// noise and starting weights as the Java version. Java is the reference; see src/lettersfromspace/.

export const W = 8;
export const H = 8;
export const N = W * H;

// ------------------------------------------------------------------ java.util.Random

/**
 * java.util.Random: the 48-bit linear congruential generator, held as two 24-bit halves so that every
 * step stays exact in JavaScript numbers.
 */
export class JavaRandom {
  constructor(seed) {
    const s = (BigInt(seed) ^ 0x5DEECE66Dn) & ((1n << 48n) - 1n);
    this.hi = Number(s >> 24n);
    this.lo = Number(s & 0xFFFFFFn);
  }

  /** seed = (seed * 0x5DEECE66D + 0xB) mod 2^48; returns (int) (seed >>> (48 - bits)). */
  next(bits) {
    const p0 = this.lo * 0xECE66D + 0xB;
    const carry = Math.floor(p0 / 0x1000000);
    const lo = p0 - carry * 0x1000000;
    const hi = (this.hi * 0xECE66D + this.lo * 0x5DE + carry) % 0x1000000;
    this.hi = hi;
    this.lo = lo;
    return Math.floor((hi * 0x1000000 + lo) / 2 ** (48 - bits)) | 0;
  }

  nextInt(bound) {
    if (!(bound > 0)) throw new Error('bound must be positive');
    let r = this.next(31);
    const m = bound - 1;
    if ((bound & m) === 0) return Math.floor((bound * r) / 2147483648);   // power of two
    for (let u = r; ((u - (r = u % bound) + m) | 0) < 0; u = this.next(31));
    return r;
  }

  nextDouble() {
    const high = this.next(26);
    return (high * 134217728 + this.next(27)) / 9007199254740992;          // * 2^-53
  }
}

// ------------------------------------------------------------------ neuralnet: Neuron, Synapse, Layer, MultiLayerNetwork

class Neuron {
  constructor(label) {
    this.output = 0;
    this.sum = 0;
    this.delta = 0;
    this.incoming = [];
    this.outgoing = [];
    this.label = label;
  }

  computeOutput() {
    const incoming = this.incoming;
    let sum = 0;
    for (let i = 0; i < incoming.length; i++) sum += incoming[i].from.output * incoming[i].weight;
    this.sum = sum;
    this.output = 1 / (1 + Math.exp(-sum));
  }

  computeOutputDelta(target) {
    this.delta = (target - this.output) * this.output * (1 - this.output);
  }

  computeHiddenDelta() {
    const outgoing = this.outgoing;
    let errorSum = 0;
    for (let i = 0; i < outgoing.length; i++) errorSum += outgoing[i].to.delta * outgoing[i].weight;
    this.delta = this.output * (1 - this.output) * errorSum;
  }

  updateWeights(learningRate, momentum) {
    const incoming = this.incoming;
    const delta = this.delta;
    for (let i = 0; i < incoming.length; i++) {
      const s = incoming[i];
      s.lastChange = learningRate * delta * s.from.output + momentum * s.lastChange;
      s.weight += s.lastChange;
    }
  }
}

class Synapse {
  /** Weight in [0, 0.2), as in 2001; registers itself with both neurons. */
  constructor(from, to, random) {
    this.from = from;
    this.to = to;
    this.weight = random.nextDouble() / 5;
    this.lastChange = 0;
    from.outgoing.push(this);
    to.incoming.push(this);
  }
}

class Layer {
  constructor(label, size) {
    this.label = label;
    this.size = size;
    this.neurons = [];
    for (let i = 0; i < size; i++) this.neurons.push(new Neuron(label + '_N' + i));
  }
}

/** A multi-layer network trained by back-propagation, exactly like MultiLayerNetwork.java. */
export class MultiLayerNetwork {
  constructor(unitsPerLayer, biasNode, seed) {
    if (unitsPerLayer.length < 2) throw new Error('A multi-layer network needs 2 or more layers');
    this.unitsPerLayer = unitsPerLayer.slice();
    this.biasNode = biasNode;
    this.random = new JavaRandom(seed);
    this.inputSize = unitsPerLayer[0];
    this.learningRate = 0.2;
    this.momentum = 0.9;
    this.errorThreshold = 0.01;
    this.error = 0;
    this.layers = [];
    this.inputLayer = new Layer('Input', this.inputSize + (biasNode ? 1 : 0));
    this.layers.push(this.inputLayer);
    const hiddenLayers = unitsPerLayer.length - 2;
    for (let i = 0; i < hiddenLayers; i++) this.layers.push(new Layer('Hidden_' + i, unitsPerLayer[i + 1]));
    this.outputLayer = new Layer('Output', unitsPerLayer[unitsPerLayer.length - 1]);
    this.layers.push(this.outputLayer);
    if (biasNode) {                                       // the bias node feeds every hidden unit
      const bias = this.inputLayer.neurons[this.inputLayer.size - 1];
      for (let layer = 1; layer <= hiddenLayers; layer++) {
        for (let unit = 0; unit < unitsPerLayer[layer]; unit++) new Synapse(bias, this.layers[layer].neurons[unit], this.random);
      }
    }
    for (let layer = 1; layer < unitsPerLayer.length; layer++) {
      for (let source = 0; source < unitsPerLayer[layer - 1]; source++) {
        for (let dest = 0; dest < unitsPerLayer[layer]; dest++) {
          new Synapse(this.layers[layer - 1].neurons[source], this.layers[layer].neurons[dest], this.random);
        }
      }
    }
  }

  setLearningRate(v) { this.learningRate = v; return this; }
  setMomentum(v) { this.momentum = v; return this; }
  setErrorThreshold(v) { this.errorThreshold = v; return this; }

  /** Inputs are arrays of numbers (a frame's pixels); targets are arrays too. */
  train(inputs, targets, maxEpochs, listener) {
    if (!inputs.length || inputs.length !== targets.length) throw new Error('There must be the same number of input vectors as target vectors');
    const history = new Float64Array(maxEpochs);
    let epochs = 0;
    let reached = false;
    for (let epoch = 0; epoch < maxEpochs; epoch++) {
      this.learnOneEpoch(inputs, targets);
      history[epoch] = this.error;
      epochs = epoch + 1;
      if (listener) listener(epoch, this.error);
      if (this.error < this.errorThreshold) {
        reached = true;
        break;
      }
    }
    return { epochs, finalError: this.error, errorHistory: history.slice(0, epochs), reachedThreshold: reached };
  }

  learnOneEpoch(inputs, targets) {
    this.error = 0;
    for (let i = 0; i < inputs.length; i++) {
      this.setInputs(inputs[i]);
      this.propagate();
      this.backPropagate(targets[i]);
      this.error += this.computeError(targets[i]);
    }
  }

  recognize(input) {
    this.setInputs(input);
    this.propagate();
    return this.outputLayer.neurons.map((n) => n.output);
  }

  setInputs(input) {
    const units = this.inputLayer.neurons;
    for (let i = 0; i < input.length; i++) units[i].output = input[i];
    if (this.biasNode) units[units.length - 1].output = 1;
  }

  propagate() {
    for (let i = 1; i < this.layers.length; i++) {
      const neurons = this.layers[i].neurons;
      for (let k = 0; k < neurons.length; k++) neurons[k].computeOutput();
    }
  }

  computeError(target) {
    let sum = 0;
    for (let i = 0; i < target.length; i++) {
      const diff = target[i] - this.outputLayer.neurons[i].output;
      sum += diff * diff;
    }
    return sum / 2;
  }

  backPropagate(target) {
    const out = this.outputLayer.neurons;
    for (let k = 0; k < out.length; k++) out[k].computeOutputDelta(target[k]);
    for (let i = this.layers.length - 2; i >= 1; i--) {
      const neurons = this.layers[i].neurons;
      for (let k = 0; k < neurons.length; k++) neurons[k].computeHiddenDelta();
    }
    for (let k = 0; k < out.length; k++) out[k].updateWeights(this.learningRate, this.momentum);
    for (let i = this.layers.length - 2; i >= 1; i--) {
      const neurons = this.layers[i].neurons;
      for (let k = 0; k < neurons.length; k++) neurons[k].updateWeights(this.learningRate, this.momentum);
    }
  }

  /** Incoming weights of every unit of {@code layer}, ordered like the previous layer (bias last). */
  getWeights(layer) {
    const previous = this.layers[layer - 1].neurons;
    const index = new Map(previous.map((n, i) => [n, i]));
    return this.layers[layer].neurons.map((n) => {
      const w = new Array(previous.length).fill(0);
      for (const s of n.incoming) w[index.get(s.from)] = s.weight;
      return w;
    });
  }

  /** Sets the incoming weights of {@code layer} from a matrix like the one getWeights returns. */
  setWeights(layer, matrix) {
    const previous = this.layers[layer - 1].neurons;
    const index = new Map(previous.map((n, i) => [n, i]));
    this.layers[layer].neurons.forEach((n, u) => { for (const s of n.incoming) s.weight = matrix[u][index.get(s.from)]; });
  }

  getOutputs(layer) {
    return this.layers[layer].neurons.slice(0, this.unitsPerLayer[layer]).map((n) => n.output);
  }
}

// ------------------------------------------------------------------ space: frames, files, noise, training sets

/** A frame: 64 pixels (0 or 1) and the letter it shows ('' when unknown). */
export function frameFromCompact(s, letter = '') {
  const pixels = new Uint8Array(N);
  for (let i = 0; i < N; i++) pixels[i] = s[i] === 'O' ? 1 : 0;
  return { pixels, letter };
}

export function toCompact(frame) {
  let s = '';
  for (let i = 0; i < N; i++) s += frame.pixels[i] ? 'O' : '.';
  return s;
}

function samePixels(a, b) {
  for (let i = 0; i < N; i++) if (a.pixels[i] !== b.pixels[i]) return false;
  return true;
}

/** FrameLoader.read: frames of 8 rows of 'O'/'.', '#' comments, LETTER=X (or LETTRE=X) tags. */
export function readFrames(text) {
  const lines = text.split(/\r?\n/);
  const frames = [];
  let letter = '';
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line === '' || line[0] === '#') continue;
    if (line.startsWith('LETTER=') || line.startsWith('LETTRE=')) {
      letter = line.charAt(7);
      continue;
    }
    if (line[0] === 'O' || line[0] === '.') {
      const rows = lines.slice(i, i + H);
      if (rows.length < H || rows.some((r) => r.length !== W)) throw new Error('bad frame at line ' + (i + 1));
      frames.push(frameFromCompact(rows.join(''), letter));
      i += H - 1;
      continue;
    }
    throw new Error('unexpected line ' + (i + 1) + ": '" + line + "'");
  }
  return frames;
}

export function groupByLetter(frames) {
  const groups = new Map();
  for (const f of frames) {
    if (!groups.has(f.letter)) groups.set(f.letter, []);
    groups.get(f.letter).push(f);
  }
  return groups;
}

/** TrainingSets.loadStudentSamples: the master glyphs, plus the hand-drawn A's (without duplicates). */
export function studentSamples(allLettersText, letterAText) {
  const samples = groupByLetter(readFrames(allLettersText));
  if (letterAText) {
    const as = samples.get('A') || [];
    for (const f of readFrames(letterAText)) {
      f.letter = 'A';
      if (!as.some((e) => samePixels(e, f))) as.push(f);
    }
    samples.set('A', as);
  }
  return samples;
}

/** Noise.flipRandomPixels: 1 to (maxPercent % of 64) flips, one per equal slice of the frame. */
export function flipRandomPixels(pixels, maxPercent, random) {
  const length = N;
  let maxNoisy = Math.trunc(Math.fround(length * Math.fround(maxPercent / 100)));   // Java float arithmetic
  if (maxNoisy === 0) maxNoisy = 1;
  let noisy = random.nextInt(maxNoisy);
  if (noisy === 0) noisy = 1;
  const interval = Math.trunc(length / noisy);
  let start = 0;
  for (let i = 0; i < noisy; i++) {
    const position = start + random.nextInt(interval);
    pixels[position] = pixels[position] === 1 ? 0 : 1;
    start += interval;
  }
}

export function noisyCopy(frame, maxPercent, random) {
  const copy = { pixels: frame.pixels.slice(), letter: frame.letter };
  flipRandomPixels(copy.pixels, maxPercent, random);
  return copy;
}

export function randomSky(maxPercent, random) {
  const frame = { pixels: new Uint8Array(N), letter: '' };
  flipRandomPixels(frame.pixels, random.nextInt(Math.max(1, maxPercent)), random);
  return frame;
}

export const SKY_MAX_PERCENT = 50;
export const DEFAULT_OPTIONS = { variants: 8, noisePercent: 8, skyFrames: 50, freshNoise: true };

/** TrainingSets.build: samples + noisy copies (round-robin) vs. other letters + empty sky. */
export function buildSets(letter, samplesByLetter, options, random) {
  const samples = samplesByLetter.get(letter);
  if (!samples || !samples.length) throw new Error("There are no samples for the letter '" + letter + "'");
  const positives = samples.slice();
  for (let i = 0; i < options.variants; i++) positives.push(noisyCopy(samples[i % samples.length], options.noisePercent, random));
  const negatives = [];
  for (const [l, list] of samplesByLetter) if (l !== letter) for (const f of list) negatives.push(f);
  for (let i = 0; i < options.skyFrames; i++) negatives.push(randomSky(SKY_MAX_PERCENT, random));
  return { positives, negatives };
}

// ------------------------------------------------------------------ LetterDetector

export const DEFAULT_THRESHOLD = 0.85;
export const DEFAULT_CONFIG = { hiddenUnits: 16, learningRate: 0.2, momentum: 0.9, maxEpochs: 10000, errorThreshold: 0.01, biasNode: false, seed: 1, restarts: 2 };
const RESTART_SEED_STEP = 1000003;

const ONE = [1];
const ZERO = [0];
const targetCache = new Map();

function targetsFor(positives, negatives) {
  const key = positives + '/' + negatives;
  let t = targetCache.get(key);
  if (!t) {
    t = [];
    for (let i = 0; i < positives; i++) t.push(ONE);
    for (let i = 0; i < negatives; i++) t.push(ZERO);
    targetCache.set(key, t);
  }
  return t;
}

/**
 * LetterDetector.train: new noise every epoch (or a fixed set), restarts from new weights when a run misses
 * the error threshold. observer(run, epoch, error, network) is called before the first epoch and after each.
 * Returns {network, result, attempts, keptRun, positives, negatives}.
 */
export function trainDetector(letter, samplesByLetter, options, config, observer) {
  const first = buildSets(letter, samplesByLetter, options, new JavaRandom(config.seed));
  const pixelsOf = (frames) => frames.map((f) => f.pixels);
  let best = null;
  let runs = 0;
  for (let attempt = 0; attempt <= Math.max(0, config.restarts); attempt++) {
    const network = new MultiLayerNetwork([N, config.hiddenUnits, 1], config.biasNode, config.seed + attempt * RESTART_SEED_STEP)
      .setLearningRate(config.learningRate).setMomentum(config.momentum).setErrorThreshold(config.errorThreshold);
    const run = attempt + 1;
    if (observer) observer(run, 0, NaN, network);
    const perEpoch = (epoch, error) => { if (observer) observer(run, epoch + 1, error, network); };
    let result;
    if (!options.freshNoise) {
      result = network.train(pixelsOf(first.positives.concat(first.negatives)), targetsFor(first.positives.length, first.negatives.length), config.maxEpochs, perEpoch);
    } else {
      const data = new JavaRandom(config.seed);
      const history = new Float64Array(config.maxEpochs);
      result = null;
      for (let epoch = 0; epoch < config.maxEpochs; epoch++) {
        const sets = buildSets(letter, samplesByLetter, options, data);
        const one = network.train(pixelsOf(sets.positives.concat(sets.negatives)), targetsFor(sets.positives.length, sets.negatives.length), 1);
        history[epoch] = one.finalError;
        perEpoch(epoch, one.finalError);
        if (one.reachedThreshold) {
          result = { epochs: epoch + 1, finalError: one.finalError, errorHistory: history.slice(0, epoch + 1), reachedThreshold: true };
          break;
        }
      }
      if (!result) result = { epochs: config.maxEpochs, finalError: history[config.maxEpochs - 1], errorHistory: history, reachedThreshold: false };
    }
    runs++;
    if (!best || result.finalError < best.result.finalError) best = { network, result, keptRun: run };
    if (result.reachedThreshold) break;
  }
  return { ...best, attempts: runs, positives: first.positives.length, negatives: first.negatives.length };
}

/** The network's answer (0..1) for a frame. */
export function score(network, frame) {
  return network.recognize(frame.pixels)[0];
}

export function scan(network, video, threshold) {
  const hits = [];
  for (let i = 0; i < video.length; i++) {
    const s = score(network, video[i]);
    if (s >= threshold) hits.push({ index: i, score: s });
  }
  return hits;
}

// ------------------------------------------------------------------ MessageDecoder

/** Merges the hits of several detectors: sorted by frame, the higher score wins a frame. */
export function mergeHits(hitsByLetter) {
  const best = new Map();
  for (const [letter, hits] of hitsByLetter) {
    for (const h of hits) {
      const current = best.get(h.index);
      if (!current || h.score > current.score) best.set(h.index, { index: h.index, letter, score: h.score });
    }
  }
  return [...best.values()].sort((a, b) => a.index - b.index);
}

export function toText(detections) {
  return detections.map((d) => (d.letter === '_' ? ' ' : d.letter)).join('');
}

// ------------------------------------------------------------------ the training trace (DashboardServer.Tracer)

const round3 = (x) => Math.round(x * 1000) / 1000;

/** Records a training for the animation, exactly like the Java Tracer. */
export class Tracer {
  constructor(master, stride) {
    this.master = master;
    this.stride = Math.max(1, stride);
    this.runs = new Map();
    this.lastLogError = new Map();
  }

  observe(run, epoch, error, network) {
    const logError = Number.isNaN(error) ? NaN : Math.log10(Math.max(error, 1e-9));
    const last = this.lastLogError.get(run);
    const moved = !Number.isNaN(logError) && (last === undefined || Number.isNaN(last) || Math.abs(logError - last) > 0.02);
    if (epoch % this.stride === 0 || moved) {
      if (!this.runs.has(run)) this.runs.set(run, []);
      this.runs.get(run).push(this.snapshot(epoch, error, network));
      this.lastLogError.set(run, logError);
    }
  }

  snapshot(epoch, error, network) {
    const hidden = network.getWeights(1);
    const flat = [];
    for (const row of hidden) for (const w of row) flat.push(round3(w));
    const output = network.getWeights(2)[0].map(round3);
    const answer = round3(score(network, this.master));
    const activations = network.getOutputs(1).map(round3);
    return { epoch, error: Number.isNaN(error) ? null : error, score: answer, hidden: flat, output, activations };
  }

  result(trained, frames) {
    const all = (this.runs.get(trained.keptRun) || []).slice();
    const r = trained.result;
    if (!all.length || all[all.length - 1].epoch !== r.epochs) all.push(this.snapshot(r.epochs, r.finalError, trained.network));
    const network = trained.network;
    return {
      hiddenUnits: network.unitsPerLayer[1],
      weightsPerUnit: network.inputSize + (network.biasNode ? 1 : 0),
      bias: network.biasNode,
      master: toCompact(this.master),
      epochs: r.epochs,
      run: trained.keptRun,
      frames: selectFrames(all, frames),
    };
  }
}

function selectFrames(all, frames) {
  const n = all.length;
  if (n <= frames) return all;
  const logError = all.map((s) => {
    let e = s.error;
    if (e === null) e = n > 1 ? all[1].error : 1;
    return Math.log10(Math.max(e, 1e-9));
  });
  const moved = new Float64Array(n);
  for (let i = 1; i < n; i++) moved[i] = moved[i - 1] + Math.abs(logError[i] - logError[i - 1]);
  const lastEpoch = Math.max(1, all[n - 1].epoch);
  const totalMoved = moved[n - 1];
  const progress = (i) => (totalMoved > 0 ? 0.5 * (all[i].epoch / lastEpoch) + 0.5 * (moved[i] / totalMoved) : all[i].epoch / lastEpoch);
  const out = [];
  let i = 0;
  for (let j = 0; j < frames; j++) {
    const target = j / (frames - 1);
    while (i < n - 1 && progress(i) < target) i++;
    if (!out.length || out[out.length - 1] !== all[i]) out.push(all[i]);
  }
  if (out[out.length - 1] !== all[n - 1]) out.push(all[n - 1]);
  return out;
}
