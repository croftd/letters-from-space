// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
// Original: edu.esi.reseauxneuronaux.Synapse, adapted from the EPFL "Neural Java" OCR
// applet (http://diwww.epfl.ch/mantra/tutorial/) and modified by David Croft, February 2001.
package lettersfromspace.neuralnet;

import java.util.Random;

/**
 * A weighted connection from one {@link Neuron} to another.
 * <p>
 * The knowledge of the network lives in the {@code weight} of its synapses. {@code lastChange}
 * remembers the previous weight update so that the <i>momentum</i> term can reuse part of it.
 */
class Synapse {

    double weight;
    double lastChange;
    final Neuron from;
    final Neuron to;

    /**
     * Creates the synapse and registers it with both neurons. The weight starts as a small
     * random number in [0, 0.2), exactly as in the 2001 code. (Many textbooks recommend a
     * symmetric range such as [-0.1, 0.1); try it and compare the training curves.)
     */
    Synapse(Neuron from, Neuron to, Random random) {
        this.from = from;
        this.to = to;
        this.weight = random.nextDouble() / 5.0;
        this.lastChange = 0.0;
        from.outgoing.add(this);
        to.incoming.add(this);
    }

    double getWeight() {
        return weight;
    }
}
