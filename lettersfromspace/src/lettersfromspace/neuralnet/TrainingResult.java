// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
package lettersfromspace.neuralnet;

/**
 * Outcome of {@link MultiLayerNetwork#train}: how many epochs ran, the final epoch error,
 * the error after every epoch (useful for plotting a learning curve) and whether training
 * stopped because the error fell below the threshold (as opposed to running out of epochs).
 */
public record TrainingResult(int epochs, double finalError, double[] errorHistory, boolean reachedThreshold) {
}
