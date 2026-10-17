// Letters from Space - trains one letter detector in a background thread (a Web Worker), so that the
// page stays responsive and several letters can train at once, one per processor core.
import { frameFromCompact, trainDetector, Tracer } from './core.js';

self.onmessage = (event) => {
  const { id, letter, samples, options, config, traceFrames, master } = event.data;
  try {
    const samplesByLetter = new Map(samples.map(([l, frames]) => [l, frames.map((f) => frameFromCompact(f, l))]));
    const tracer = traceFrames > 0 ? new Tracer(frameFromCompact(master, letter), Math.ceil(config.maxEpochs / 1500)) : null;
    const start = performance.now();
    const trained = trainDetector(letter, samplesByLetter, options, config,
      tracer ? (run, epoch, error, network) => tracer.observe(run, epoch, error, network) : null);
    const millis = Math.round(performance.now() - start);
    self.postMessage({
      id,
      ok: true,
      millis,
      epochs: trained.result.epochs,
      finalError: trained.result.finalError,
      reachedThreshold: trained.result.reachedThreshold,
      errorHistory: trained.result.errorHistory,
      attempts: trained.attempts,
      hidden: trained.network.getWeights(1),
      output: trained.network.getWeights(2),
      trace: tracer ? tracer.result(trained, traceFrames) : null,
    });
  } catch (error) {
    self.postMessage({ id, ok: false, error: String(error && error.message ? error.message : error) });
  }
};
