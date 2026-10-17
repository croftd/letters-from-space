// Adopté d'un exemple aux réseaux neuronaux dessiné pour l'OCR:
// ("Optical Character Recognition" en anglais). Référence:
//
// http://diwww.epfl.ch/mantra/tutorial/french/index.html
//
// Modifications par David Croft pour le cours d'IA a l'ESI
// février 2001
// Cette classe était changé d'une classe qui s'appelle 'Perceptron'

package edu.esi.reseauxneuronaux;

import edu.esi.outils.Debug;
import java.util.*;
import java.text.NumberFormat;

/**
 * Cette classe modele un reseau neuronaux multi-couche. Vous pouvez decider
 * tout la structure du reseau (combien des couches intermediare, combien des
 * noeuds dans la couche d'entree/sortie/intermediares). Il utilise
 * l'interface VecteurES (Vecteur Entree/Sortie) pour les entrees et sorties 
 * du reseau.
 * <p>
 * @see VecteurES
 */
public class ReseauMulticouche {

  Vector           layers_;
  Vector           vecteursEntree_;		// les énchantillons des entrées
  Vector           vecteursSortie_;		// les énchantillons des sorties

  Layer     coucheEntree_;		// la couche d'entrée
  Layer     coucheSortie_;		// la couche de sortie

  private boolean bias_ = false;
  private int entreeSize_;
  private double    error_;
	
  private int numLayers_;
  private int[] unitsPerLayer_;

  private StringBuffer toString_ = new StringBuffer(300);

  // get a percent format object to print out the errors as percentages
  NumberFormat percentFormat_ = NumberFormat.getPercentInstance();
	
  /**
   * Cree un nouveau reseau multi-couche avec le nombre des couche specifie.
   * Le deuxieme parametre est une collection (array) de type int qui specifie 
   * combien des noeuds dans chaque couche. Le dernier parametre est si vous
   * voulez ajouter un noeuds supplementaire pour la couche d'entree qui
   * toujour dans l'activation aux noeuds dans les couches intermediaire.
   * (Nous appellons ceci le noeuds de penchant (ou "bias node" en anglais).
   * Quelque fois c'aide pour former le reseau, mais des autres fois c'a fait
   * des choses plus complique. Je ne comprends pas exactement comment ca
   * change les resultats, mais vous pouvez jouer avec cet parametre.
   * <p>
   * Par exemple:
   * <PRE>
   * int numCouches = 3;
   * int[] numNoeudsPerCouche = { 16, 4, 2 };
   * ReseauMulticouche reseau = 
   *   new ReseauMulticouche( numCouches, numNoeudsPerCouche, false);
   * </PRE>
   */
  public ReseauMulticouche(	int numCouches, int[] numNoeudsPerCouche,
		  					boolean ajouteNoeudDePenchant) {
	
	Debug.ASSERT( numCouches >= 2, "Un reseaux multicouche a besoin de " + 
		"2 ou plus couches (layers)");
	Debug.ASSERT( numNoeudsPerCouche.length == numCouches, 
		"Il faut specifier " +
		"le nombre des noeuds (units) pour chaque couche");

	bias_ = ajouteNoeudDePenchant;

	numLayers_ = numCouches;
	unitsPerLayer_ = numNoeudsPerCouche;

    layers_          = new Vector();
    vecteursEntree_    = new Vector();
    vecteursSortie_   = new Vector();

	initializeLayers();
    error_ = 0.0;
  }

  private void initializeLayers() {

	toString_.append("\nLa structure du reseau: ");
	
	int i,j,k=0;

	entreeSize_ = unitsPerLayer_[0];
	int entreeSizeAvecBias = entreeSize_;

	// si il y a un noeud supplémentaire dans la couche des entrées 
	// pour le penchant (bias)
	if (bias_) {
		entreeSizeAvecBias += 1;
	}
	coucheEntree_ = new Layer("Entree", entreeSizeAvecBias);
	toString_.append("\n\t" + coucheEntree_.toString());

	// Note - it is important that the layers are added in the proper order
	
    layers_.addElement(coucheEntree_);

	int numHiddenLayers = numLayers_-2;

	// initialize the hidden layers if they exist
	if (numHiddenLayers != 0) {
		Layer layer;
		for (i=0; i < numHiddenLayers; i++) {
			String label = "Cachee_" + String.valueOf(i);
			layer = new Layer(label, unitsPerLayer_[i+1]);
			toString_.append("\n\t" + layer.toString());
			layers_.addElement(layer);
		}
	} 

	// add the output layer
	coucheSortie_ = 
		new Layer("Sortie",unitsPerLayer_[unitsPerLayer_.length-1]);
	toString_.append("\n\t" + coucheSortie_.toString());
    layers_.addElement(coucheSortie_);

	if (bias_) {
		// connect bias to nodes in the hidden layers
		if (numHiddenLayers != 0) {
			for (i=1; i <= numHiddenLayers; i++) {
				for (j=0; j < unitsPerLayer_[i]; j++) {
					// connects the bias node from the input layer
					biasConnect(i /*layer number*/, j /*neuron in layer i*/);
				}
			}
		}
	}
	
	// connect bias to the first node of the output?
	//biasConnect(numLayers_-1, 0);

	// connect the layers together
	for (i=1; i<numLayers_; i++) {
		int sourceLayer = i-1;
		int destLayer = i;
		for (j=0; j<unitsPerLayer_[sourceLayer]; j++) {
			for (k=0; k<unitsPerLayer_[destLayer]; k++) {
				connect(sourceLayer, j, destLayer, k);
			}
		}
	}
  }
	
  /**
   * Forme le reseau avec un Vector des entrees et un Vector des sorties.
   * <p>
   * <i>Importante:</i>
   * <p>
   * La fonction forme() revient apres il a fini tous les iterations, ou apres 
   * l'erreur est suffisamment petite - quand la difference entre:
   * <p><i>
   * (les vecteurs de la sortie nous utilisons pour former le reseau)</i><br>
   * et <br><i>
   * (cela qui le reseau sort quand nous appliquons une des entrees de la 
   * formation)</i><br> est moins qu'un point du percentage. <p>
   * L'erreur est calculer avec une fonction du cout qui s'appelle 
   * "erreur somme - rendue carre".  C'est:
   * <p>
   * <img src="../../../images/costFunction.gif">
   * <p>
   * Souvent, milliers des iterations sont exige pour former un grand reseau 
   * - tout depend de la structure du reseau et le nombre des des 
   * echantillons (si il y a beaucoup des enchantillons, c'est plus difficile
   * pour ajuster les poids du reseau a accommoder tous les enchantillons). 
   * Vous pouvez mettre le nombre d'iterations a un grand nombre, parce que 
   * la fonction reviendra automatiquement quand l'erreur est suffisamment 
   * petite. 
   * <p>
   * <i>Aussi importante:</i>
   * <p>
   * Il faut que l'element a l'index i dans les entrees correspond
   * avec element i dans les sorties. Par exemple:
   * <p><PRE>
   * // Remarque:
   * // reseau est cadre.longuer() pour la couche de l'entree et il y a
   * // seulement un noeud dans la couche de la sortie (pour cette exemple)
   *
   * // Cree un Vector avec deux entrees - un pour un cadre qui represente un
   * // enchantillon positif, et le deuxieme pour un cadre qui represente un
   * // enchantillon negatif.
   * Vector entrees = new Vector(2);
   * entrees.addElement(cadrePositif);
   * entrees.addElement(cadreNegatif);
   *
   * // Cree un Vector avec deux sorties qui corresponds aux indexes pour les
   * // entrees (1.0 pour la premiere positif, 0.0 pour la deuxieme negatif).
   * Vector sorties = new Vector(2);
   * VecteurESDouble sortie = new VectorESDouble(2);
   * sortie.addElement(new Double(1.0));
   * sortie.addElement(new Double(0.0));
   * sorties.addElement(sortie);
   *
   * // Regarde! entrees.elementAt(0) est couple avec sorties.elementAt(0)
   * // et entrees.elementAt(1) est couple avec sorties.elementAt(1)
   *
   * // Maintenant, forme le reseau avec ces Vectors pour 400 iterations
   * reseau.forme(entrees, sorties, 400);
   * </PRE>
   */
  public void forme(Vector vecteursEntree, Vector vecteursSortie, 
		  			int numIterations) {

	setVecteursPourLaFormation(vecteursEntree, vecteursSortie);
	
	System.out.println("Forme le reseau...");

	// if there are many iterations we want to limit how often we print 
	// the current error
	int printErrorEveryNthTime = numIterations/50;
	int bigValue = 1000;


	for(int i=0; i < numIterations; i++) {
		learn(1);
		if (error_ < 0.01) {
			break;
		}
		if (error_ < 1 || (i % printErrorEveryNthTime == 0) ) {
			printIterationAndError(i);
		}
		if (i >= bigValue && i % bigValue == 0) {
			System.out.println(
				"\tNom d'un chien! Ca prend le temps pour former le reseau!");
		}
	}
	System.out.println("Formation complet! (Erreur = " +
		percentFormat_.format(error_) + ")");
  }

  private final void printIterationAndError(int iteration) {
	System.out.println("\tIteration " + iteration + ":\t Erreur = " +
			percentFormat_.format(error_));
  }

  private void setVecteursPourLaFormation(	Vector vecteursEntree, 
		  									Vector vecteursSortie) {

		Debug.ASSERT(vecteursEntree.size() > 0,
			"Il faut qu'il y a plus que 0 vecteurs pour former le reseau!");

		Debug.ASSERT(vecteursEntree.size() == vecteursSortie.size(),
			"Il faut que il y a la meme nombre des vecteurs entree " +
			"qu'il y a vecteurs sortie");

		VecteurES entree = (VecteurES)vecteursEntree.elementAt(0);
		VecteurES sortie = (VecteurES)vecteursSortie.elementAt(0);

		int lengthEntree = entree.longueur();
		int lengthSortie = sortie.longueur();
		
		Debug.ASSERT(lengthEntree == entreeSize_,
			"Il faut que les dimensions des vecteurs entree sont la meme " +
			"dimension de la couche entree du reseau (" + entreeSize_ + 
			" noeuds)");

		Debug.ASSERT(lengthSortie == coucheSortie_.getSize(),
			"Il faut que les dimensions des vecteurs sortie sont la meme " +
			"dimension de la couche sortie du reseau (" + 
			coucheSortie_.getSize() + " noeuds)");

		vecteursEntree_ = vecteursEntree;
		vecteursSortie_ = vecteursSortie;
  }

  private void addLayer(int n,String name) {
    layers_.insertElementAt(new Layer(name,n),layers_.size()-1);
  }
	
  private Layer getLayer(int i) {
	Layer layer = null;
	try {
		layer = (Layer)layers_.elementAt(i);
	} catch (ArrayIndexOutOfBoundsException e) {
		System.err.println("Erreur: couche " + i + " n'existe pas");
	}
	return layer;
  }

  private void connect(	int sourceLayer,int sourceNeuron,
		      			int destLayer,int destNeuron) {
	Neuron source = getLayer(sourceLayer).getNeuron(sourceNeuron);
	Neuron dest = getLayer(destLayer).getNeuron(destNeuron);
    new Synapse(source, dest);
  }

  private void biasConnect(int destLayer,int destNeuron) {
	Neuron input = coucheEntree_.getNeuron(coucheEntree_.size-1);
	Neuron dest = getLayer(destLayer).getNeuron(destNeuron);
	//System.out.println("\tAssocie neurone " + dest +
	//	" avec le noeud du penchant " + input);
    new Synapse(input, dest);
  }
	
  /**
   * Rend un String qui decrit la structure du reseau.
   */
  public String toString() {
	return toString_.toString();
  }
	
  /**
   * Applique un vecteur a la couche de l'entree et calcule la production.
   * Alors, un VecteurES qui represente la couche de la sortie est rendu. Si
   * il y a 2 noeuds dans la couche de la sortie, ce VecteurES va avoir une
   * longueur de 2.
   * <p>
   * @see #getOutput()
   */
  public VecteurES reconnais(VecteurES entree) {
	
	Debug.ASSERT(entree.longueur() == entreeSize_,
		"Le réseau était formé avec les vecteurs qui possedent " +
		"la longueur de  " + entreeSize_ + " noeuds");

    initInputs(entree);
    propagate();
    return getOutput();
  }

  private void learn(int iterations) {
    Enumeration iS;
    Enumeration oS;
    for(int i = 0; i<iterations; i++) {
      // accumulate total error over each epoch
      error_ = 0.0;
      iS = vecteursEntree_.elements();
      oS = vecteursSortie_.elements();
      while(iS.hasMoreElements()) {
       VecteurES temp = (VecteurES)oS.nextElement();
       learnPattern((VecteurES)iS.nextElement(), temp);
       error_ += computeError(temp);
      }
    }
  }

  private final void learnPattern(VecteurES entree, VecteurES sortie) {
    initInputs(entree);
    propagate();
    bpAdjustWeights(sortie);
  }

  private final void initInputs(VecteurES entree) {
    Neuron neuron;

	// we should be guaranteed that coucheEntree has 1 more element than
	// the input vector
    Enumeration e = coucheEntree_.neurons.elements();
	for (int i=0; i < entree.longueur(); i++) {
		neuron = (Neuron)e.nextElement();
		neuron.output = entree.getDoubleAt(i);
	}
	if (bias_) {
    	neuron = (Neuron)e.nextElement(); // bias;
    	neuron.output = 1.0;
	}
  }

  private final void propagate() {
    Layer layer;
    Enumeration e = layers_.elements();
    e.nextElement(); // skip the input layer
    while(e.hasMoreElements()) {
      layer = (Layer)e.nextElement();
      layer.computeOutputs();
    }
  }
	
  /**
   * Rend un VecteurES avec les valeurs courantes des noeuds dans la couche de
   * la sortie.
   * <p>
   * @see #reconnais(VecteurES)
   */
  public VecteurES getOutput() {
	VecteurESDouble sortie = new VecteurESDouble(coucheSortie_.neurons.size());
    Neuron neuron;
    Enumeration e = coucheSortie_.neurons.elements();
    while (e.hasMoreElements()) {
      neuron = (Neuron)e.nextElement();
      sortie.addElement(new Double(neuron.getOutput()));
    }
    return sortie;
  }
	
  /**
   * Cette fonction calcule l'erreur avec une fonction du cout qui 
   * s'appelle "erreur somme - rendue carre".<p>
   * @see #forme(Vector, Vector, int)
   */
  private double computeError(VecteurES sortie) {
    Neuron neuron;
    double sum=0.0;
    double tmp;
    Enumeration e = coucheSortie_.neurons.elements();
	for (int i=0; i < sortie.longueur(); i++) {
      neuron = (Neuron)e.nextElement();
      tmp = sortie.getDoubleAt(i) - neuron.getOutput();
	  //System.out.println(tmp);
      sum += tmp * tmp;
    }
    return sum/2.0;
  }

  private double currentError() {
    return error_;
  }

  private void bpAdjustWeights(VecteurES sortie) {
    coucheSortie_.computeBackpropDeltas(sortie);
    for (int i=layers_.size()-2; i>=1; i--) {
    	((Layer)layers_.elementAt(i)).computeBackpropDeltas();
	}
    coucheSortie_.computeWeights();
    for (int i=layers_.size()-2; i>=1; i--) {
		((Layer)layers_.elementAt(i)).computeWeights();
	}
  }

  void print() {
    Layer layer;
    Enumeration e = layers_.elements();
    while(e.hasMoreElements()) {
      layer = (Layer)e.nextElement();
      layer.print();
    }
  }
}
