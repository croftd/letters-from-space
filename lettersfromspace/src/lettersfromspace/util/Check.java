// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
// Original: edu.esi.outils.Debug by David Croft, February 2001.
package lettersfromspace.util;

/**
 * Small argument-checking helper, the successor of the 2001 {@code Debug} class.
 * <p>
 * The original printed a message, dumped a stack trace and called {@code System.exit(1)}.
 * The rewrite throws an exception instead, so the same classes can be used inside the
 * web dashboard server without killing it. Example:
 * <pre>
 * Check.require(noodleFactory.contains("shrimp"), "Noodle factory does not contain shrimp flavour!");
 * </pre>
 */
public final class Check {

    private Check() {}

    /** Throws {@link IllegalArgumentException} with {@code message} when {@code condition} is false. */
    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /** Throws {@link IllegalArgumentException} when {@code object} is null. Returns the object otherwise. */
    public static <T> T notNull(T object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
        return object;
    }

    /** Throws {@link IllegalStateException}: the program reached a state it should never reach. */
    public static IllegalStateException fail(String message) {
        return new IllegalStateException(message);
    }
}
