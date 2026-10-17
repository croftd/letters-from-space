// Crée par David Croft pour le cours d'IA a l'ESI
// février 2001

package edu.esi.lettresdespace;

import edu.esi.outils.Debug;

import java.awt.*;
import java.awt.geom.*;

/**
 * Fournit les memes fonctionnalites que VideoLecteur mais il
 * utilise le AWT (Abstract Window Toolkit) de Java pour presenter
 * l'utilisateur avec une image graphique d'un cadre.
 */
public class VideoLecteurGraphique extends VideoLecteur {

	private Cadre currentCadre_ = new Cadre();
	
	private Frame frame_ = new Frame("Cadre afficheur");
	private Panel panel_ = new Panel() {
		public void paint(Graphics g) {
			Dimension dimension = ((Panel)this).getSize();
			int hauteurElement = dimension.height/Cadre.HAUTEUR;
			int largeurElement = dimension.width/Cadre.LARGEUR;

			for (int i=0; i<Cadre.HAUTEUR; i++) {
				for (int j=0; j<Cadre.LARGEUR; j++) {
					int x1 = j*largeurElement;
					int y1 = i*hauteurElement;
					int x2 = x1+largeurElement;
					int y2 = y1+hauteurElement;

					int donnee = currentCadre_.get(i,j);
					if (donnee == 0) {
						g.setColor(Color.black);
					} else {
						g.setColor(Color.white);
					}
					g.fillRect(x1,y1,x2,y2);
				}
			}
		}
	};
	
	/**
	 * Cree un nouveau VideoLecteurGraphique pour regarder les cadres dans une
	 * video.
	 */
	public VideoLecteurGraphique() {
		frame_.add(panel_);
		frame_.setSize(224,256);
		frame_.show();
	}
	
	/**
	 * Fait la meme chose que VideoLecteur.affiche() mais il utilise un
	 * fenetre graphique pour afficher le cadre.
	 *
	 * @see VideoLecteur#affiche(Cadre)
	 */
	public void affiche(Cadre cadreVideo) {
		Debug.ASSERT(cadreVideo, "ne peux pax afficher un cadre video null");
		currentCadre_ = cadreVideo;
		panel_.repaint();
	}
}
