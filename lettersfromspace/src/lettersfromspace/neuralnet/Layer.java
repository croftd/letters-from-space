// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
// Original: edu.esi.reseauxneuronaux.Layer, adapted from the EPFL "Neural Java" OCR
// applet and modified by David Croft, February 2001.
package lettersfromspace.neuralnet;

import java.util.ArrayList;
import java.util.List;

import lettersfromspace.util.Check;

/** A layer of {@link Neuron}s. The network is a list of layers: input, hidden..., output. */
class Layer {

    final List<Neuron> neurons;
    final int size;
    final String label;

    Layer(String label, int size) {
        this.label = label;
        this.size = size;
        this.neurons = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            neurons.add(new Neuron(label + "_N" + i));
        }
    }

    int getSize() {
        return size;
    }

    Neuron getNeuron(int index) {
        Check.require(index >= 0 && index < neurons.size(),
                "Cannot access neuron at index " + index + " - layer " + label + " has " + neurons.size() + " neurons");
        return neurons.get(index);
    }

    void computeOutputs() {
        for (Neuron n : neurons) {
            n.computeOutput();
        }
    }

    /** Error signals for the output layer, from the desired output vector. */
    void computeOutputDeltas(IOVector target) {
        Check.require(target.length() == neurons.size(),
                "Output layer has a different dimension than the target vector we are training with!");
        for (int i = 0; i < target.length(); i++) {
            neurons.get(i).computeOutputDelta(target.get(i));
        }
    }

    /** Error signals for a hidden layer. */
    void computeHiddenDeltas() {
        for (Neuron n : neurons) {
            n.computeHiddenDelta();
        }
    }

    void updateWeights(double learningRate, double momentum) {
        for (Neuron n : neurons) {
            n.updateWeights(learningRate, momentum);
        }
    }

    void print() {
        for (Neuron n : neurons) {
            n.print();
        }
    }

    @Override
    public String toString() {
        return label + " (" + size + " units)";
    }
}
