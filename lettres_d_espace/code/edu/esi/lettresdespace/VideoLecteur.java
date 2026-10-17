// Crée par David Croft pour le cours d'IA a l'ESI
// février 2001

package edu.esi.lettresdespace;

import edu.esi.outils.Debug;

import java.io.*;

/**
 * Represente un lecteur pour regarder une video.
 * Fondamentalement, il vous permet d'envisager les cadres une par une.
 * Aussi, il fournit une fonction de l'utilite pour demander une reponse de
 * l'utilisateur.
 *
 * @see Video
 * @see VideoLecteurGraphique
 */
public class VideoLecteur {

	private BufferedReader input_ = 
		new BufferedReader(new InputStreamReader(System.in));
	
	/**
	 * Regarde le cadre specifie par imprimer tous les donnees dans le cadre a
	 * System.out. Pour un version graphique, utilise VideoLecteurGraphique.
	 *
	 * @see VideoLecteurGraphique#affiche(Cadre)
	 */
	public void affiche(Cadre cadre) {
		Debug.ASSERT(cadre, "Erreur: cadre est null");
		System.out.println(cadre);
	}

	/**
	 * Regarde le cadre specifie et demande une reponse de l'utilisateur
	 * (oui ou non), pour verifier ce cadre. Vous pouvez utilise cette
	 * fonction pour afficher un cadre et demander s'il ressemble au type de
	 * cadre que vous cherchez (par exemple, si vous cherchez tous les cadres
	 * qui resemble la lettre 'A', possiblement qui vient de l'espace!).
	 */
	public boolean confirmCadre(Cadre cadre) {
		affiche(cadre);

		try {
			System.out.print(
				"Confirm cadre No_" + cadre.getCadreNo() + " ? (o/n): ");
			while (true) {
				String response = input_.readLine();
				if (response.equalsIgnoreCase("o")) {
					return true;
				} else if (response.equalsIgnoreCase("n")) {
					return false;
				} else {
					System.out.println("Il faut repondre o ou n!");
				}
			}
		} catch (IOException e) {
			Debug.EXCEPTION(e, "Pendant je lisais la reponse");
			return false;
		}
	}
}

