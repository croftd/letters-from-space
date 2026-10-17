// Letters from Space - one-off converter for the 2001 serialized video (video.dat).
// Compile against the original French classes in lettres_d_espace/code (see convert.sh).
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import edu.esi.lettresdespace.Cadre;
import edu.esi.lettresdespace.Video;

/**
 * Reads the 2001 {@code video.dat} (a deflated Java-serialized {@code Video} of {@code Cadre}s)
 * with the original classes and writes it in the new plain-text format used by
 * {@code lettersfromspace.space.Video}.
 * <pre>java -cp build/legacy ConvertLegacyVideo ../lettres_d_espace/code/edu/esi/lettresdespace/video.dat data/space_video_2001.dat</pre>
 */
public class ConvertLegacyVideo {

    public static void main(String[] args) throws IOException {
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        Video video = Video.charge(in.toString());
        int n = video.getSize();
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.US_ASCII)) {
            w.write("# Letters from Space - video recording\n");
            w.write("# frames=" + n + " height=" + Cadre.HAUTEUR + " width=" + Cadre.LARGEUR + "\n");
            w.write("# 'O' = star, '.' = dark sky. Frames are separated by a blank line; frame 0 comes first.\n");
            w.write("# Converted from the 2001 video.dat (Java serialization) on 2026-10-07; frame order preserved.\n");
            for (int i = 0; i < n; i++) {
                Cadre c = video.getCadreAt(i);
                w.write('\n');
                for (int row = 0; row < Cadre.HAUTEUR; row++) {
                    StringBuilder sb = new StringBuilder(Cadre.LARGEUR);
                    for (int col = 0; col < Cadre.LARGEUR; col++) {
                        sb.append(c.get(row, col) == 1 ? 'O' : '.');
                    }
                    w.write(sb.toString());
                    w.write('\n');
                }
            }
        }
        System.out.println("Wrote " + n + " frames to " + out);
    }
}
