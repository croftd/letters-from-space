// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
// Original: edu.esi.reseauxneuronaux.Neuron, adapted from the EPFL "Neural Java" OCR
// applet and modified by David Croft, February 2001.
package lettersfromspace.neuralnet;

import java.util.ArrayList;
import java.util.List;

/**
 * One processing unit of the network.
 * <p>
 * Forward pass: {@code sum} is the weighted sum of the outputs of the neurons connected to
 * the incoming synapses, and {@code output = sigmoid(sum)}, a value between 0.0 and 1.0.
 * <p>
 * Backward pass (back-propagation): {@code delta} is the error signal of this neuron. For an
 * output neuron it is computed from the desired value; for a hidden neuron it is collected
 * from the deltas of the neurons it feeds. The incoming weights are then moved in the
 * direction that reduces the error.
 */
class Neuron {

    double output;   // between 0.0 and 1.0
    double sum;
    double delta;
    final List<Synapse> incoming = new ArrayList<>();
    final List<Synapse> outgoing = new ArrayList<>();
    final String label;

    Neuron(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }

    double getOutput() {
        return output;
    }

    double getDelta() {
        return delta;
    }

    /** Forward pass for this neuron. */
    void computeOutput() {
        sum = 0.0;
        for (Synapse s : incoming) {
            sum += s.from.output * s.weight;
        }
        // A sigmoid activation is used rather than the step function of the perceptron:
        // back-propagation needs an activation function that is continuous and differentiable.
        output = 1.0 / (1.0 + Math.exp(-sum));
    }

    /** Error signal for an OUTPUT neuron, given the desired output {@code target}. */
    void computeOutputDelta(double target) {
        delta = (target - output) * output * (1.0 - output);
    }

    /** Error signal for a HIDDEN neuron: the deltas of the next layer, weighted by the synapses. */
    void computeHiddenDelta() {
        double errorSum = 0.0;
        for (Synapse s : outgoing) {
            errorSum += s.to.delta * s.weight;
        }
        delta = output * (1.0 - output) * errorSum;
    }

    /**
     * Gradient-descent step on the incoming weights:
     * change = learningRate * delta * input + momentum * previousChange.
     */
    void updateWeights(double learningRate, double momentum) {
        for (Synapse s : incoming) {
            s.lastChange = learningRate * delta * s.from.output + momentum * s.lastChange;
            s.weight += s.lastChange;
        }
    }

    /** Prints this neuron's output and its outgoing weights (debugging aid). */
    void print() {
        StringBuilder sb = new StringBuilder(label).append('=').append(output).append(": ");
        for (Synapse s : outgoing) {
            sb.append(s.to.label).append('(').append(s.weight).append(") ");
        }
        System.out.println(sb);
    }
}
