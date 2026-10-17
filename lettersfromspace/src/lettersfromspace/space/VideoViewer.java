// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
// Original: edu.esi.lettresdespace.VideoLecteur by David Croft, February 2001.
package lettersfromspace.space;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import lettersfromspace.util.Check;

/**
 * A viewer for looking at a video one frame at a time on the console, with a helper that asks
 * the user to confirm a frame ("is this the letter you are looking for?").
 *
 * @see Video
 * @see GraphicalVideoViewer
 */
public class VideoViewer {

    private final BufferedReader input = new BufferedReader(new InputStreamReader(System.in));

    /** Shows a frame by printing its pixels. For a graphical version use {@link GraphicalVideoViewer}. */
    public void show(Frame frame) {
        Check.notNull(frame, "frame is null");
        System.out.println(frame);
    }

    /**
     * Shows a frame and asks the user whether it is the frame type being searched for
     * (for example the letter 'A', possibly coming from outer space!). Returns true for yes.
     */
    public boolean confirmFrame(Frame frame) {
        show(frame);
        try {
            System.out.print("Confirm frame #" + frame.getFrameIndex() + "? (y/n): ");
            while (true) {
                String response = input.readLine();
                if (response == null) {
                    return false;
                }
                response = response.trim();
                if (response.equalsIgnoreCase("y") || response.equalsIgnoreCase("o")) {
                    return true;
                }
                if (response.equalsIgnoreCase("n")) {
                    return false;
                }
                System.out.print("Please answer y or n: ");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error while reading the answer", e);
        }
    }
}
