// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
// Original: edu.esi.reseauxneuronaux.VecteurES by David Croft, February 2001.
package lettersfromspace.neuralnet;

/**
 * An input/output vector for a {@link MultiLayerNetwork}: anything that can be read as a
 * fixed-length list of doubles. Inputs are presented to the network through this interface
 * and targets (the desired outputs) are read through it as well.
 * <p>
 * A video {@code Frame} implements it directly, so a frame can be fed to the network without
 * copying its pixels into another structure.
 *
 * @see DoubleIOVector
 * @see lettersfromspace.space.Frame
 */
public interface IOVector {

    /** Number of elements in the vector. */
    int length();

    /** Element at {@code index}, in the range {@code 0 <= index < length()}. */
    double get(int index);
}
