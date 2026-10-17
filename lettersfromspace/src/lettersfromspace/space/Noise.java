// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
// Original: the addNoise / newCadreViergeBruyant methods of edu.esi.lettresdespace.prof.VideoCreateur.
package lettersfromspace.space;

import java.util.Random;

import lettersfromspace.util.Check;

/**
 * Random noise for frames: flips pixels so that the same letter never looks exactly the same
 * twice, and creates "empty sky" frames with random stars. Used by the instructor's video
 * generator and to make extra training samples from a single hand-drawn letter.
 */
public final class Noise {

    private Noise() {}

    /**
     * Flips up to {@code maxPercent} percent of the pixels of {@code frame} (at least one pixel).
     * The flipped pixels are spread over the frame: the frame is cut into equal intervals and
     * one random pixel per interval is inverted, exactly like the 2001 generator did.
     */
    public static void flipRandomPixels(Frame frame, int maxPercent, Random random) {
        Check.require(maxPercent >= 0 && maxPercent <= 100, "maxPercent must be between 0 and 100");
        int length = Frame.HEIGHT * Frame.WIDTH;                       // 64
        int maxNoisy = (int) (length * (maxPercent / 100.0f));        // e.g. 8% -> 5 pixels
        if (maxNoisy == 0) {
            maxNoisy = 1;
        }
        int noisy = random.nextInt(maxNoisy);
        if (noisy == 0) {
            noisy = 1;
        }
        int interval = length / noisy;
        int start = 0;
        for (int i = 0; i < noisy; i++) {
            int position = start + random.nextInt(interval);
            frame.set(position, frame.get(position) == 1.0 ? 0.0 : 1.0);
            start += interval;
        }
    }

    /** A copy of {@code frame} with up to {@code maxPercent} percent of its pixels flipped. */
    public static Frame noisyCopy(Frame frame, int maxPercent, Random random) {
        Frame copy = new Frame(frame);
        copy.setLetter(frame.getLetter());
        flipRandomPixels(copy, maxPercent, random);
        return copy;
    }

    /** An "empty sky" frame: blank, then a random amount of noise up to {@code maxPercent} percent. */
    public static Frame randomSky(int maxPercent, Random random) {
        Frame frame = new Frame();
        flipRandomPixels(frame, random.nextInt(Math.max(1, maxPercent)), random);
        return frame;
    }
}
