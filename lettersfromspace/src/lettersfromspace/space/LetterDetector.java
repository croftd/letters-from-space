// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
package lettersfromspace.space;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import lettersfromspace.neuralnet.DoubleIOVector;
import lettersfromspace.neuralnet.EpochListener;
import lettersfromspace.neuralnet.IOVector;
import lettersfromspace.neuralnet.MultiLayerNetwork;
import lettersfromspace.neuralnet.TrainingResult;
import lettersfromspace.util.Check;

/**
 * A network trained to answer one question about a frame: "is this the letter X?".
 * <p>
 * This is the heart of the 2001 practical: one network per letter, with a single output unit
 * that should produce 1.0 for the letter and 0.0 for everything else. The detector then sweeps
 * through a whole video and reports the frames whose output reaches a threshold.
 * <p>
 * Training can get stuck: the 2001 network starts from small positive weights and sometimes
 * settles where every output is the same and the error never moves again. When a run ends without
 * reaching the error threshold, the detector starts again from new random weights, up to
 * {@link Config#restarts()} times, and keeps the best run.
 */
public class LetterDetector {

    /** Scan threshold used by the dashboard and the self-test (the 2001 solution used 0.8, with a smaller training set). */
    public static final double DEFAULT_THRESHOLD = 0.85;

    /** Added to the weight seed for every restart, so each try starts from different weights. */
    private static final long RESTART_SEED_STEP = 1_000_003L;

    /**
     * Training settings. {@link #defaults()} is the 2001 network (64-16-1, no bias, learning rate
     * 0.2, momentum 0.9, stop below an epoch error of 0.01) with up to 2 restarts.
     */
    public record Config(int hiddenUnits, double learningRate, double momentum, int maxEpochs,
                         double errorThreshold, boolean biasNode, long seed, int restarts) {

        public static Config defaults() {
            return new Config(Frame.HEIGHT * Frame.WIDTH / 4, 0.2, 0.9, 10_000, 0.01, false, 1L, 2);
        }

        public Config withSeed(long newSeed) {
            return new Config(hiddenUnits, learningRate, momentum, maxEpochs, errorThreshold, biasNode, newSeed, restarts);
        }

        public Config withRestarts(int newRestarts) {
            return new Config(hiddenUnits, learningRate, momentum, maxEpochs, errorThreshold, biasNode, seed, newRestarts);
        }
    }

    /** A frame whose detector output is at or above the threshold. */
    public record Hit(int frameIndex, double score) {}

    /**
     * Watches a training: called with the network of run {@code run} (1 for the first, more after
     * restarts) before the first epoch ({@code epoch} 0, error NaN) and after every epoch. The dashboard
     * uses it to record the weights for its training animation; it must not change the network.
     */
    @FunctionalInterface
    public interface TrainingObserver {
        void onEpoch(int run, int epoch, double error, MultiLayerNetwork network);
    }

    private final char letter;
    private final MultiLayerNetwork network;
    private final TrainingResult trainingResult;
    private final int positiveCount;
    private final int negativeCount;
    private final Config config;
    private final int attempts;
    private final int keptRun;

    private LetterDetector(char letter, MultiLayerNetwork network, TrainingResult result,
                           int positives, int negatives, Config config, int attempts, int keptRun) {
        this.letter = letter;
        this.network = network;
        this.trainingResult = result;
        this.positiveCount = positives;
        this.negativeCount = negatives;
        this.config = config;
        this.attempts = attempts;
        this.keptRun = keptRun;
    }

    /** Trains a detector for {@code letter} on fixed lists of positive and negative frames. */
    public static LetterDetector train(char letter, List<Frame> positives, List<Frame> negatives, Config config) {
        return train(letter, positives, negatives, config, null);
    }

    public static LetterDetector train(char letter, List<Frame> positives, List<Frame> negatives,
                                       Config config, EpochListener listener) {
        return train(letter, positives, negatives, config, listener, null);
    }

    public static LetterDetector train(char letter, List<Frame> positives, List<Frame> negatives,
                                       Config config, EpochListener listener, TrainingObserver observer) {
        Check.require(positives != null && !positives.isEmpty(), "At least one positive sample is needed");
        Check.require(negatives != null && !negatives.isEmpty(), "At least one negative sample is needed");
        List<Frame> inputs = new ArrayList<>(positives);
        inputs.addAll(negatives);
        List<IOVector> targets = targets(positives.size(), negatives.size());
        return withRestarts(letter, config, positives.size(), negatives.size(), listener, observer,
                (network, perEpoch) -> network.train(inputs, targets, config.maxEpochs(), perEpoch));
    }

    /**
     * Trains a detector for {@code letter} from samples grouped by letter, building its examples with
     * {@link TrainingSets#build}. With {@link TrainingSets.Options#freshNoise()} every epoch gets new
     * noisy copies and new empty sky; otherwise one set is drawn and used for every epoch. The first
     * epoch's examples are those {@code TrainingSets.build(letter, samplesByLetter, options, new
     * Random(config.seed()))} returns, and every restart sees the same examples again.
     */
    public static LetterDetector train(char letter, Map<Character, List<Frame>> samplesByLetter,
                                       TrainingSets.Options options, Config config, EpochListener listener) {
        return train(letter, samplesByLetter, options, config, listener, null);
    }

    public static LetterDetector train(char letter, Map<Character, List<Frame>> samplesByLetter,
                                       TrainingSets.Options options, Config config, EpochListener listener,
                                       TrainingObserver observer) {
        TrainingSets.Sets first = TrainingSets.build(letter, samplesByLetter, options, new Random(config.seed()));
        if (!options.freshNoise()) {
            return train(letter, first.positives(), first.negatives(), config, listener, observer);
        }
        return withRestarts(letter, config, first.positives().size(), first.negatives().size(), listener, observer, (network, perEpoch) -> {
            Random data = new Random(config.seed());
            double[] history = new double[config.maxEpochs()];
            for (int epoch = 0; epoch < config.maxEpochs(); epoch++) {
                TrainingSets.Sets sets = TrainingSets.build(letter, samplesByLetter, options, data);
                List<Frame> inputs = new ArrayList<>(sets.positives());
                inputs.addAll(sets.negatives());
                TrainingResult one = network.train(inputs, targets(sets.positives().size(), sets.negatives().size()), 1);
                history[epoch] = one.finalError();
                perEpoch.onEpoch(epoch, one.finalError());
                if (one.reachedThreshold()) {
                    return new TrainingResult(epoch + 1, one.finalError(), java.util.Arrays.copyOf(history, epoch + 1), true);
                }
            }
            return new TrainingResult(config.maxEpochs(), history[config.maxEpochs() - 1], history, false);
        });
    }

    private interface Run {
        /** Trains {@code network}, calling {@code perEpoch} after every epoch. */
        TrainingResult on(MultiLayerNetwork network, EpochListener perEpoch);
    }

    /** Runs the training on a new network; while it misses the error threshold, restarts from new weights. Keeps the best run. */
    private static LetterDetector withRestarts(char letter, Config config, int positives, int negatives,
                                               EpochListener listener, TrainingObserver observer, Run run) {
        MultiLayerNetwork bestNetwork = null;
        TrainingResult bestResult = null;
        int bestRun = 0;
        int runs = 0;
        for (int attempt = 0; attempt <= Math.max(0, config.restarts()); attempt++) {
            MultiLayerNetwork network = new MultiLayerNetwork(new int[] {Frame.HEIGHT * Frame.WIDTH, config.hiddenUnits(), 1},
                    config.biasNode(), config.seed() + attempt * RESTART_SEED_STEP)
                    .setLearningRate(config.learningRate())
                    .setMomentum(config.momentum())
                    .setErrorThreshold(config.errorThreshold());
            final int runNumber = attempt + 1;
            if (observer != null) {
                observer.onEpoch(runNumber, 0, Double.NaN, network);
            }
            EpochListener perEpoch = (epoch, error) -> {
                if (listener != null) {
                    listener.onEpoch(epoch, error);
                }
                if (observer != null) {
                    observer.onEpoch(runNumber, epoch + 1, error, network);
                }
            };
            TrainingResult result = run.on(network, perEpoch);
            runs++;
            if (bestResult == null || result.finalError() < bestResult.finalError()) {
                bestNetwork = network;
                bestResult = result;
                bestRun = runNumber;
            }
            if (result.reachedThreshold()) {
                break;
            }
        }
        return new LetterDetector(letter, bestNetwork, bestResult, positives, negatives, config, runs, bestRun);
    }

    /** Targets in the order of the inputs: 1.0 for the positives, then 0.0 for the negatives. */
    private static List<IOVector> targets(int positives, int negatives) {
        List<IOVector> targets = new ArrayList<>(positives + negatives);
        for (int i = 0; i < positives; i++) {
            targets.add(DoubleIOVector.of(1.0));
        }
        for (int i = 0; i < negatives; i++) {
            targets.add(DoubleIOVector.of(0.0));
        }
        return targets;
    }

    /** The network's belief, between 0.0 and 1.0, that {@code frame} shows the letter. */
    public double score(Frame frame) {
        return network.recognize(frame).get(0);
    }

    /** Sweeps the whole video and returns the frames scoring at or above {@code threshold}, in frame order. */
    public List<Hit> scan(Video video, double threshold) {
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < video.size(); i++) {
            double s = score(video.getFrame(i));
            if (s >= threshold) {
                hits.add(new Hit(i, s));
            }
        }
        return hits;
    }

    public char getLetter() { return letter; }
    public MultiLayerNetwork getNetwork() { return network; }
    /** The training of the run that was kept (the last one when a restart reached the threshold). */
    public TrainingResult getTrainingResult() { return trainingResult; }
    /** Positive examples per epoch. */
    public int getPositiveCount() { return positiveCount; }
    /** Negative examples per epoch. */
    public int getNegativeCount() { return negativeCount; }
    public Config getConfig() { return config; }
    /** How many runs were made: 1 when the first run reached the error threshold, 1 + restarts when none did. */
    public int getAttempts() { return attempts; }
    /** Which run was kept (1-based): the one that reached the threshold, or the one with the lowest error. */
    public int getKeptRun() { return keptRun; }
}
