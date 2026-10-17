// Crée par David Croft pour le cours d'IA a l'ESI
// février 2001

package edu.esi.lettresdespace;

import edu.esi.outils.Debug;

import java.io.*;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Vector;

/**
 * Pour charger les enchantillons des cadres d'un fichier.
 */
public class CadreChargeur {
	
	// disable the default constructor
	private CadreChargeur() {
	}
	
	/**
	 * Charge tout les cadres d'un fichier. 
	 * Cree un nouveau Vector des Cadres avec le nom du dossier (le
	 * dossier qui a les fichiers avec les enchantillons) et le nom de la 
	 * fichier. Par exemple: 
	 * <PRE>
	 * Vector cadres = CadreChargeur.charge("fichiers", "lettreA.dat");
	 * </PRE>
	 */
	public static Vector charge(String nomDuDossier, 
								String nomDeLaFichier) {

		File file = new File(nomDuDossier);
		URL codeBase = null;
		BufferedReader reader = null;

		try {
			codeBase = file.toURL();
		} catch (MalformedURLException e) {
			System.err.println(
				"Erreur: Nom du dossier est inexact: " + e);
			return null;
		}

		try {
			URL fichierComplet = new URL(codeBase, nomDeLaFichier);
			InputStream in = fichierComplet.openStream();
			InputStreamReader isr = new InputStreamReader(in);
			reader = new BufferedReader(isr);
		} catch  (Exception e) {
			System.err.println("Erreur: il y etait un probleme quand charger " +
				"la fichier " + nomDeLaFichier + 
				"avec les enchantillons des cadres video" +
				"Exception: " + e);
			return null;
		}
		
		System.out.println("Lit les enchantillons de cadre " +
			"de la fichier: " + nomDeLaFichier + 
			"\nCompte que les dimensions d'un cadre sont " + 
			Cadre.HAUTEUR + " lignes par " + Cadre.LARGEUR + " colonnes");

		Vector cadres = new Vector(50);
		String line = null;
		int lineNo = 0;
		int numCadres = 0;
		char currentLettre = Character.MIN_VALUE;
		String letterTag = "LETTRE=";

		try {
			outer: for(;; numCadres++) {
				
				inner: while (true) {
					// read until we arrive at something other 
					// than a blank line 
					line = reader.readLine();
					lineNo++;

					// check that the end of file has not been reached
					if (line == null) {	
						break outer;
					} else if (line.length() == 0) {
						continue;
					} else if (line.startsWith(letterTag)) {
						currentLettre = line.charAt(letterTag.length());
					} else {
						char first = line.charAt(0);
						if (first == '#') {
							continue;
						} else if ( ! Character.isWhitespace(first) ) { 
							// if character is not whitespace, then we 
							// believe we have arrived at an actual letter
							// definition using '.' and 'O'
							break inner;
						}
					}
				}

				// lit un cadre
				int[][] donnee = new int[Cadre.HAUTEUR][Cadre.LARGEUR];

				for(int j=0; j < Cadre.HAUTEUR; j++) {

					// check that the end of file has not been 
					// reached prematurely (line will be null if EOF)
					Debug.ASSERT(line, 	
						"arrive au fin de la fichier mais le cadre actuel " +
						"n'est pas complet (Ligne = " + lineNo + ")");

					// System.out.println(line);
					for(int k=0; k < Cadre.LARGEUR; k++) {
						char c = line.charAt(k);
						if (c=='O') {
							donnee[j][k] = 1;
						} else if (c=='.') {
							donnee[j][k] = 0;
						} else {
							Debug.EXIT(
								"Unexpected character '" + c + "' at line " +
								lineNo + ", column " + k);
						}
					}
					line = reader.readLine();
					lineNo++;
				}

				// Create a new Cadre and add it to the vector of Cadres
				Cadre next = new Cadre(donnee);
				next.setLettre(currentLettre);
				cadres.addElement(next);
			}

			System.out.println("Charge " + numCadres + " cadres de video\n");
			return cadres;

		} catch (Exception e) {
			System.err.println("Erreur: pendant la chargement de '" + 
				nomDeLaFichier + 
				"', Nombre de la ligne: " + lineNo + "\nException: " + e);
				e.printStackTrace();
			try {
				reader.close();
			} catch (IOException e2) {
			}
			return null;
		}
	}
}

