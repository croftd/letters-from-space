// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
// Original: edu.esi.reseauxneuronaux.VecteurESDouble by David Croft, February 2001.
package lettersfromspace.neuralnet;

import java.util.ArrayList;
import java.util.List;

/**
 * Convenience {@link IOVector} backed by a growable list of doubles. Used mostly for target
 * vectors, for example {@code DoubleIOVector.of(1.0)} for a positive example of a one-output
 * network. (The 2001 class extended {@code Vector}; with generics that would clash with
 * {@code List.get}, so the list is now a field.)
 */
public class DoubleIOVector implements IOVector {

    private final List<Double> values;

    public DoubleIOVector() {
        values = new ArrayList<>();
    }

    public DoubleIOVector(int initialCapacity) {
        values = new ArrayList<>(initialCapacity);
    }

    /** Builds a vector from the given values. */
    public static DoubleIOVector of(double... values) {
        DoubleIOVector v = new DoubleIOVector(values.length);
        for (double d : values) {
            v.add(d);
        }
        return v;
    }

    /** Appends a value. */
    public void add(double value) {
        values.add(value);
    }

    @Override
    public int length() {
        return values.size();
    }

    @Override
    public double get(int index) {
        return values.get(index);
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
