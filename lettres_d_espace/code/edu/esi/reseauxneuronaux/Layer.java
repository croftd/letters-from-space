// Adopté d'un exemple aux réseaux neuronaux dessiné pour l'OCR:
// ("Optical Character Recognition" en anglais). Référence:
//
// http://diwww.epfl.ch/mantra/tutorial/french/index.html
//
// Modifications par David Croft pour le cours d'IA a l'ESI
// février 2001

package edu.esi.reseauxneuronaux;

import edu.esi.outils.Debug;
import java.util.*;

class Layer
{
  Vector neurons;
  int size;
  String label;

  public Layer(String l,int s)
  {
    size = s;
	label = l;
    neurons = new Vector(size);
    for(int i=0;i<s;i++)
    {
      String neuronLabel = label + "_N" + String.valueOf(i);
      neurons.addElement(new Neuron(neuronLabel));
    }
  }

  public int getSize() { return size; }

  public Neuron getNeuron(int i)
  {
	Neuron neuron = null;
	try {
		neuron = (Neuron)neurons.get(i);
	} catch (ArrayIndexOutOfBoundsException e) {
		Debug.ASSERT(neuron, "Cannot access neuron at index '" + i + 
			"' - vector size is " + neurons.size() + ": " + e);
	}
	return neuron;
  }
  public void computeOutputs()
  {
    Neuron neuron;
    Enumeration e = neurons.elements();
    while(e.hasMoreElements())
    {
      neuron = (Neuron)e.nextElement();
      neuron.computeOutput();
    }
  }

  public void computeBackpropDeltas(VecteurES sortie) // for output neurons
  {
	Debug.ASSERT(sortie.longueur() == neurons.size(), 
		"Output layer has a different " +
		"dimension than the output vector we are training with!");
    Neuron neuron;
    Enumeration e = neurons.elements();
	for (int i=0; i < sortie.longueur(); i++) {
      neuron = (Neuron)e.nextElement();
      neuron.computeBackpropDelta(sortie.getDoubleAt(i));
    }
  }
  public void computeBackpropDeltas() // for hidden neurons
  {
    Neuron neuron;
    Enumeration e = neurons.elements();
    while(e.hasMoreElements())
    {
      neuron = (Neuron)e.nextElement();
      neuron.computeBackpropDelta();
    }
  }
  public void computeWeights()
  {
    Neuron neuron;
    Enumeration e = neurons.elements();
    while(e.hasMoreElements())
    {
      neuron = (Neuron)e.nextElement();
      neuron.computeWeight();
    }
  }
  public void print()
  {
    Neuron neuron;
    Enumeration e = neurons.elements();
    while(e.hasMoreElements())
    {
      neuron = (Neuron)e.nextElement();
      neuron.print();
    }
  }
  public String toString() {
	  return label + " (" + size + " noeuds)";
  }
}

