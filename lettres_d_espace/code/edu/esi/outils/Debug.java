// Crée par David Croft pour le cours d'IA a l'ESI
// février 2001

package edu.esi.outils;

/**
 * Classe d'outil pour vérifier les paramettres d'une fonction,
 * manier les exceptions, et pour quitter la programme si il y a une erreur.
 */
public class Debug
{
	// disable the default constructor
	private Debug() {}
	
	/**
	 * Print the given message to standard error, print the current stack
	 * trace and exit the program with a return value of 1.
	 */
	public static void EXIT(String message) {
		System.err.println(message);
		try {
			throw new RuntimeException(message);
		} catch (RuntimeException e) {
			e.printStackTrace(System.err);
		}
		System.err.println("program exiting");
		System.exit(1);
	}
	
	/**
	 * Assert that a programming statement is true. If false, 
	 * the EXIT(String message) method will be called.
	 * <p>
	 * For example: <PRE>
	 * Debug.ASSERT( noodleFactory.contains("Shrimp flavour"),
	 * 	"Noodle factory does not contain shrimp flavour!");
	 * </PRE>
	 */
	public static void ASSERT(boolean expression, String message) {
		if (expression == false) {
			EXIT("ASSERTION: " + message);
		}

	}
	
	/**
	 * Similar to the ASSERT(boolean, String) method, however asserts 
	 * that the given object is not null.
	 */
	public static void ASSERT(Object object, String message) {
		if (object == null) {
			ASSERT(false, message);
		}
	}
	
	/**
	 * Same as the ASSERT(Object) method however requires no error message.
	 * (Faster to write code, but less informative when there is a problem).
	 */
	public static void ASSERT(Object object) {
		ASSERT(object, "null object");
	}

	
	/**
	 * Calls the EXIT method using the error message and e.toString().
	 */
	public static void EXCEPTION(Exception e, String message) {
		EXIT("EXCEPTION: " + message + ":\n" + e.toString());
	}
}

	
