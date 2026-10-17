// Crée par David Croft pour le cours d'IA a l'ESI
// février 2001

package edu.esi.reseauxneuronaux;

import java.util.Vector;

/**
 * Classe d'utile pour cree un VecteurES avec les elements Double. Cela peut
 * etre utilise pour creer des entrees et sorties pour usage avec le reseau.
 */
public class VecteurESDouble extends Vector implements VecteurES {

	public VecteurESDouble(int size) {
		super(size);
	}

	public int longueur() {
		return size();
	}

	public double getDoubleAt(int index) {
		return ((Double)elementAt(index)).doubleValue();
	}
}
