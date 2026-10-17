// Crée par David Croft pour le cours d'IA a l'ESI
// février 2001

package edu.esi.lettresdespace;

import edu.esi.outils.Debug;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import java.io.*;

/**
 * Represente une serie des cadres, comme ca qui sorts d'un appareil-photo
 * video.
 * <p>
 * @see Cadre
 */
public class Video implements java.io.Serializable {

	private Cadre[] cadres_;
	
	/**
	 * Cree une nouvelle video d'une serie des cadres.
	 */
	public Video(Cadre[] cadres) {
		cadres_ = cadres;
	}
	
	/**
	 * Cree une nouvelle video avec les cadres vierges.
	 */
	public Video(int size) {
		cadres_ = new Cadre[size];
		for (int i=0; i < cadres_.length; i++) {
			cadres_[i] = new Cadre();
		}
	}
	
	/**
	 * Rend la longueur (nombre des cadres) dans cette video.
	 */
	public int getSize() {
		return cadres_.length;
	}

	/**
	 * Rend le cadre a l'index specifie. Il faut que index est moins de la
	 * longueur de la video est plus que 0.
	 */
	public Cadre getCadreAt(int index) {
		Debug.ASSERT( (index >= 0 && index < cadres_.length),
			"cadre index " + index + " n'est pas valide pour cette video");
		return cadres_[index];
	}

	/**
	 * Enregiste cette video a une fichier (vous specifiez le nom de la 
	 * fichier). Il comprime la video avant il met ca dans la fichier.
	 */
	public void save(String nomDeLaFichier) {
		try {
			System.out.println("Enregiste la video " + this + 
				" a fichier: " + nomDeLaFichier);
			FileOutputStream fo = new FileOutputStream(nomDeLaFichier);
			DeflaterOutputStream deo = new DeflaterOutputStream(fo);
			ObjectOutputStream so = new ObjectOutputStream(deo);
			so.writeObject(this);
			so.flush();
			deo.close();
			fo.close();
		} catch (Exception e) {
			Debug.EXCEPTION(e, "erreur quand enregiste la video");
		}
	}

	/**
	 * Charge une video d'une fichier. Il faut que le format de la fichier est
	 * la meme utilise par la fonction save() - d'habitude, seulement les
	 * video enregistre avec la fonction save() peut etre charge avec cette 
	 * fonction.
	 */
	public static Video charge(String nomDeLaFichier) {
		Video video = null;
		try {
			System.out.println("Charge une video de la fichier: " +
				nomDeLaFichier);
			long time = System.currentTimeMillis();
			FileInputStream fi = new FileInputStream(nomDeLaFichier);
			InflaterInputStream ii = new InflaterInputStream(fi);
			ObjectInputStream si = new ObjectInputStream(ii);
			video = (Video)si.readObject();
			Debug.ASSERT(video);
			fi.close();
			time = System.currentTimeMillis() - time;
			System.out.println("Charge " + video + " dans " + time + " ms.");
		} catch (Exception e) {
			Debug.EXCEPTION(e, "Ne peux pas charger le video de " +
				nomDeLaFichier);
		}
		return video;
	}
}
