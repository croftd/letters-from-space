// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
// Original: edu.esi.lettresdespace.Cadre by David Croft, February 2001.
package lettersfromspace.space;

import java.util.ArrayList;
import java.util.List;

import lettersfromspace.neuralnet.IOVector;
import lettersfromspace.util.Check;

/**
 * One frame of a {@link Video}: a HEIGHT x WIDTH matrix of pixels where 1 is a star ('O')
 * and 0 is dark sky ('.'). A frame can hold a simple image such as a letter.
 * <p>
 * This class implements {@link IOVector}, so a frame can be used directly as the input of a
 * {@link lettersfromspace.neuralnet.MultiLayerNetwork}: element {@code index} of the vector is
 * pixel {@code [index / WIDTH][index % WIDTH]}.
 *
 * @see Video
 */
public class Frame implements IOVector {

    public static final int HEIGHT = 8;
    public static final int WIDTH = 8;
    public static final char STAR = 'O';
    public static final char DARK = '.';
    /** Value of {@link #getLetter()} when the frame is not known to represent a letter. */
    public static final char NO_LETTER = Character.MIN_VALUE;

    private final int[][] pixels;
    private int frameIndex = -1;
    private char letter = NO_LETTER;

    /** Creates a blank (all dark) frame. */
    public Frame() {
        pixels = new int[HEIGHT][WIDTH];
    }

    /** Creates a frame from a HEIGHT x WIDTH matrix of 0/1 values (the matrix is used, not copied). */
    public Frame(int[][] pixels) {
        Check.require(pixels != null && pixels.length == HEIGHT, "A frame must have " + HEIGHT + " rows");
        for (int[] row : pixels) {
            Check.require(row != null && row.length == WIDTH, "A frame must have " + WIDTH + " columns");
        }
        this.pixels = pixels;
    }

    /** Copies the pixels of another frame (not its index or letter). */
    public Frame(Frame other) {
        this();
        for (int i = 0; i < HEIGHT; i++) {
            System.arraycopy(other.pixels[i], 0, pixels[i], 0, WIDTH);
        }
    }

    /** Builds a frame from HEIGHT text rows of WIDTH characters, 'O' for a star and '.' for dark sky. */
    public static Frame fromRows(List<String> rows) {
        Check.require(rows != null && rows.size() == HEIGHT, "A frame needs " + HEIGHT + " rows of text");
        int[][] pixels = new int[HEIGHT][WIDTH];
        for (int i = 0; i < HEIGHT; i++) {
            String row = rows.get(i);
            Check.require(row != null && row.length() == WIDTH, "Row " + i + " must have " + WIDTH + " characters: '" + row + "'");
            for (int j = 0; j < WIDTH; j++) {
                pixels[i][j] = pixelValue(row.charAt(j));
            }
        }
        return new Frame(pixels);
    }

    public static Frame fromRows(String... rows) {
        return fromRows(List.of(rows));
    }

    /** Builds a frame from a single string of HEIGHT*WIDTH characters (row after row). */
    public static Frame fromCompactString(String s) {
        Check.require(s != null && s.length() == HEIGHT * WIDTH, "Expected " + (HEIGHT * WIDTH) + " characters, got " + (s == null ? 0 : s.length()));
        List<String> rows = new ArrayList<>(HEIGHT);
        for (int i = 0; i < HEIGHT; i++) {
            rows.add(s.substring(i * WIDTH, (i + 1) * WIDTH));
        }
        return fromRows(rows);
    }

    private static int pixelValue(char c) {
        if (c == STAR || c == '1') {
            return 1;
        }
        if (c == DARK || c == '0') {
            return 0;
        }
        throw new IllegalArgumentException("Unexpected pixel character '" + c + "' (use '" + STAR + "' or '" + DARK + "')");
    }

    /** The position of this frame inside its video, or -1 when unknown. */
    public int getFrameIndex() {
        return frameIndex;
    }

    public void setFrameIndex(int index) {
        Check.require(index >= 0, "A frame index must be 0 or more");
        frameIndex = index;
    }

    /** The letter this frame is known to show (training samples), or {@link #NO_LETTER}. Does not change the pixels. */
    public char getLetter() {
        return letter;
    }

    public void setLetter(char letter) {
        this.letter = letter;
    }

    public boolean hasLetter() {
        return letter != NO_LETTER;
    }

    // ---- IOVector: lets a frame be used directly as network input ----

    /** Length of the vector: HEIGHT * WIDTH. */
    @Override
    public int length() {
        return HEIGHT * WIDTH;
    }

    /** Pixel at vector position {@code index}, i.e. {@code pixels[index / WIDTH][index % WIDTH]}. */
    @Override
    public double get(int index) {
        return pixels[index / WIDTH][index % WIDTH];
    }

    /** Sets the pixel at vector position {@code index} (see {@link #get(int)}). */
    public void set(int index, double value) {
        pixels[index / WIDTH][index % WIDTH] = (int) value;
    }

    /** Pixel at row {@code row}, column {@code col}. */
    public int getPixel(int row, int col) {
        Check.require(row >= 0 && row < HEIGHT, "Row " + row + " is outside the frame (height " + HEIGHT + ")");
        Check.require(col >= 0 && col < WIDTH, "Column " + col + " is outside the frame (width " + WIDTH + ")");
        return pixels[row][col];
    }

    public void setPixel(int row, int col, int value) {
        Check.require(value == 0 || value == 1, "A pixel is 0 or 1");
        pixels[row][col] = value;
    }

    /** Number of stars (pixels set to 1). */
    public int countStars() {
        int n = 0;
        for (int[] row : pixels) {
            for (int p : row) {
                n += p;
            }
        }
        return n;
    }

    /** True when both frames have identical pixels. */
    public boolean samePixels(Frame other) {
        for (int i = 0; i < HEIGHT; i++) {
            for (int j = 0; j < WIDTH; j++) {
                if (pixels[i][j] != other.pixels[i][j]) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The frame as HEIGHT strings of 'O' and '.'. */
    public List<String> toRows() {
        List<String> rows = new ArrayList<>(HEIGHT);
        for (int[] row : pixels) {
            StringBuilder sb = new StringBuilder(WIDTH);
            for (int p : row) {
                sb.append(p == 1 ? STAR : DARK);
            }
            rows.add(sb.toString());
        }
        return rows;
    }

    /** The frame as one string of HEIGHT*WIDTH characters. */
    public String toCompactString() {
        return String.join("", toRows());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Frame #" + frameIndex);
        if (hasLetter()) {
            sb.append(" (letter ").append(letter).append(')');
        }
        sb.append('\n');
        for (String row : toRows()) {
            sb.append(row).append('\n');
        }
        return sb.toString();
    }
}
