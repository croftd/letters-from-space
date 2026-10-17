// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
// Original: edu.esi.lettresdespace.CadreChargeur by David Croft, February 2001.
package lettersfromspace.space;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lettersfromspace.util.Check;

/**
 * Loads sample frames from a text file.
 * <p>
 * File format: frames are 8 lines of 8 characters ('O' = star, '.' = dark). Blank lines and
 * lines starting with '#' are ignored. A line {@code LETTER=X} says that the frames that
 * follow are samples of the letter X (the 2001 French tag {@code LETTRE=X} is still accepted).
 * <pre>
 * List&lt;Frame&gt; frames = FrameLoader.load(Path.of("data/letters/letterA.dat"));
 * </pre>
 */
public final class FrameLoader {

    public static final String LETTER_TAG = "LETTER=";
    private static final String LEGACY_LETTER_TAG = "LETTRE=";

    private FrameLoader() {}

    /** Loads every frame of a file. */
    public static List<Frame> load(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            return read(reader, file.toString());
        }
    }

    /** Loads every frame of {@code fileName} inside {@code folder} (the 2001 calling style). */
    public static List<Frame> load(String folder, String fileName) throws IOException {
        return load(Path.of(folder, fileName));
    }

    /** Reads frames from an open reader; {@code sourceName} is only used in error messages. */
    public static List<Frame> read(BufferedReader reader, String sourceName) throws IOException {
        List<Frame> frames = new ArrayList<>();
        char currentLetter = Frame.NO_LETTER;
        String line;
        int lineNo = 0;
        while ((line = reader.readLine()) != null) {
            lineNo++;
            line = stripCr(line);
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            if (line.startsWith(LETTER_TAG) || line.startsWith(LEGACY_LETTER_TAG)) {
                Check.require(line.length() > LETTER_TAG.length(), sourceName + ":" + lineNo + ": letter tag without a letter");
                currentLetter = line.charAt(LETTER_TAG.length());
                continue;
            }
            char first = line.charAt(0);
            if (first == Frame.STAR || first == Frame.DARK) {
                // the start of a frame: this line plus the next HEIGHT-1 lines
                List<String> rows = new ArrayList<>(Frame.HEIGHT);
                rows.add(line);
                for (int i = 1; i < Frame.HEIGHT; i++) {
                    String next = reader.readLine();
                    lineNo++;
                    Check.require(next != null, sourceName + ": reached the end of the file but the current frame is not complete (line " + lineNo + ")");
                    rows.add(stripCr(next));
                }
                try {
                    Frame frame = Frame.fromRows(rows);
                    frame.setLetter(currentLetter);
                    frames.add(frame);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(sourceName + ": bad frame ending at line " + lineNo + ": " + e.getMessage(), e);
                }
                continue;
            }
            throw new IllegalArgumentException(sourceName + ":" + lineNo + ": unexpected line '" + line + "'");
        }
        return frames;
    }

    /** Groups frames by their letter, keeping the order in which letters first appear. */
    public static Map<Character, List<Frame>> groupByLetter(List<Frame> frames) {
        Map<Character, List<Frame>> groups = new LinkedHashMap<>();
        for (Frame f : frames) {
            groups.computeIfAbsent(f.getLetter(), k -> new ArrayList<>()).add(f);
        }
        return groups;
    }

    private static String stripCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
