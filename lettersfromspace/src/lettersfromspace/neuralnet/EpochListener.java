// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
package lettersfromspace.neuralnet;

/** Callback invoked after every training epoch, e.g. to update a progress display. */
@FunctionalInterface
public interface EpochListener {
    void onEpoch(int epoch, double error);
}
