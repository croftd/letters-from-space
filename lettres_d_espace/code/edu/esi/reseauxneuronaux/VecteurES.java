// Crée par David Croft pour le cours d'IA a l'ESI
// février 2001

package edu.esi.reseauxneuronaux;

/**
 * Cela definit un 'interface' que peut etre rendu effectif par les autres
 * classes afin qu'ils peuvent entre se habitues comme entrees et sorties 
 * d'un ReseauMulticouche. VecteurES ca va dire Vecteur (Entree/Sortie).
 * <p>
 * Par example, regarde:
 * @see edu.esi.lettresdespace.Cadre
 * <p>
 * Aussi:
 * @see ReseauMulticouche
 */
public interface VecteurES {
	public int longueur();
	public double getDoubleAt(int index);
}
