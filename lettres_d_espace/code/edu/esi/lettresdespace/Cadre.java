// Crée par David Croft pour le cours d'IA a l'ESI
// février 2001

package edu.esi.lettresdespace;

import edu.esi.reseauxneuronaux.VecteurES;
import edu.esi.outils.Debug;

import java.io.*;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Vector;

/**
 * Represente un cadre d'une Video. Un cadre est une matrice 
 * avec les dimensions HAUTER par LARGEUR qui peut etre utilisee pour
 * representer une image simple (par exemples, cela d'une lettre...).
 * <p>
 * Remarque: cette classe 'implements' edu.esi.reseauxneuronaux.VecteurES,
 * afin qu'il peut etre utilise directement comme une entree dans un 
 * ReseauMulticouche.
 * <p>
 * @see Video
 * @see edu.esi.reseauxneuronaux.ReseauMulticouche
 */
public class Cadre implements VecteurES, Serializable {

	public static final int HAUTEUR = 8;
	public static final int LARGEUR = 8;
	
	private int[][] donnee_;
	private int cadreNo_ = -1;
	private char lettre_ = Character.MIN_VALUE;
	
	/**
	 * Cree un nouveau Cadre vierge.
	 */
	public Cadre() {
		donnee_ = new int[ HAUTEUR ][ LARGEUR ];
	}
	
	/**
	 * Cree un nouveau Cadre avec une matrice des int. Il faut que les
	 * dimensions de donnee[][] sont HAUTEUR par LARGEUR.
	 */
	public Cadre(int[][] donnee) {

		// Verifie que les dimensions de 'donnee' sont HAUTEUR par LARGEUR
		//
		Debug.ASSERT(donnee.length == HAUTEUR, 
			"Erreur: il faut que l'hauteur d'un cadre est " + HAUTEUR + 
			" elements");
		Debug.ASSERT(donnee[0].length == LARGEUR, 
			"Erreur: il faut que la largeur d'un cadre est " + LARGEUR + 
			" elements");
		donnee_ = donnee;
	}
	
	/**
	 * Cree un nouveau cadre par utilisant un autre cadre. Il fait une copie
	 * de tous les donnees.
	 */
	public Cadre(Cadre cadre) {

		donnee_ = new int[ HAUTEUR ][ LARGEUR ];

		for (int i=0; i < HAUTEUR; i++) {
			for (int j=0; j < LARGEUR; j++) {
				donnee_[i][j] = cadre.donnee_[i][j];
			}
		}
	}
	
	/**
	 * Quand un cadre existe par rapport a autres cadres dans une video, c'est
	 * utile a assigner un index a chaque cadre. Cette method assigne cet
	 * index.
	 */
	public void setCadreNo(int numero) {
		Debug.ASSERT(numero >= 0,
			"Erreur: il faut que le numero pour un cadre est plus que 0");
		cadreNo_ = numero;
	}
	
	/**
	 * Obtient le numero de ce cadre si c'existe (si non, cette fonction
	 * returne -1).
	 */
	public int getCadreNo() {
		return cadreNo_;
	}
	
	/**
	 * Fonction d'outil - si ce cadre represente une lettre, vous pouvez
	 * assigner un caractere - remarque que ca fait rien aux donnees
	 * en base.
	 *
	 * @see #getLettre()
	 */
	public void setLettre(char lettre) {
		lettre_ = lettre;
	}
	
	/**
	 * La meme idee que setLettre - seulement un outil pour rappeller si 
	 * ce cadre resemble un lettre et si oui, quelle lettre. (Mais
	 * premierement - il faut reconnaitre quelle lettre ca resemble!).
	 */
	public char getLettre() {
		return lettre_;
	}

	// overload the methods for VecteurES so that we can use a Cadre object
	// directly as input into a ReseauMulticouche
	
	/**
	 * Rend la longueur de ce VecteurES (pour un cadre c'est 
	 * HAUTER par LARGEUR).
	 */
	public int longueur() {
		return HAUTEUR * LARGEUR;
	}
	
	/**
	 * Une fonction pour l'interface VecteurES - pour un cadre cela rend
	 * le donnee a l'index [index/LARGEUR][index%LARGEUR].
	 */
	public double getDoubleAt(int index) {

		// eg. getElement(200) should return donnee[10][0];
		// eg. getElement(224) should return donnee[11][4];

		int row = index / LARGEUR;
		int column = index % LARGEUR;

		return (double)donnee_[row][column];
	}
	
	/**
	 * Assigne le donne a l'index specifie. Pour comprendre common ca marche
	 * avec l'index (il faut qu'il traduit l'index a deux dimension):
	 * @see #getDoubleAt(int)
	 */
	public void set(int index, double value) {
		int row = index / LARGEUR;
		int column = index % LARGEUR;
		donnee_[row][column] = (int)value;
	}

	/**
	 * Rend le donnee a ligne i, colonne j.
	 */
	public int get(int i, int j) {
		Debug.ASSERT(i < HAUTEUR, 
			"Erreur: ce n'est pas possible a lire element (" + i + "," + j +
			"): l'hauteur est limite a " + HAUTEUR);
		Debug.ASSERT(j < LARGEUR, 
			"Erreur: ce n'est pas possible a lire element (" + i + "," + j +
			"): la largeur est limite a " + LARGEUR);

		return donnee_[i][j];
	}
	
	/**
	 * Cette fonction convertit le cadre dans un object String.
	 * Ca resemble la matrice des donnees.
	 */
	public String toString() {
		StringBuffer string = new StringBuffer(LARGEUR*(HAUTEUR+1)+50);
		string.append("Cadre no: " + cadreNo_ + "\n");
		for (int i=0; i < HAUTEUR; i++) {
			for (int j=0; j < LARGEUR; j++) {
				string.append(donnee_[i][j]);
			}
			string.append("\n");
		}
		return string.toString();
	}
}

