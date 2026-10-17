// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
// Original: edu.esi.reseauxneuronaux.ReseauMulticouche, adapted from the EPFL "Neural Java"
// OCR applet (a class originally called 'Perceptron') and modified by David Croft, February 2001.
package lettersfromspace.neuralnet;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import lettersfromspace.util.Check;

/**
 * A multi-layer neural network trained with back-propagation.
 * <p>
 * You decide the whole structure: how many layers, and how many units in the input, hidden
 * and output layers. Inputs and desired outputs are presented through the {@link IOVector}
 * interface. For example:
 * <pre>
 * int[] unitsPerLayer = { 16, 4, 2 };            // 16 inputs, 4 hidden units, 2 outputs
 * MultiLayerNetwork network = new MultiLayerNetwork(unitsPerLayer, false);
 * </pre>
 * The optional <b>bias node</b> is an extra input unit whose output is always 1.0, connected to
 * every hidden unit. It lets a hidden unit shift its threshold. Sometimes it helps training,
 * sometimes it makes things more complicated: experiment with it.
 * <p>
 * Differences from the 2001 class: the layer count is no longer passed separately (it is the
 * length of {@code unitsPerLayer}); the learning rate and momentum are per-network settings
 * instead of static fields; a {@link Random} seed can be given for reproducible experiments;
 * {@link #train} returns a {@link TrainingResult} with the learning curve instead of only
 * printing it.
 *
 * @see IOVector
 */
public class MultiLayerNetwork {

    private final List<Layer> layers = new ArrayList<>();
    private final Layer inputLayer;
    private final Layer outputLayer;
    private final boolean biasNode;
    private final int inputSize;
    private final int[] unitsPerLayer;
    private final Random random;

    private double learningRate = 0.2;
    private double momentum = 0.9;
    private double errorThreshold = 0.01;
    private boolean verbose = false;
    private double error = 0.0;

    private final NumberFormat percentFormat = NumberFormat.getPercentInstance();

    /** Creates a network with the given units per layer (first = input, last = output) and random weights. */
    public MultiLayerNetwork(int[] unitsPerLayer, boolean addBiasNode) {
        this(unitsPerLayer, addBiasNode, new Random());
    }

    /** Same, with a seed so that two runs start from identical weights. */
    public MultiLayerNetwork(int[] unitsPerLayer, boolean addBiasNode, long seed) {
        this(unitsPerLayer, addBiasNode, new Random(seed));
    }

    public MultiLayerNetwork(int[] unitsPerLayer, boolean addBiasNode, Random random) {
        Check.require(unitsPerLayer != null && unitsPerLayer.length >= 2,
                "A multi-layer network needs 2 or more layers");
        for (int units : unitsPerLayer) {
            Check.require(units >= 1, "Every layer needs at least one unit");
        }
        this.unitsPerLayer = unitsPerLayer.clone();
        this.biasNode = addBiasNode;
        this.random = Check.notNull(random, "random generator is null");
        this.inputSize = unitsPerLayer[0];

        // --- build the layers -------------------------------------------------------------
        int inputUnits = inputSize + (biasNode ? 1 : 0);
        inputLayer = new Layer("Input", inputUnits);
        layers.add(inputLayer);                       // layers must be added in order
        int hiddenLayers = unitsPerLayer.length - 2;
        for (int i = 0; i < hiddenLayers; i++) {
            layers.add(new Layer("Hidden_" + i, unitsPerLayer[i + 1]));
        }
        outputLayer = new Layer("Output", unitsPerLayer[unitsPerLayer.length - 1]);
        layers.add(outputLayer);

        // --- connect the bias node to every hidden unit --------------------------------------
        if (biasNode) {
            for (int layer = 1; layer <= hiddenLayers; layer++) {
                for (int unit = 0; unit < unitsPerLayer[layer]; unit++) {
                    connectBias(layer, unit);
                }
            }
        }
        // --- fully connect consecutive layers ----------------------------------------------
        for (int layer = 1; layer < unitsPerLayer.length; layer++) {
            for (int source = 0; source < unitsPerLayer[layer - 1]; source++) {
                for (int dest = 0; dest < unitsPerLayer[layer]; dest++) {
                    connect(layer - 1, source, layer, dest);
                }
            }
        }
    }

    // ---------------------------------------------------------------- settings

    public double getLearningRate() { return learningRate; }
    public MultiLayerNetwork setLearningRate(double learningRate) { this.learningRate = learningRate; return this; }
    public double getMomentum() { return momentum; }
    public MultiLayerNetwork setMomentum(double momentum) { this.momentum = momentum; return this; }
    public double getErrorThreshold() { return errorThreshold; }
    /** Training stops as soon as the epoch error falls below this value (default 0.01). */
    public MultiLayerNetwork setErrorThreshold(double errorThreshold) { this.errorThreshold = errorThreshold; return this; }
    /** When true, {@link #train} prints its progress to the console like the 2001 program did. */
    public MultiLayerNetwork setVerbose(boolean verbose) { this.verbose = verbose; return this; }
    public boolean hasBiasNode() { return biasNode; }
    public int getInputSize() { return inputSize; }
    public int getOutputSize() { return outputLayer.getSize(); }
    public int getLayerCount() { return layers.size(); }
    /** Units in the given layer (0 = input, excluding the bias node). */
    public int getLayerSize(int layer) { return unitsPerLayer[layer]; }
    public int[] getUnitsPerLayer() { return unitsPerLayer.clone(); }

    // ---------------------------------------------------------------- training

    /**
     * Trains the network on a list of inputs and the corresponding list of desired outputs.
     * <p>
     * <b>Important:</b> element {@code i} of {@code inputs} must correspond to element {@code i}
     * of {@code targets}. For example, to train a one-output network with a positive and a
     * negative frame:
     * <pre>
     * List&lt;Frame&gt; inputs = List.of(positiveFrame, negativeFrame);
     * List&lt;IOVector&gt; targets = List.of(DoubleIOVector.of(1.0), DoubleIOVector.of(0.0));
     * TrainingResult result = network.train(inputs, targets, 400);
     * </pre>
     * One <i>epoch</i> presents every pattern once and adjusts the weights after each one. The
     * epoch error is the sum over the patterns of the "sum-squared error":
     * {@code E = 1/2 * sum_k (target_k - output_k)^2}. Training returns after {@code maxEpochs}
     * epochs or as soon as the epoch error is below the error threshold, whichever comes first.
     * Thousands of epochs are often needed; you can pass a large number because the method
     * returns automatically when the error is small enough.
     */
    public TrainingResult train(List<? extends IOVector> inputs, List<? extends IOVector> targets, int maxEpochs) {
        return train(inputs, targets, maxEpochs, null);
    }

    /** Same as {@link #train(List, List, int)} with a listener called after every epoch. */
    public TrainingResult train(List<? extends IOVector> inputs, List<? extends IOVector> targets,
                                int maxEpochs, EpochListener listener) {
        checkTrainingVectors(inputs, targets);
        Check.require(maxEpochs >= 1, "maxEpochs must be at least 1");
        if (verbose) {
            System.out.println("Training the network...");
        }
        int printEvery = Math.max(1, maxEpochs / 50);
        double[] history = new double[maxEpochs];
        int epochs = 0;
        boolean reached = false;
        for (int epoch = 0; epoch < maxEpochs; epoch++) {
            learnOneEpoch(inputs, targets);
            history[epoch] = error;
            epochs = epoch + 1;
            if (listener != null) {
                listener.onEpoch(epoch, error);
            }
            if (verbose && (error < 1.0 || epoch % printEvery == 0)) {
                System.out.println("\tEpoch " + epoch + ":\t error = " + percentFormat.format(error));
            }
            if (error < errorThreshold) {
                reached = true;
                break;
            }
        }
        if (verbose) {
            System.out.println("Training complete! (error = " + percentFormat.format(error) + " after " + epochs + " epochs)");
        }
        return new TrainingResult(epochs, error, Arrays.copyOf(history, epochs), reached);
    }

    private void checkTrainingVectors(List<? extends IOVector> inputs, List<? extends IOVector> targets) {
        Check.require(inputs != null && !inputs.isEmpty(), "There must be more than 0 vectors to train the network!");
        Check.require(targets != null && inputs.size() == targets.size(),
                "There must be the same number of input vectors as target vectors");
        Check.require(inputs.get(0).length() == inputSize,
                "The input vectors must have the same dimension as the input layer (" + inputSize + " units)");
        Check.require(targets.get(0).length() == outputLayer.getSize(),
                "The target vectors must have the same dimension as the output layer (" + outputLayer.getSize() + " units)");
    }

    /** One pass over all patterns: present, propagate, back-propagate, accumulate the error. */
    private void learnOneEpoch(List<? extends IOVector> inputs, List<? extends IOVector> targets) {
        error = 0.0;
        for (int i = 0; i < inputs.size(); i++) {
            IOVector target = targets.get(i);
            setInputs(inputs.get(i));
            propagate();
            backPropagate(target);
            error += computeError(target);     // uses the outputs computed before the update
        }
    }

    /** The current epoch error (see {@link #train}). */
    public double getError() {
        return error;
    }

    // ---------------------------------------------------------------- recognition

    /**
     * Applies an input vector to the input layer, propagates it through the network and returns
     * the output layer as an {@link IOVector}. If the output layer has 2 units, the returned
     * vector has length 2.
     */
    public IOVector recognize(IOVector input) {
        Check.require(input.length() == inputSize,
                "The network was built for input vectors of length " + inputSize + " (got " + input.length() + ")");
        setInputs(input);
        propagate();
        return getOutput();
    }

    /** The current values of the output units. */
    public IOVector getOutput() {
        DoubleIOVector out = new DoubleIOVector(outputLayer.getSize());
        for (Neuron n : outputLayer.neurons) {
            out.add(n.getOutput());
        }
        return out;
    }

    private void setInputs(IOVector input) {
        List<Neuron> units = inputLayer.neurons;   // has one extra unit when there is a bias node
        for (int i = 0; i < input.length(); i++) {
            units.get(i).output = input.get(i);
        }
        if (biasNode) {
            units.get(units.size() - 1).output = 1.0;
        }
    }

    private void propagate() {
        for (int i = 1; i < layers.size(); i++) {   // skip the input layer
            layers.get(i).computeOutputs();
        }
    }

    /** The "sum-squared error" cost for the current outputs against {@code target}. */
    private double computeError(IOVector target) {
        double sum = 0.0;
        for (int i = 0; i < target.length(); i++) {
            double diff = target.get(i) - outputLayer.neurons.get(i).getOutput();
            sum += diff * diff;
        }
        return sum / 2.0;
    }

    /** Back-propagation: deltas from the output layer back to the first hidden layer, then weight updates. */
    private void backPropagate(IOVector target) {
        outputLayer.computeOutputDeltas(target);
        for (int i = layers.size() - 2; i >= 1; i--) {
            layers.get(i).computeHiddenDeltas();
        }
        outputLayer.updateWeights(learningRate, momentum);
        for (int i = layers.size() - 2; i >= 1; i--) {
            layers.get(i).updateWeights(learningRate, momentum);
        }
    }

    // ---------------------------------------------------------------- wiring

    private void connect(int sourceLayer, int sourceUnit, int destLayer, int destUnit) {
        Neuron source = layers.get(sourceLayer).getNeuron(sourceUnit);
        Neuron dest = layers.get(destLayer).getNeuron(destUnit);
        new Synapse(source, dest, random);
    }

    private void connectBias(int destLayer, int destUnit) {
        Neuron bias = inputLayer.getNeuron(inputLayer.size - 1);
        Neuron dest = layers.get(destLayer).getNeuron(destUnit);
        new Synapse(bias, dest, random);
    }

    // ---------------------------------------------------------------- inspection

    /**
     * Weights of the synapses entering unit {@code unit} of layer {@code layer} (1 = first hidden
     * layer), ordered like the units of the previous layer. When the previous layer is the input
     * layer and a bias node exists, its weight is the last element.
     */
    public double[] getIncomingWeights(int layer, int unit) {
        Check.require(layer >= 1 && layer < layers.size(), "layer must be between 1 and " + (layers.size() - 1));
        Layer previous = layers.get(layer - 1);
        Neuron neuron = layers.get(layer).getNeuron(unit);
        double[] weights = new double[previous.size];
        for (Synapse s : neuron.incoming) {
            int index = previous.neurons.indexOf(s.from);
            if (index >= 0) {
                weights[index] = s.weight;
            }
        }
        return weights;
    }

    /**
     * All the incoming weights of {@code layer} at once (1 = first hidden layer): row {@code u} is
     * {@link #getIncomingWeights(int, int) getIncomingWeights(layer, u)}. Faster than asking unit by unit,
     * for example to record the weights while the network trains.
     */
    public double[][] getWeights(int layer) {
        Check.require(layer >= 1 && layer < layers.size(), "layer must be between 1 and " + (layers.size() - 1));
        Layer previous = layers.get(layer - 1);
        java.util.Map<Neuron, Integer> index = new java.util.IdentityHashMap<>();
        for (int i = 0; i < previous.size; i++) {
            index.put(previous.neurons.get(i), i);
        }
        Layer current = layers.get(layer);
        double[][] weights = new double[current.size][previous.size];
        for (int u = 0; u < current.size; u++) {
            for (Synapse s : current.neurons.get(u).incoming) {
                Integer i = index.get(s.from);
                if (i != null) {
                    weights[u][i] = s.weight;
                }
            }
        }
        return weights;
    }

    /** The outputs of the units of {@code layer} for the last input presented (0 = input layer, without the bias unit). */
    public double[] getOutputs(int layer) {
        Check.require(layer >= 0 && layer < layers.size(), "layer must be between 0 and " + (layers.size() - 1));
        double[] out = new double[unitsPerLayer[layer]];
        for (int u = 0; u < out.length; u++) {
            out[u] = layers.get(layer).neurons.get(u).getOutput();
        }
        return out;
    }

    /** Prints every neuron and its outgoing weights (debugging aid). */
    public void print() {
        for (Layer layer : layers) {
            layer.print();
        }
    }

    /** Describes the structure of the network. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Network structure:");
        for (Layer layer : layers) {
            sb.append("\n\t").append(layer);
        }
        if (biasNode) {
            sb.append("\n\t(the input layer includes a bias unit)");
        }
        return sb.toString();
    }
}
