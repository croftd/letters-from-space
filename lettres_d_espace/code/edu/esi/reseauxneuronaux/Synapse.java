// Adopté d'un exemple aux réseaux neuronaux dessiné pour l'OCR:
// ("Optical Character Recognition" en anglais). Référence:
//
// http://diwww.epfl.ch/mantra/tutorial/french/index.html
//
// Modifications par David Croft pour le cours d'IA a l'ESI
// février 2001

package edu.esi.reseauxneuronaux;

import java.util.*;

class Synapse
{
  double weight;
  double data;
  Neuron from;
  Neuron to;
  static Random random = new Random();

  Synapse(Neuron f,Neuron t)
  {
    from   = f;
    to     = t;
    weight = random.nextDouble() / 5.0;
    data   = 0.0;
    f.outlinks.addElement(this);
    t.inlinks.addElement(this);
  }
  public double getWeight()
  {
    return weight;
  }
}
