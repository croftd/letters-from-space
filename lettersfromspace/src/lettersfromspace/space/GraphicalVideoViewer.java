// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
// Original: edu.esi.lettresdespace.VideoLecteurGraphique by David Croft, February 2001.
package lettersfromspace.space;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Panel;

import lettersfromspace.util.Check;

/**
 * Same behaviour as {@link VideoViewer}, but shows each frame in a small AWT window: dark
 * cells for 0 and white cells for 1. Requires a display; on a headless machine use the
 * console viewer or the web dashboard instead.
 */
public class GraphicalVideoViewer extends VideoViewer {

    private Frame currentFrame = new Frame();

    private final java.awt.Frame window = new java.awt.Frame("Frame viewer");
    private final Panel panel = new Panel() {
        @Override
        public void paint(Graphics g) {
            Dimension size = getSize();
            int cellHeight = size.height / Frame.HEIGHT;
            int cellWidth = size.width / Frame.WIDTH;
            for (int row = 0; row < Frame.HEIGHT; row++) {
                for (int col = 0; col < Frame.WIDTH; col++) {
                    g.setColor(currentFrame.getPixel(row, col) == 0 ? Color.black : Color.white);
                    g.fillRect(col * cellWidth, row * cellHeight, cellWidth, cellHeight);
                }
            }
        }
    };

    /** Opens the viewer window. Throws {@link java.awt.HeadlessException} when there is no display. */
    public GraphicalVideoViewer() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new java.awt.HeadlessException("No display available: use VideoViewer (console) or the web dashboard");
        }
        window.add(panel);
        window.setSize(224, 256);
        window.setVisible(true);
    }

    /** Shows the frame in the window (and nothing on the console). */
    @Override
    public void show(Frame frame) {
        currentFrame = Check.notNull(frame, "cannot show a null frame");
        panel.repaint();
    }

    public void close() {
        window.dispose();
    }
}
