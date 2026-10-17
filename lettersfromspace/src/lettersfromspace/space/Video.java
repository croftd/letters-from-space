// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
// Original: edu.esi.lettresdespace.Video by David Croft, February 2001.
package lettersfromspace.space;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import lettersfromspace.util.Check;

/**
 * A series of {@link Frame}s, like the recording of a video camera pointed at the night sky.
 * <p>
 * The 2001 class stored the video with Java object serialization (compressed). The rewrite
 * uses a plain text file that students can open in any editor: a few comment lines starting
 * with '#', then every frame as 8 lines of 'O' / '.', frames separated by a blank line. The
 * original 2001 recording was converted to this format (see {@code tools/legacy}).
 *
 * @see Frame
 */
public class Video {

    private final Frame[] frames;

    /** Creates a video from frames; frame indexes are (re)assigned in order. */
    public Video(Frame[] frames) {
        this.frames = Check.notNull(frames, "frames is null").clone();
        for (int i = 0; i < this.frames.length; i++) {
            this.frames[i].setFrameIndex(i);
        }
    }

    public Video(List<Frame> frames) {
        this(frames.toArray(new Frame[0]));
    }

    /** Creates a video of blank frames. */
    public Video(int size) {
        frames = new Frame[size];
        for (int i = 0; i < size; i++) {
            frames[i] = new Frame();
            frames[i].setFrameIndex(i);
        }
    }

    /** Number of frames. */
    public int size() {
        return frames.length;
    }

    /** The frame at {@code index} (0 &lt;= index &lt; size()). */
    public Frame getFrame(int index) {
        Check.require(index >= 0 && index < frames.length, "Frame index " + index + " is not valid for this video (" + frames.length + " frames)");
        return frames[index];
    }

    /** Saves the video as a text file. */
    public void save(Path file) throws IOException {
        save(file, List.of());
    }

    /** Saves the video as a text file, with extra comment lines (for example how it was made) in its header. */
    public void save(Path file, List<String> comments) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.US_ASCII)) {
            w.write("# Letters from Space - video recording\n");
            w.write("# frames=" + frames.length + " height=" + Frame.HEIGHT + " width=" + Frame.WIDTH + "\n");
            w.write("# 'O' = star, '.' = dark sky. Frames are separated by a blank line; frame 0 comes first.\n");
            for (String comment : comments) {
                w.write("# " + comment + "\n");
            }
            for (Frame f : frames) {
                w.write('\n');
                for (String row : f.toRows()) {
                    w.write(row);
                    w.write('\n');
                }
            }
        }
    }

    /** Loads a video saved by {@link #save(Path)}. */
    public static Video load(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            List<Frame> frames = FrameLoader.read(reader, file.toString());
            Check.require(!frames.isEmpty(), file + " contains no frames");
            return new Video(frames);
        }
    }
}
